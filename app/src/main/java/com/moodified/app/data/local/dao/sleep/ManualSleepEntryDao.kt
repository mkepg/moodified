package com.moodified.app.data.local.dao.sleep

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moodified.app.data.local.entity.sleep.ManualSleepEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ManualSleepEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: ManualSleepEntryEntity): Long

    @Query("DELETE FROM manual_sleep_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("DELETE FROM manual_sleep_entries WHERE date = :date")
    suspend fun deleteAllForDate(date: String)

    @Query("SELECT * FROM manual_sleep_entries WHERE date = :date ORDER BY startTimeMillis ASC")
    fun getEntriesForDate(date: String): Flow<List<ManualSleepEntryEntity>>

    @Query(
        """
        SELECT * FROM manual_sleep_entries
        WHERE date >= :startDate AND date <= :endDate
        ORDER BY date ASC, startTimeMillis ASC
        """,
    )
    fun getEntriesInDateRange(
        startDate: String,
        endDate: String,
    ): Flow<List<ManualSleepEntryEntity>>
}
