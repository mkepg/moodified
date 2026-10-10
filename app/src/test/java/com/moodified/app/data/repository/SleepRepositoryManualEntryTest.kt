package com.moodified.app.data.repository

import com.moodified.app.data.local.entity.sleep.ManualSleepEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SleepRepositoryManualEntryTest {
    @Test
    fun `manual entry date string matches LocalDate toString format`() {
        val date = LocalDate.of(2026, 10, 7)
        val entity =
            ManualSleepEntryEntity(
                id = 1,
                date = date.toString(),
                startTimeMillis = 0L,
                endTimeMillis = 0L,
            )
        assertEquals("2026-10-07", entity.date)
        assertEquals(date, LocalDate.parse(entity.date))
    }

    @Test
    fun `toDomain round-trips through fromDomain`() {
        val date = LocalDate.of(2026, 10, 7)
        val original =
            ManualSleepEntryEntity(
                id = 42,
                date = date.toString(),
                startTimeMillis = 1_000_000L,
                endTimeMillis = 2_000_000L,
            )
        val domain = original.toDomain()
        val roundTripped = ManualSleepEntryEntity.fromDomain(domain)

        assertEquals(original.id, roundTripped.id)
        assertEquals(original.date, roundTripped.date)
        assertEquals(original.startTimeMillis, roundTripped.startTimeMillis)
        assertEquals(original.endTimeMillis, roundTripped.endTimeMillis)
    }
}
