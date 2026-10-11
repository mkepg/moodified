package com.moodified.app.core.coordination

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.moodified.app.core.service.TrackingService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingCoordinator
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "TrackingCoordinator"

            /**
             * Only a start may use startForegroundService(): that call promises Android the service
             * will post its notification within seconds, and Android kills the app if it doesn't.
             * A stop or pause can end the service before it ever posts one, for example when
             * notifications are blocked, so those are delivered as plain service starts.
             */
            internal fun dispatchFor(
                action: String,
                canPostNotifications: Boolean,
            ): Dispatch =
                when {
                    !action.startsWith("ACTION_START_") -> Dispatch.BACKGROUND
                    canPostNotifications -> Dispatch.FOREGROUND
                    else -> Dispatch.SKIP
                }
        }

        internal enum class Dispatch { FOREGROUND, BACKGROUND, SKIP }

        private fun hasRequiredPermissions(): Boolean {
            // [FIX APPLIED]: Only validates POST_NOTIFICATIONS to allow Domain Isolation.
            val hasNotif =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

            return hasNotif
        }

        fun startActivity() = sendAction(TrackingService.ACTION_START_ACTIVITY)

        fun stopActivity() = sendAction(TrackingService.ACTION_STOP_ACTIVITY)

        fun pauseActivity() = sendAction(TrackingService.ACTION_PAUSE_ACTIVITY) // NEW

        fun startSleep() = sendAction(TrackingService.ACTION_START_SLEEP)

        fun stopSleep() = sendAction(TrackingService.ACTION_STOP_SLEEP)

        fun pauseSleep() = sendAction(TrackingService.ACTION_PAUSE_SLEEP) // NEW

        fun startInteraction() = sendAction(TrackingService.ACTION_START_INTERACTION)

        fun stopInteraction() = sendAction(TrackingService.ACTION_STOP_INTERACTION)

        fun pauseInteraction() = sendAction(TrackingService.ACTION_PAUSE_INTERACTION) // NEW

        private fun sendAction(action: String) {
            val intent = Intent(context, TrackingService::class.java).apply { this.action = action }

            try {
                when (dispatchFor(action, hasRequiredPermissions())) {
                    Dispatch.FOREGROUND -> ContextCompat.startForegroundService(context, intent)
                    Dispatch.BACKGROUND -> context.startService(intent)
                    Dispatch.SKIP -> {
                        Log.w(TAG, "Missing core Notification permission. Cannot send $action to foreground service.")
                        return
                    }
                }
                Log.d(TAG, "Sent $action to TrackingService.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send $action: ${e.message}", e)
            }
        }
    }
