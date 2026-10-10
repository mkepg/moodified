package com.moodified.app.data.repository

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.moodified.app.core.coordination.PollingJob
import com.moodified.app.core.coordination.TrackingCoordinator
import com.moodified.app.data.local.dao.sleep.ManualSleepEntryDao
import com.moodified.app.data.local.dao.sleep.SleepSegmentDao
import com.moodified.app.data.local.datasource.SleepPreferencesDataSource
import com.moodified.app.data.local.datasource.UsageStatsDataSource
import com.moodified.app.data.local.entity.sleep.ManualSleepEntryEntity
import com.moodified.app.data.local.entity.sleep.SleepSegmentEntity
import com.moodified.app.domain.model.sleep.DailySleepSummary
import com.moodified.app.domain.model.sleep.ManualSleepEntry
import com.moodified.app.domain.model.sleep.SleepSegment
import com.moodified.app.domain.model.sleep.SleepSignal
import com.moodified.app.domain.model.sleep.SleepStatus
import com.moodified.app.domain.repository.SleepRepository
import com.moodified.app.domain.usecase.sleep.CalculateSleepSegmentsUseCase
import com.moodified.app.domain.usecase.sleep.DailySleepMerger
import com.moodified.app.domain.usecase.sleep.ManualSleepAnchoring
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val sleepSegmentDao: SleepSegmentDao,
        private val manualSleepEntryDao: ManualSleepEntryDao,
        private val preferencesDataSource: SleepPreferencesDataSource,
        private val usageStatsDataSource: UsageStatsDataSource,
        private val inferSleepSegmentsUseCase: CalculateSleepSegmentsUseCase,
        private val coordinator: TrackingCoordinator,
    ) : SleepRepository {
        companion object {
            private const val TAG = "SleepRepo"
            private const val POLL_INTERVAL = 5 * 60_000L
            private const val SESSION_GAP_HOURS = 4L
            private const val BACKFILL_DAYS = 7
        }

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val stateMutex = Mutex()

        @Volatile private var _isProcessActive: Boolean = false
        override val isTracking: Boolean get() = preferencesDataSource.isTracking

        private val _signals = MutableStateFlow(buildInitialSignal(preferencesDataSource))

        override fun observeLiveSignal(): Flow<SleepSignal> {
            publishSnapshot()
            return _signals.asStateFlow()
        }

        private val poller =
            PollingJob(
                scope = scope,
                mutex = stateMutex,
                intervalMs = POLL_INTERVAL,
                tag = "$TAG/inference",
                isActive = { _isProcessActive },
            ) { refreshFromUsageStats() }

        init {
            preferencesDataSource.ensureInstallTimeRecorded()
        }

        override fun startTracking(): Boolean {
            if (!usageStatsDataSource.hasPermission()) {
                Log.w(TAG, "startTracking: UsageStats permission not granted.")
                return false
            }

            preferencesDataSource.isTracking = true
            preferencesDataSource.hasActiveSession = true
            publishSnapshot()

            if (_isProcessActive) {
                Log.d(TAG, "startTracking: already running.")
                return true
            }
            _isProcessActive = true

            coordinator.startSleep()
            Log.d(TAG, "startTracking: launching backfill and poller.")

            poller.start()
            scope.launch(Dispatchers.IO) { backfillHistoricalSleep() }

            return true
        }

        override fun stopTracking() {
            preferencesDataSource.isTracking = false
            publishSnapshot()

            if (!_isProcessActive) {
                Log.d(TAG, "stopTracking: already stopped — circuit breaker bypassed for prefs.")
                return
            }

            _isProcessActive = false
            coordinator.stopSleep()
            poller.stop()

            Log.d(TAG, "stopTracking: tracking stopped.")
        }

        override fun pauseTracking() {
            if (!_isProcessActive) return

            Log.d(TAG, "pauseTracking: Suspending processes due to missing permissions. Intent preserved.")
            _isProcessActive = false
            coordinator.stopSleep()
            poller.stop()

            publishSnapshot()
        }

        private suspend fun refreshFromUsageStats(forceFinalize: Boolean = false) {
            if (!usageStatsDataSource.hasPermission()) return

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isScreenOn = powerManager.isInteractive

            val targetDate = LocalDate.now()
            val zone = ZoneId.systemDefault()

            val windowStartMs =
                targetDate.minusDays(
                    1,
                ).atTime(CalculateSleepSegmentsUseCase.SLEEP_EARLIEST_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
            val windowEndMs = targetDate.atTime(CalculateSleepSegmentsUseCase.WAKE_LATEST_HOUR, 0).atZone(zone).toInstant().toEpochMilli()

            val rawGaps = usageStatsDataSource.queryScreenOffGaps(windowStartMs, windowEndMs)
            val segments = inferSleepSegmentsUseCase(targetDate, rawGaps)

            // [FIX APPLIED]: Deadlock resolved by trusting the PollingJob lock.
            // [FIX APPLIED]: Dynamic Finalized Blocks. Only execute heavy DB operations if the
            // user is awake (screen is on) indicating the sleep session has concluded, OR if
            // the TelemetryWorker forces a database flush to resolve edge cases.
            if (isScreenOn || forceFinalize) {
                sleepSegmentDao.deleteSegmentsBetween(windowStartMs, windowEndMs)
                if (segments.isNotEmpty()) {
                    sleepSegmentDao.insertSegments(segments.map { SleepSegmentEntity.fromDomain(it) })
                    preferencesDataSource.inferredConfidence = segments.first().confidence
                } else {
                    preferencesDataSource.inferredConfidence = 0
                }
            }

            publishSnapshot()
        }

        override suspend fun flushSleepDataToDb() {
            // Explicitly acquire the lock here to ensure thread-safety when called externally
            // by the background TelemetryWorker, avoiding collision with the PollingJob.
            stateMutex.withLock {
                refreshFromUsageStats(forceFinalize = true)
            }
        }

        private suspend fun backfillHistoricalSleep() {
            if (!usageStatsDataSource.hasPermission()) return
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now()

            Log.d(TAG, "[BACKFILL] Starting historical sleep backfill for $BACKFILL_DAYS nights.")

            for (daysBack in 1..BACKFILL_DAYS) {
                val targetDate = today.minusDays(daysBack.toLong())
                val windowStartMs =
                    targetDate.minusDays(1)
                        .atTime(CalculateSleepSegmentsUseCase.SLEEP_EARLIEST_HOUR, 0)
                        .atZone(zone).toInstant().toEpochMilli()
                val windowEndMs =
                    targetDate
                        .atTime(CalculateSleepSegmentsUseCase.WAKE_LATEST_HOUR, 0)
                        .atZone(zone).toInstant().toEpochMilli()

                try {
                    val existing = sleepSegmentDao.countSegmentsInWindow(windowStartMs, windowEndMs)
                    if (existing > 0) {
                        continue
                    }

                    val rawGaps = usageStatsDataSource.queryScreenOffGaps(windowStartMs, windowEndMs)
                    val segments = inferSleepSegmentsUseCase(targetDate, rawGaps)

                    if (segments.isNotEmpty()) {
                        sleepSegmentDao.insertSegments(
                            segments.map { SleepSegmentEntity.fromDomain(it, isBackfilled = true) },
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "[BACKFILL] $targetDate — failed: ${e.message}")
                }
            }

            Log.d(TAG, "[BACKFILL] Backfill complete.")
        }

        override fun getSegmentsForDate(date: LocalDate): Flow<List<SleepSegment>> {
            val zone = ZoneId.systemDefault()
            val windowStartMs = date.minusDays(1).atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
            val windowEndMs = date.plusDays(1).atTime(6, 0).atZone(zone).toInstant().toEpochMilli()

            return sleepSegmentDao.getSegmentsBetween(windowStartMs, windowEndMs)
                .map { entities -> entities.map { it.toDomain() } }
        }

        override fun getWeeklySummaries(endDate: LocalDate): Flow<List<DailySleepSummary>> {
            val zone = ZoneId.systemDefault()
            val broadStartMs = endDate.minusDays(8).atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
            val broadEndMs = endDate.plusDays(1).atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
            val rangeStartDate = endDate.minusDays(6).toString()
            val rangeEndDate = endDate.toString()

            return combine(
                sleepSegmentDao.getSegmentsBetween(broadStartMs, broadEndMs),
                manualSleepEntryDao.getEntriesInDateRange(rangeStartDate, rangeEndDate),
            ) { entities, manualEntities ->
                val all = entities.map { it.toDomain() }.sortedBy { it.startTime }
                val sessions = groupIntoSessions(all)

                (0L..6L).mapNotNull { daysBack ->
                    val d = endDate.minusDays(daysBack)
                    val manual =
                        manualEntities
                            .filter { it.date == d.toString() }
                            .map { ManualSleepAnchoring.correctLegacy(it.toDomain(), zone) }
                    val estimate =
                        sessions
                            .firstOrNull { s -> s.last().endTime.toLocalDate() == d }
                            ?.filter { it.status == SleepStatus.ASLEEP }
                            ?.minByOrNull { it.startTime }
                    DailySleepMerger.merge(d, estimate, manual, zone)
                }
            }
        }

        override suspend fun persistSegments(segments: List<SleepSegment>) {
            sleepSegmentDao.insertSegments(segments.map { SleepSegmentEntity.fromDomain(it) })
        }

        override fun hasUsagePermission(): Boolean = usageStatsDataSource.hasPermission()

        override suspend fun saveManualSleepEntry(
            date: LocalDate,
            startTimeMs: Long,
            endTimeMs: Long,
        ): Long =
            manualSleepEntryDao.insertEntry(
                ManualSleepEntryEntity(date = date.toString(), startTimeMillis = startTimeMs, endTimeMillis = endTimeMs),
            )

        override suspend fun deleteManualSleepEntry(id: Long) {
            manualSleepEntryDao.deleteEntryById(id)
        }

        override suspend fun clearManualSleepEntriesForDate(date: LocalDate) {
            manualSleepEntryDao.deleteAllForDate(date.toString())
        }

        override fun observeManualEntriesForDate(date: LocalDate): Flow<List<ManualSleepEntry>> =
            manualSleepEntryDao.getEntriesForDate(date.toString())
                .map { entities ->
                    val zone = ZoneId.systemDefault()
                    entities.map { ManualSleepAnchoring.correctLegacy(it.toDomain(), zone) }
                }

        private fun groupIntoSessions(segments: List<SleepSegment>): List<List<SleepSegment>> {
            if (segments.isEmpty()) return emptyList()
            val sessions = mutableListOf<MutableList<SleepSegment>>()
            var current = mutableListOf(segments.first())

            for (i in 1 until segments.size) {
                if (Duration.between(segments[i - 1].endTime, segments[i].startTime).toHours() >= SESSION_GAP_HOURS) {
                    sessions.add(current)
                    current = mutableListOf()
                }
                current.add(segments[i])
            }
            sessions.add(current)
            return sessions
        }

        private fun publishSnapshot() {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isScreenOn = powerManager.isInteractive

            _signals.update {
                SleepSignal(
                    isTracking = preferencesDataSource.isTracking,
                    hasActiveSession = preferencesDataSource.hasActiveSession,
                    status = if (isScreenOn) SleepStatus.AWAKE else SleepStatus.UNKNOWN,
                    confidence = preferencesDataSource.inferredConfidence,
                    timestamp = LocalDateTime.now(),
                )
            }
        }

        private fun buildInitialSignal(prefs: SleepPreferencesDataSource): SleepSignal {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isScreenOn = powerManager.isInteractive

            return SleepSignal(
                isTracking = prefs.isTracking,
                hasActiveSession = prefs.hasActiveSession,
                status = if (isScreenOn) SleepStatus.AWAKE else SleepStatus.UNKNOWN,
                confidence = prefs.inferredConfidence,
                timestamp = LocalDateTime.now(),
            )
        }
    }
