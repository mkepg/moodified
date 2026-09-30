package com.moodified.app.presentation.quicklog

import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.model.mood.Valence
import com.moodified.app.domain.repository.MoodRepository
import com.moodified.app.domain.usecase.inference.FeedbackCalibrationUseCase
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class QuickLogViewModelTest {
    private lateinit var repo: FakeMoodRepository
    private lateinit var vm: QuickLogViewModel
    private val dispatcher = StandardTestDispatcher()
    private val fakeFeedbackCalibration: FeedbackCalibrationUseCase = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeMoodRepository()
        vm = QuickLogViewModel(repo, fakeFeedbackCalibration)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `save in add mode inserts new entry with current time when timestamp not customized`() =
        runTest(dispatcher) {
            vm.selectValence(Valence.POSITIVE)
            vm.selectArousal(Arousal.MID)
            val before = LocalDateTime.now()
            vm.save()
            advanceUntilIdle()

            assertEquals(1, repo.inserted.size)
            assertEquals(0, repo.updated.size)
            val entry = repo.inserted.single()
            assertEquals(Valence.POSITIVE, entry.valence)
            assertEquals(Arousal.MID, entry.arousal)
            assertTrue("timestamp should be ~now", !entry.timestamp.isBefore(before))
            assertTrue("timestamp should be ~now", !entry.timestamp.isAfter(LocalDateTime.now()))
        }

    @Test
    fun `save uses customized timestamp when user picked one`() =
        runTest(dispatcher) {
            val picked = LocalDateTime.now().minusDays(2).withHour(15).withMinute(30)
            vm.selectValence(Valence.NEUTRAL)
            vm.selectArousal(Arousal.LOW)
            vm.updateTimestamp(picked)
            vm.save()
            advanceUntilIdle()

            assertEquals(picked, repo.inserted.single().timestamp)
        }

    @Test
    fun `save with note trims and stores it, blank note becomes null`() =
        runTest(dispatcher) {
            vm.selectValence(Valence.POSITIVE)
            vm.selectArousal(Arousal.HIGH)
            vm.updateNote("   feeling good   ")
            vm.save()
            advanceUntilIdle()
            assertEquals("feeling good", repo.inserted.single().note)

            repo.inserted.clear()
            vm.reset()
            vm.selectValence(Valence.POSITIVE)
            vm.selectArousal(Arousal.HIGH)
            vm.updateNote("   ")
            vm.save()
            advanceUntilIdle()
            assertNull(repo.inserted.single().note)
        }

    @Test
    fun `save rejects future timestamp and emits error event`() =
        runTest(dispatcher) {
            vm.selectValence(Valence.NEGATIVE)
            vm.selectArousal(Arousal.LOW)
            vm.updateTimestamp(LocalDateTime.now().plusHours(2))
            vm.save()
            advanceUntilIdle()

            assertEquals(0, repo.inserted.size)
            val event = vm.events.first()
            assertTrue(event is QuickLogEvent.SaveError)
            assertFalse(vm.uiState.value.isSaving)
        }

    @Test
    fun `startEdit populates state from repository`() =
        runTest(dispatcher) {
            val existing =
                MoodEntry(
                    id = 42,
                    valence = Valence.NEGATIVE,
                    arousal = Arousal.HIGH,
                    note = "rough day",
                    timestamp = LocalDateTime.of(2026, 3, 1, 9, 15),
                )
            repo.byId[42] = existing

            vm.startEdit(42)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(42L, state.editingEntryId)
            assertTrue(state.isEditMode)
            assertEquals(Valence.NEGATIVE, state.selectedValence)
            assertEquals(Arousal.HIGH, state.selectedArousal)
            assertEquals("rough day", state.note)
            assertEquals(existing.timestamp, state.timestamp)
            assertTrue(state.isTimestampCustomized)
        }

    @Test
    fun `startEdit with unknown id emits EntryNotFound`() =
        runTest(dispatcher) {
            vm.startEdit(999)
            advanceUntilIdle()

            assertEquals(QuickLogEvent.EntryNotFound, vm.events.first())
        }

    @Test
    fun `save in edit mode calls updateEntry not insertEntry`() =
        runTest(dispatcher) {
            val original =
                MoodEntry(
                    id = 7,
                    valence = Valence.NEUTRAL,
                    arousal = Arousal.MID,
                    timestamp = LocalDateTime.now().minusDays(1),
                )
            repo.byId[7] = original
            vm.startEdit(7)
            advanceUntilIdle()

            vm.selectValence(Valence.POSITIVE)
            vm.updateNote("felt better after all")
            vm.save()
            advanceUntilIdle()

            assertEquals(0, repo.inserted.size)
            assertEquals(1, repo.updated.size)
            val updated = repo.updated.single()
            assertEquals(7L, updated.id)
            assertEquals(Valence.POSITIVE, updated.valence)
            assertEquals("felt better after all", updated.note)
        }

    @Test
    fun `deleteCurrent in edit mode calls deleteEntry and emits Deleted`() =
        runTest(dispatcher) {
            repo.byId[3] =
                MoodEntry(id = 3, valence = Valence.NEUTRAL, arousal = Arousal.LOW, timestamp = LocalDateTime.now())
            vm.startEdit(3)
            advanceUntilIdle()

            vm.deleteCurrent()
            advanceUntilIdle()

            assertEquals(listOf(3L), repo.deleted)
            assertEquals(QuickLogEvent.Deleted, vm.events.first())
        }

    @Test
    fun `deleteCurrent in add mode is a no-op`() =
        runTest(dispatcher) {
            vm.deleteCurrent()
            advanceUntilIdle()
            assertTrue(repo.deleted.isEmpty())
        }

    private class FakeMoodRepository : MoodRepository {
        val inserted = mutableListOf<MoodEntry>()
        val updated = mutableListOf<MoodEntry>()
        val deleted = mutableListOf<Long>()
        val byId = mutableMapOf<Long, MoodEntry>()

        override fun getAllEntries(): Flow<List<MoodEntry>> = emptyFlow()

        override fun getTodayEntries(): Flow<List<MoodEntry>> = emptyFlow()

        override fun getEntriesForDate(date: LocalDate): Flow<List<MoodEntry>> = emptyFlow()

        override fun getEntriesInRange(
            startDate: LocalDate,
            endDate: LocalDate,
        ): Flow<List<MoodEntry>> = emptyFlow()

        override suspend fun insertEntry(entry: MoodEntry): Long {
            inserted.add(entry)
            return entry.id
        }

        override suspend fun updateEntry(entry: MoodEntry) {
            updated.add(entry)
        }

        override suspend fun getEntryById(id: Long): MoodEntry? {
            assertNotNull("test setup: byId should contain the requested id if the test expects a hit", byId)
            return byId[id]
        }

        override suspend fun deleteEntry(id: Long) {
            deleted.add(id)
        }
    }
}
