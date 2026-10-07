package com.moodified.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moodified.app.data.local.dao.activity.ActivityDailySummaryDao
import com.moodified.app.data.local.dao.activity.ActivityTelemetryDao
import com.moodified.app.data.local.dao.interaction.InteractionDailySummaryDao
import com.moodified.app.data.local.dao.interaction.InteractionSessionDao
import com.moodified.app.data.local.dao.intervention.InterventionHistoryDao
import com.moodified.app.data.local.dao.mood.MoodEntryDao
import com.moodified.app.data.local.dao.notification.NotificationRecordDao
import com.moodified.app.data.local.dao.sleep.ManualSleepEntryDao
import com.moodified.app.data.local.dao.sleep.SleepSegmentDao
import com.moodified.app.data.local.entity.activity.ActivityDailySummaryEntity
import com.moodified.app.data.local.entity.activity.ActivityTelemetryEntity
import com.moodified.app.data.local.entity.interaction.InteractionDailySummaryEntity
import com.moodified.app.data.local.entity.interaction.InteractionSessionEntity
import com.moodified.app.data.local.entity.intervention.InterventionHistoryEntity
import com.moodified.app.data.local.entity.mood.MoodEntryEntity
import com.moodified.app.data.local.entity.notification.NotificationRecordEntity
import com.moodified.app.data.local.entity.sleep.ManualSleepEntryEntity
import com.moodified.app.data.local.entity.sleep.SleepSegmentEntity

@Database(
    entities = [
        MoodEntryEntity::class,
        SleepSegmentEntity::class,
        ActivityTelemetryEntity::class,
        ActivityDailySummaryEntity::class,
        InteractionSessionEntity::class,
        InteractionDailySummaryEntity::class,
        InterventionHistoryEntity::class,
        NotificationRecordEntity::class,
        ManualSleepEntryEntity::class,
    ],
    version = 16,
    exportSchema = true,
)
abstract class MoodifiedDatabase : RoomDatabase() {
    abstract fun moodEntryDao(): MoodEntryDao

    abstract fun sleepSegmentDao(): SleepSegmentDao

    abstract fun activityTelemetryDao(): ActivityTelemetryDao

    abstract fun activityDailySummaryDao(): ActivityDailySummaryDao

    abstract fun interactionSessionDao(): InteractionSessionDao

    abstract fun interactionDailySummaryDao(): InteractionDailySummaryDao

    abstract fun interventionHistoryDao(): InterventionHistoryDao

    abstract fun notificationRecordDao(): NotificationRecordDao

    abstract fun manualSleepEntryDao(): ManualSleepEntryDao

    companion object {
        const val DATABASE_NAME = "moodified_db"

        val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `interaction_sessions_new` (
                            `id`              INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `startTimeMillis` INTEGER NOT NULL,
                            `endTimeMillis`   INTEGER NOT NULL,
                            `durationMinutes` INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        INSERT INTO `interaction_sessions_new`
                            (id, startTimeMillis, endTimeMillis, durationMinutes)
                        SELECT id, startTimeMillis, endTimeMillis, durationMinutes
                        FROM `interaction_sessions`
                        """.trimIndent(),
                    )
                    db.execSQL("DROP TABLE `interaction_sessions`")
                    db.execSQL("ALTER TABLE `interaction_sessions_new` RENAME TO `interaction_sessions`")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `interaction_daily_summaries_new` (
                            `date`                   TEXT    PRIMARY KEY NOT NULL,
                            `totalScreenTimeMinutes` INTEGER NOT NULL,
                            `lateNightUsageMinutes`  INTEGER NOT NULL DEFAULT 0,
                            `isPartialDay`           INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        INSERT INTO `interaction_daily_summaries_new`
                            (date, totalScreenTimeMinutes, lateNightUsageMinutes, isPartialDay)
                        SELECT date, totalScreenTimeMinutes, 0, isPartialDay
                        FROM `interaction_daily_summaries`
                        """.trimIndent(),
                    )
                    db.execSQL("DROP TABLE `interaction_daily_summaries`")
                    db.execSQL(
                        "ALTER TABLE `interaction_daily_summaries_new` " +
                            "RENAME TO `interaction_daily_summaries`",
                    )
                }
            }

        val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE `interaction_daily_summaries` ADD COLUMN `unlockCount` INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }

        val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `interaction_daily_summaries_new` (
                            `date`                   TEXT    PRIMARY KEY NOT NULL,
                            `totalScreenTimeMinutes` INTEGER NOT NULL,
                            `lateNightUsageMinutes`  INTEGER NOT NULL DEFAULT 0,
                            `unlockCount`            INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        INSERT INTO `interaction_daily_summaries_new`
                            (date, totalScreenTimeMinutes, lateNightUsageMinutes, unlockCount)
                        SELECT date, totalScreenTimeMinutes, lateNightUsageMinutes, unlockCount
                        FROM `interaction_daily_summaries`
                        """.trimIndent(),
                    )
                    db.execSQL("DROP TABLE `interaction_daily_summaries`")
                    db.execSQL("ALTER TABLE `interaction_daily_summaries_new` RENAME TO `interaction_daily_summaries`")
                }
            }

        val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE `sleep_segments` ADD COLUMN `isBackfilled` INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }

        val MIGRATION_10_11 =
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `mood_entries` ADD COLUMN `contextActivityIntensity` TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE `mood_entries` ADD COLUMN `contextSleepMinutes` INTEGER DEFAULT NULL")
                    db.execSQL("ALTER TABLE `activity_daily_summaries` ADD COLUMN `minutesPerIntensityBandRaw` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `interaction_daily_summaries` ADD COLUMN `sessionCount` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL(
                        "ALTER TABLE `interaction_daily_summaries` ADD COLUMN `averageSessionDurationMinutes` INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }

        val MIGRATION_11_12 =
            object : Migration(11, 12) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `sleep_segments` ADD COLUMN `awakenings` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `sleep_segments` ADD COLUMN `timeInBedMinutes` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `sleep_segments` ADD COLUMN `totalSleepMinutes` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("DELETE FROM `sleep_segments`")
                }
            }

        val MIGRATION_12_13 =
            object : Migration(12, 13) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `sleep_segments` ADD COLUMN `confidence` INTEGER NOT NULL DEFAULT 0")
                }
            }

        val MIGRATION_13_14 =
            object : Migration(13, 14) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `intervention_history` (
                            `interventionId` TEXT NOT NULL, 
                            `lastShownAtMillis` INTEGER NOT NULL, 
                            `userFeedback` TEXT DEFAULT NULL, 
                            `domain` TEXT NOT NULL DEFAULT '', 
                            `wasCompleted` INTEGER NOT NULL DEFAULT 0, 
                            `dismissalCount` INTEGER NOT NULL DEFAULT 0, 
                            PRIMARY KEY(`interventionId`)
                        )
                        """.trimIndent(),
                    )
                }
            }

        val MIGRATION_14_15 =
            object : Migration(14, 15) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `notification_records` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `type` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `body` TEXT NOT NULL,
                            `deepLink` TEXT,
                            `deliveredAt` INTEGER NOT NULL,
                            `readAt` INTEGER,
                            `dismissedAt` INTEGER
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS `index_notification_records_deliveredAt`
                          ON `notification_records`(`deliveredAt`)
                        """.trimIndent(),
                    )
                }
            }

        val MIGRATION_15_16 =
            object : Migration(15, 16) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `manual_sleep_entries` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `date` TEXT NOT NULL,
                            `startTimeMillis` INTEGER NOT NULL,
                            `endTimeMillis` INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                }
            }
    }
}
