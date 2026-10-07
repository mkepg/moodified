package com.moodified.app.data.local.entity.sleep

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moodified.app.domain.model.sleep.ManualSleepEntry
import java.time.LocalDate

@Entity(tableName = "manual_sleep_entries")
data class ManualSleepEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
) {
    fun toDomain(): ManualSleepEntry =
        ManualSleepEntry(
            id = id,
            date = LocalDate.parse(date),
            startTimeMs = startTimeMillis,
            endTimeMs = endTimeMillis,
        )

    companion object {
        fun fromDomain(entry: ManualSleepEntry): ManualSleepEntryEntity =
            ManualSleepEntryEntity(
                id = entry.id,
                date = entry.date.toString(),
                startTimeMillis = entry.startTimeMs,
                endTimeMillis = entry.endTimeMs,
            )
    }
}
