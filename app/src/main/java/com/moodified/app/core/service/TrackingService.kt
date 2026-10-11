package com.moodified.app.core.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.moodified.app.MainActivity
import com.moodified.app.R
import com.moodified.app.data.local.datasource.PromptPreferencesDataSource
import com.moodified.app.data.local.entity.notification.NotificationRecordType
import com.moodified.app.data.receiver.MicroPromptReceiver
import com.moodified.app.domain.model.mood.Valence
import com.moodified.app.domain.model.notification.NotificationRecord
import com.moodified.app.domain.repository.ActivityRepository
import com.moodified.app.domain.repository.InteractionRepository
import com.moodified.app.domain.repository.MoodRepository
import com.moodified.app.domain.repository.NotificationHistoryRepository
import com.moodified.app.domain.repository.SleepRepository
import com.moodified.app.domain.usecase.inference.EvaluateMicroPromptTriggersUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {
    @Inject lateinit var activityRepository: ActivityRepository

    @Inject lateinit var sleepRepository: SleepRepository

    @Inject lateinit var interactionRepository: InteractionRepository

    @Inject lateinit var moodRepository: MoodRepository

    @Inject lateinit var evaluateMicroPromptTriggers: EvaluateMicroPromptTriggersUseCase

    @Inject lateinit var notificationHistoryRepository: NotificationHistoryRepository

    @Inject lateinit var promptPrefs: PromptPreferencesDataSource

    companion object {
        private const val TAG = "TrackingService"
        const val ACTION_START_ACTIVITY = "ACTION_START_ACTIVITY"
        const val ACTION_STOP_ACTIVITY = "ACTION_STOP_ACTIVITY"
        const val ACTION_PAUSE_ACTIVITY = "ACTION_PAUSE_ACTIVITY"
        const val ACTION_START_SLEEP = "ACTION_START_SLEEP"
        const val ACTION_STOP_SLEEP = "ACTION_STOP_SLEEP"
        const val ACTION_PAUSE_SLEEP = "ACTION_PAUSE_SLEEP"
        const val ACTION_START_INTERACTION = "ACTION_START_INTERACTION"
        const val ACTION_STOP_INTERACTION = "ACTION_STOP_INTERACTION"
        const val ACTION_PAUSE_INTERACTION = "ACTION_PAUSE_INTERACTION"

        private const val CHANNEL_ID = "HealthTrackingChannel"
        private const val PROMPT_CHANNEL_ID = "MicroPromptChannel"
        private const val NOTIFICATION_ID = 404
    }

    private var isActivityTracking = false
    private var isSleepTracking = false
    private var isInteractionTracking = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var isVerifyingDatabase = false

    private fun hasRequiredPermissions(): Boolean {
        val hasNotif =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        return hasNotif
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannels()
        }
        observeContextForPrompt()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Log.d(TAG, "[TRACKING_FLOW] onStartCommand action=${intent?.action}")

        if (!hasRequiredPermissions()) {
            Log.w(TAG, "[TRACKING_FLOW] Missing notifications permission. Stopping service and trackers.")
            isActivityTracking = false
            isSleepTracking = false
            isInteractionTracking = false

            activityRepository.stopTracking()
            sleepRepository.stopTracking()
            interactionRepository.stopTracking()

            // If this start came through startForegroundService() (e.g. an OS restart of the sticky
            // service), Android kills the app unless startForeground() is called before stopping.
            // Without the notification permission the call still succeeds; the notification is hidden.
            startServiceForeground()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent == null) {
            Log.d(TAG, "[TRACKING_FLOW] OS restart — restoring.")
            restoreStateAndResume()
        } else {
            when (intent.action) {
                ACTION_START_ACTIVITY -> {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
                        isActivityTracking = true
                        activityRepository.startTracking()
                        Log.d(TAG, "[TRACKING_FLOW] Activity tracking ACTIVE.")
                    } else {
                        Log.w(TAG, "[TRACKING_FLOW] Activity permission missing. Gracefully paused.")
                        isActivityTracking = false
                        activityRepository.pauseTracking()
                    }
                }
                ACTION_STOP_ACTIVITY -> {
                    isActivityTracking = false
                    activityRepository.stopTracking()
                    Log.d(TAG, "[TRACKING_FLOW] Activity tracking STOPPED.")
                }
                ACTION_PAUSE_ACTIVITY -> {
                    isActivityTracking = false
                    activityRepository.pauseTracking()
                    Log.d(TAG, "[TRACKING_FLOW] Activity tracking PAUSED (Intent preserved).")
                }

                ACTION_START_SLEEP -> {
                    if (sleepRepository.hasUsagePermission()) {
                        isSleepTracking = true
                        sleepRepository.startTracking()
                        Log.d(TAG, "[TRACKING_FLOW] Sleep tracking ACTIVE.")
                    } else {
                        Log.w(TAG, "[TRACKING_FLOW] Usage access missing. Sleep Tracker paused.")
                        isSleepTracking = false
                        sleepRepository.pauseTracking()
                    }
                }
                ACTION_STOP_SLEEP -> {
                    isSleepTracking = false
                    sleepRepository.stopTracking()
                    Log.d(TAG, "[TRACKING_FLOW] Sleep tracking STOPPED.")
                }
                ACTION_PAUSE_SLEEP -> {
                    isSleepTracking = false
                    sleepRepository.pauseTracking()
                    Log.d(TAG, "[TRACKING_FLOW] Sleep tracking PAUSED (Intent preserved).")
                }

                ACTION_START_INTERACTION -> {
                    if (interactionRepository.hasUsagePermission()) {
                        isInteractionTracking = true
                        interactionRepository.startTracking()
                        Log.d(TAG, "[TRACKING_FLOW] Interaction tracking ACTIVE.")
                    } else {
                        Log.w(TAG, "[TRACKING_FLOW] Usage access missing. Interaction Tracker paused.")
                        isInteractionTracking = false
                        interactionRepository.pauseTracking()
                    }
                }
                ACTION_STOP_INTERACTION -> {
                    isInteractionTracking = false
                    interactionRepository.stopTracking()
                    Log.d(TAG, "[TRACKING_FLOW] Interaction tracking STOPPED.")
                }
                ACTION_PAUSE_INTERACTION -> {
                    isInteractionTracking = false
                    interactionRepository.pauseTracking()
                    Log.d(TAG, "[TRACKING_FLOW] Interaction tracking PAUSED (Intent preserved).")
                }
            }
        }

        startServiceForeground()

        if (!isActivityTracking && !isSleepTracking && !isInteractionTracking) {
            Log.d(TAG, "[TRACKING_FLOW] Nothing to track — stopping self.")
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "[TRACKING_FLOW] Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun restoreStateAndResume() {
        if (activityRepository.isTracking) {
            isActivityTracking = true
            activityRepository.startTracking()
        }
        if (sleepRepository.isTracking) {
            isSleepTracking = true
            sleepRepository.startTracking()
        }
        if (interactionRepository.isTracking) {
            isInteractionTracking = true
            interactionRepository.startTracking()
        }
    }

    private fun observeContextForPrompt() {
        serviceScope.launch {
            combine(
                activityRepository.observeSignal(),
                interactionRepository.observeLiveSignal(),
            ) { activity, interaction ->
                activity to interaction
            }.collect { (activity, interaction) ->
                val result =
                    try {
                        evaluateMicroPromptTriggers(activity, interaction)
                    } catch (e: Exception) {
                        Log.e(TAG, "State evaluation failed", e)
                        return@collect
                    }

                if (result.shouldPrompt && result.promptMessage != null && !isVerifyingDatabase) {
                    isVerifyingDatabase = true
                    serviceScope.launch {
                        try {
                            val nowMs = System.currentTimeMillis()
                            val todayEntries = moodRepository.getTodayEntries().first()

                            val lastLogMs =
                                todayEntries.maxOfOrNull {
                                    it.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                } ?: 0L

                            // Include lastMoodActionMs so a backdated mood entry (whose own
                            // timestamp is in the past) still counts as a recent action and
                            // suppresses immediate prompting.
                            val lastActionMs =
                                maxOf(
                                    promptPrefs.lastPromptTimestampMs,
                                    promptPrefs.lastMoodActionMs,
                                    lastLogMs,
                                )

                            if (nowMs - lastActionMs >= EvaluateMicroPromptTriggersUseCase.COOLDOWN_MS) {
                                promptPrefs.lastPromptTimestampMs = nowMs
                                promptPrefs.promptsTodayCount += 1
                                triggerMicroPromptNotification(result.promptMessage)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed database verification for micro-prompt", e)
                        } finally {
                            isVerifyingDatabase = false
                        }
                    }
                }
            }
        }
    }

    private fun triggerMicroPromptNotification(contextMessage: String) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        // Existing Action Buttons
        val actions =
            Valence.entries.mapIndexed { index, valence ->
                val intent =
                    Intent(this, MicroPromptReceiver::class.java).apply {
                        action = MicroPromptReceiver.ACTION_SELECT_VALENCE
                        putExtra(MicroPromptReceiver.EXTRA_VALENCE, valence.name)
                    }
                val pending = PendingIntent.getBroadcast(this, index, intent, flags)

                NotificationCompat.Action.Builder(
                    valence.iconRes(),
                    valence.displayLabel(),
                    pending,
                ).build()
            }

        // Tap Intent for deep linking to QuickLogSheet
        val tapIntent =
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("moodified://quicklog")
            }
        val pendingTapIntent =
            PendingIntent.getActivity(
                this,
                0,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(this, PROMPT_CHANNEL_ID)
                .setContentTitle("Moodified is with you")
                .setContentText(contextMessage)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingTapIntent)
                .apply { actions.forEach { addAction(it) } }
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

        val nm = getSystemService(NotificationManager::class.java)
        runBlocking(Dispatchers.IO) {
            notificationHistoryRepository.record(
                NotificationRecord(
                    type = NotificationRecordType.MICRO_PROMPT,
                    title = "Moodified is with you",
                    body = contextMessage,
                    deepLink = "moodified://quicklog",
                    deliveredAt = System.currentTimeMillis(),
                ),
            )
        }
        nm?.notify(MicroPromptReceiver.PROMPT_NOTIFICATION_ID, notification)
    }

    private fun startServiceForeground() {
        try {
            val notification =
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Moodified is with you")
                    .setContentText("Quietly learning your daily rhythms to support your well-being.")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting foreground service.", e)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service.", e)
            stopSelf()
        }
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return

        val trackingChannel =
            NotificationChannel(
                CHANNEL_ID,
                "Health & Interaction Tracking",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Maintains background tracking for activity, sleep, and phone interactions."
                setShowBadge(false)
            }

        val promptChannel =
            NotificationChannel(
                PROMPT_CHANNEL_ID,
                "Gentle Check-ins",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Context-aware prompts asking how you are feeling."
                setShowBadge(true)
            }

        nm.createNotificationChannel(trackingChannel)
        nm.createNotificationChannel(promptChannel)
    }
}
