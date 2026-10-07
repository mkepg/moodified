package com.moodified.app.di

import android.content.Context
import androidx.room.Room
import com.moodified.app.data.local.dao.activity.ActivityDailySummaryDao
import com.moodified.app.data.local.dao.activity.ActivityTelemetryDao
import com.moodified.app.data.local.dao.interaction.InteractionDailySummaryDao
import com.moodified.app.data.local.dao.interaction.InteractionSessionDao
import com.moodified.app.data.local.dao.intervention.InterventionHistoryDao
import com.moodified.app.data.local.dao.mood.MoodEntryDao
import com.moodified.app.data.local.dao.notification.NotificationRecordDao
import com.moodified.app.data.local.dao.sleep.ManualSleepEntryDao
import com.moodified.app.data.local.dao.sleep.SleepSegmentDao
import com.moodified.app.data.local.database.MoodifiedDatabase
import com.moodified.app.data.repository.ActivityRepositoryImpl
import com.moodified.app.data.repository.InteractionRepositoryImpl
import com.moodified.app.data.repository.InterventionRepositoryImpl
import com.moodified.app.data.repository.MoodRepositoryImpl
import com.moodified.app.data.repository.NotificationHistoryRepositoryImpl
import com.moodified.app.data.repository.SleepRepositoryImpl
import com.moodified.app.domain.repository.ActivityRepository
import com.moodified.app.domain.repository.InteractionRepository
import com.moodified.app.domain.repository.InterventionRepository
import com.moodified.app.domain.repository.MoodRepository
import com.moodified.app.domain.repository.NotificationHistoryRepository
import com.moodified.app.domain.repository.SleepRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MoodifiedDatabase =
        Room.databaseBuilder(
            context,
            MoodifiedDatabase::class.java,
            MoodifiedDatabase.DATABASE_NAME,
        )
            .addMigrations(
                MoodifiedDatabase.MIGRATION_6_7,
                MoodifiedDatabase.MIGRATION_7_8,
                MoodifiedDatabase.MIGRATION_8_9,
                MoodifiedDatabase.MIGRATION_9_10,
                MoodifiedDatabase.MIGRATION_10_11,
                MoodifiedDatabase.MIGRATION_11_12,
                MoodifiedDatabase.MIGRATION_12_13,
                MoodifiedDatabase.MIGRATION_13_14,
                MoodifiedDatabase.MIGRATION_14_15,
                MoodifiedDatabase.MIGRATION_15_16,
            )
            .build()

    @Provides @Singleton
    fun provideMoodEntryDao(db: MoodifiedDatabase): MoodEntryDao = db.moodEntryDao()

    @Provides @Singleton
    fun provideSleepSegmentDao(db: MoodifiedDatabase): SleepSegmentDao = db.sleepSegmentDao()

    @Provides @Singleton
    fun provideActivityTelemetryDao(db: MoodifiedDatabase): ActivityTelemetryDao = db.activityTelemetryDao()

    @Provides @Singleton
    fun provideActivityDailySummaryDao(db: MoodifiedDatabase): ActivityDailySummaryDao = db.activityDailySummaryDao()

    @Provides @Singleton
    fun provideInteractionSessionDao(db: MoodifiedDatabase): InteractionSessionDao = db.interactionSessionDao()

    @Provides @Singleton
    fun provideInteractionDailySummaryDao(db: MoodifiedDatabase): InteractionDailySummaryDao = db.interactionDailySummaryDao()

    @Provides @Singleton
    fun provideInterventionHistoryDao(db: MoodifiedDatabase): InterventionHistoryDao = db.interventionHistoryDao()

    @Provides @Singleton
    fun provideNotificationRecordDao(db: MoodifiedDatabase): NotificationRecordDao = db.notificationRecordDao()

    @Provides @Singleton
    fun provideManualSleepEntryDao(db: MoodifiedDatabase): ManualSleepEntryDao = db.manualSleepEntryDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindMoodRepository(impl: MoodRepositoryImpl): MoodRepository

    @Binds @Singleton
    abstract fun bindSleepRepository(impl: SleepRepositoryImpl): SleepRepository

    @Binds @Singleton
    abstract fun bindActivityRepository(impl: ActivityRepositoryImpl): ActivityRepository

    @Binds @Singleton
    abstract fun bindInteractionRepository(impl: InteractionRepositoryImpl): InteractionRepository

    @Binds @Singleton
    abstract fun bindInterventionRepository(impl: InterventionRepositoryImpl): InterventionRepository

    @Binds @Singleton
    abstract fun bindNotificationHistoryRepository(impl: NotificationHistoryRepositoryImpl): NotificationHistoryRepository
}
