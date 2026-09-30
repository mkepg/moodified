package com.moodified.app.presentation.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodified.app.core.utils.midnightTickerFlow
import com.moodified.app.data.local.datasource.CalibrationPreferencesDataSource
import com.moodified.app.data.local.datasource.CarePreferencesDataSource
import com.moodified.app.domain.model.activity.ActivityDailySummary
import com.moodified.app.domain.model.interaction.InteractionDailySummary
import com.moodified.app.domain.model.intervention.InterventionAction
import com.moodified.app.domain.model.intervention.WellBeingDomain
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.model.sleep.DailySleepSummary
import com.moodified.app.domain.usecase.activity.GetWeeklyActivitySummariesUseCase
import com.moodified.app.domain.usecase.activity.ObserveActivitySignalUseCase
import com.moodified.app.domain.usecase.inference.BuildDailyBehaviorSnapshotUseCase
import com.moodified.app.domain.usecase.inference.InferenceConstants
import com.moodified.app.domain.usecase.inference.RuleBasedMoodInferenceEngine
import com.moodified.app.domain.usecase.interaction.GetWeeklyInteractionSummariesUseCase
import com.moodified.app.domain.usecase.interaction.ObserveInteractionSignalUseCase
import com.moodified.app.domain.usecase.intervention.CareEvaluationEngine
import com.moodified.app.domain.usecase.intervention.RecordInterventionEffectivenessUseCase
import com.moodified.app.domain.usecase.mood.GetMoodHistoryUseCase
import com.moodified.app.domain.usecase.sleep.GetWeeklySleepSummariesUseCase
import com.moodified.app.presentation.insight.DomainReadiness
import com.moodified.app.presentation.insight.InsightDomainReadiness
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class CareViewModel
    @Inject
    constructor(
        private val buildDailyBehaviorSnapshot: BuildDailyBehaviorSnapshotUseCase,
        private val ruleBasedMoodInferenceEngine: RuleBasedMoodInferenceEngine,
        private val careEvaluationEngine: CareEvaluationEngine,
        private val recordInterventionEffectiveness: RecordInterventionEffectivenessUseCase,
        private val observeActivitySignal: ObserveActivitySignalUseCase,
        private val observeInteractionSignal: ObserveInteractionSignalUseCase,
        private val getWeeklySleepSummaries: GetWeeklySleepSummariesUseCase,
        private val getWeeklyActivitySummaries: GetWeeklyActivitySummariesUseCase,
        private val getWeeklyInteractionSummaries: GetWeeklyInteractionSummariesUseCase,
        private val getMoodHistory: GetMoodHistoryUseCase,
        private val carePreferences: CarePreferencesDataSource,
        private val calibrationSource: CalibrationPreferencesDataSource,
    ) : ViewModel() {
        private val refreshTrigger = MutableStateFlow(System.currentTimeMillis())
        private val _activeDomain = MutableStateFlow(carePreferences.activeDomain)
        private val _dismissedIds = MutableStateFlow<Set<String>>(emptySet())

        private data class HistoricalData(
            val sleep: List<DailySleepSummary>,
            val activity: List<ActivityDailySummary>,
            val interaction: List<InteractionDailySummary>,
            val moods: Map<LocalDate, List<MoodEntry>>,
        )

        val uiState: StateFlow<CareUiState> =
            combine(
                midnightTickerFlow(),
                refreshTrigger,
            ) { date, _ -> date }
                .flatMapLatest { today ->
                    val snapshotFlow = buildDailyBehaviorSnapshot(today)

                    val historicalDataFlow =
                        combine(
                            getWeeklySleepSummaries(today),
                            getWeeklyActivitySummaries(today),
                            getWeeklyInteractionSummaries(today),
                            getMoodHistory(),
                        ) { sleep, activity, interaction, moods ->
                            HistoricalData(sleep, activity, interaction, moods)
                        }

                    val synchronizedDbFlow =
                        combine(
                            snapshotFlow,
                            historicalDataFlow,
                        ) { snapshot, history ->
                            Pair(snapshot, history)
                        }.debounce(250)

                    val synchronizedWithCalibrationFlow =
                        combine(
                            synchronizedDbFlow,
                            calibrationSource.flow(),
                        ) { snapshotAndHistory, calibration ->
                            Triple(snapshotAndHistory.first, snapshotAndHistory.second, calibration)
                        }

                    combine(
                        synchronizedWithCalibrationFlow,
                        observeActivitySignal(),
                        observeInteractionSignal(),
                        _activeDomain,
                        _dismissedIds,
                    ) { (snapshot, history, calibration), activity, interaction, domain, dismissedIds ->

                        val sleepDays = history.sleep.size
                        val phoneDays = history.interaction.size
                        val activityDays = history.activity.size
                        val manualMoodDays = history.moods.values.flatten().count { it.isManual }

                        val domainReadiness =
                            InsightDomainReadiness(
                                sleep = DomainReadiness(isReady = sleepDays >= 1, daysWithData = sleepDays, requiredDays = 0),
                                phone = DomainReadiness(isReady = phoneDays >= 1, daysWithData = phoneDays, requiredDays = 0),
                                activity = DomainReadiness(isReady = activityDays >= 3, daysWithData = activityDays, requiredDays = 3),
                                mood = DomainReadiness(isReady = manualMoodDays >= 3, daysWithData = manualMoodDays, requiredDays = 3),
                            )

                        val moodState = ruleBasedMoodInferenceEngine(snapshot, calibration)

                        val actions =
                            careEvaluationEngine(
                                moodState = moodState,
                                snapshot = snapshot,
                                liveActivity = activity,
                                liveInteraction = interaction,
                                historicalSleep = history.sleep,
                                historicalActivity = history.activity,
                                historicalInteraction = history.interaction,
                                historicalMoods = history.moods.values.flatten(),
                            )

                        val isIntradayComplete = snapshot.dataCompletenessScore >= InferenceConstants.MIN_COMPLETENESS_FOR_INFERENCE

                        val activeGuidance =
                            actions
                                .filter { it is InterventionAction.Guidance || it is InterventionAction.Motivation }
                                .filterNot { it.id in dismissedIds }

                        val trendAlerts =
                            actions.filterIsInstance<InterventionAction.TrendAlert>()
                                .filterNot { it.id in dismissedIds }

                        val suggestedRoutine =
                            actions.filterIsInstance<InterventionAction.GuidedRoutine>()
                                .firstOrNull { it.id !in dismissedIds }

                        val motivationNudge =
                            actions.filterIsInstance<InterventionAction.MotivationNudge>()
                                .firstOrNull { it.id !in dismissedIds }

                        CareUiState(
                            isLoading = false,
                            hasEnoughMultiDayData = domainReadiness.anyReady,
                            isIntradayComplete = isIntradayComplete,
                            domainReadiness = domainReadiness,
                            inferredMood = moodState,
                            activeGuidance = activeGuidance,
                            trendAlerts = trendAlerts,
                            suggestedRoutine = suggestedRoutine,
                            microInterventions = getMicroInterventionCatalog().filter { it.wellBeingDomain == domain },
                            motivationNudge = motivationNudge,
                            activeDomain = domain,
                        )
                    }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = CareUiState(isLoading = true),
                )

        fun setWellBeingDomain(domain: WellBeingDomain) {
            carePreferences.activeDomain = domain
            _activeDomain.value = domain
        }

        fun recordFeedback(
            interventionId: String,
            feedback: String,
            wasCompleted: Boolean = false,
        ) {
            _dismissedIds.update { it + interventionId }
            viewModelScope.launch(Dispatchers.IO) {
                recordInterventionEffectiveness(interventionId, feedback, wasCompleted)
                refreshTrigger.value = System.currentTimeMillis()
            }
        }

        private fun getMicroInterventionCatalog(): List<InterventionAction.MicroIntervention> {
            return listOf(
                // ==========================================
                // MENTAL DOMAIN
                // ==========================================
                InterventionAction.MicroIntervention(
                    id = "micro_breath_478",
                    priority = 1,
                    steps =
                        listOf(
                            InterventionAction.MicroIntervention.MicroStep("Inhale quietly through your nose.", 4),
                            InterventionAction.MicroIntervention.MicroStep("Hold your breath.", 7),
                            InterventionAction.MicroIntervention.MicroStep("Exhale completely through your mouth.", 8),
                            InterventionAction.MicroIntervention.MicroStep("Inhale quietly through your nose.", 4),
                            InterventionAction.MicroIntervention.MicroStep("Hold your breath.", 7),
                            InterventionAction.MicroIntervention.MicroStep("Exhale completely through your mouth.", 8),
                            InterventionAction.MicroIntervention.MicroStep("Inhale quietly through your nose.", 4),
                            InterventionAction.MicroIntervention.MicroStep("Hold your breath.", 7),
                            InterventionAction.MicroIntervention.MicroStep("Exhale completely through your mouth.", 8),
                        ),
                    wellBeingDomain = WellBeingDomain.MENTAL,
                    isAutoAdvance = true,
                ),
                InterventionAction.MicroIntervention(
                    id = "micro_grounding_54321",
                    priority = 2,
                    steps =
                        listOf(
                            InterventionAction.MicroIntervention.MicroStep("Acknowledge 5 things you see around you.", 30),
                            InterventionAction.MicroIntervention.MicroStep("Acknowledge 4 things you can touch.", 30),
                            InterventionAction.MicroIntervention.MicroStep("Acknowledge 3 things you hear.", 30),
                            InterventionAction.MicroIntervention.MicroStep("Acknowledge 2 things you can smell.", 30),
                            InterventionAction.MicroIntervention.MicroStep("Acknowledge 1 thing you can taste.", 30),
                        ),
                    wellBeingDomain = WellBeingDomain.MENTAL,
                    isAutoAdvance = false,
                ),
                InterventionAction.MicroIntervention(
                    id = "micro_gratitude_moment",
                    priority = 3,
                    steps =
                        listOf(
                            InterventionAction.MicroIntervention.MicroStep("Close your eyes and take a deep breath.", 10),
                            InterventionAction.MicroIntervention.MicroStep("Think of one small thing that went well today.", 30),
                            InterventionAction.MicroIntervention.MicroStep("Let yourself feel genuine appreciation for it.", 20),
                        ),
                    wellBeingDomain = WellBeingDomain.MENTAL,
                    isAutoAdvance = false,
                ),
                // ==========================================
                // PHYSICAL DOMAIN
                // ==========================================
                InterventionAction.MicroIntervention(
                    id = "micro_neck_stretch",
                    priority = 4,
                    steps =
                        listOf(
                            InterventionAction.MicroIntervention.MicroStep("Drop your chin to your chest and hold.", 10),
                            InterventionAction.MicroIntervention.MicroStep("Gently roll your head to the left and hold.", 10),
                            InterventionAction.MicroIntervention.MicroStep("Roll your head to the right and hold.", 10),
                            InterventionAction.MicroIntervention.MicroStep("Slowly roll your head back and forth to release tension.", 10),
                        ),
                    wellBeingDomain = WellBeingDomain.PHYSICAL,
                    isAutoAdvance = true,
                ),
                InterventionAction.MicroIntervention(
                    id = "micro_posture_reset",
                    priority = 5,
                    steps =
                        listOf(
                            InterventionAction.MicroIntervention.MicroStep("Plant both feet flat on the floor.", 5),
                            InterventionAction.MicroIntervention.MicroStep("Sit up straight and roll your shoulders back and down.", 5),
                            InterventionAction.MicroIntervention.MicroStep(
                                "Tuck your chin slightly to align your neck with your spine.",
                                5,
                            ),
                            InterventionAction.MicroIntervention.MicroStep("Take a deep, expanding breath in this strong posture.", 10),
                        ),
                    wellBeingDomain = WellBeingDomain.PHYSICAL,
                    isAutoAdvance = true,
                ),
                InterventionAction.MicroIntervention(
                    id = "micro_eye_palming",
                    priority = 6,
                    steps =
                        listOf(
                            InterventionAction.MicroIntervention.MicroStep("Rub your hands together briskly to generate warmth.", 10),
                            InterventionAction.MicroIntervention.MicroStep("Close your eyes and gently cup your warm hands over them.", 5),
                            InterventionAction.MicroIntervention.MicroStep("Breathe slowly and enjoy the soothing darkness.", 30),
                        ),
                    wellBeingDomain = WellBeingDomain.PHYSICAL,
                    isAutoAdvance = true,
                ),
                // ==========================================
                // SLEEP DOMAIN
                // ==========================================
                InterventionAction.MicroIntervention(
                    id = "micro_box_breathing",
                    priority = 7,
                    steps =
                        listOf(
                            InterventionAction.MicroIntervention.MicroStep("Inhale slowly through your nose.", 4),
                            InterventionAction.MicroIntervention.MicroStep("Hold your breath.", 4),
                            InterventionAction.MicroIntervention.MicroStep("Exhale slowly through your mouth.", 4),
                            InterventionAction.MicroIntervention.MicroStep("Hold your breath empty.", 4),
                            InterventionAction.MicroIntervention.MicroStep("Inhale slowly through your nose.", 4),
                            InterventionAction.MicroIntervention.MicroStep("Hold your breath.", 4),
                            InterventionAction.MicroIntervention.MicroStep("Exhale slowly through your mouth.", 4),
                            InterventionAction.MicroIntervention.MicroStep("Hold your breath empty.", 4),
                        ),
                    wellBeingDomain = WellBeingDomain.SLEEP,
                    isAutoAdvance = true,
                ),
                InterventionAction.MicroIntervention(
                    id = "micro_progressive_relaxation",
                    priority = 8,
                    steps =
                        listOf(
                            InterventionAction.MicroIntervention.MicroStep("Tense the muscles in your feet and toes tightly.", 5),
                            InterventionAction.MicroIntervention.MicroStep("Release completely and notice the heaviness.", 10),
                            InterventionAction.MicroIntervention.MicroStep("Tense your leg muscles tightly.", 5),
                            InterventionAction.MicroIntervention.MicroStep("Release completely, letting them sink into the surface.", 10),
                            InterventionAction.MicroIntervention.MicroStep("Tense your shoulders and hands tightly.", 5),
                            InterventionAction.MicroIntervention.MicroStep("Release completely and let your whole body go limp.", 15),
                        ),
                    wellBeingDomain = WellBeingDomain.SLEEP,
                    isAutoAdvance = true,
                ),
                InterventionAction.MicroIntervention(
                    id = "micro_sleep_environment",
                    priority = 9,
                    steps =
                        listOf(
                            InterventionAction.MicroIntervention.MicroStep("Dim your screen brightness or turn on Night Light.", 15),
                            InterventionAction.MicroIntervention.MicroStep("Ensure your room is cool and as dark as possible.", 20),
                            InterventionAction.MicroIntervention.MicroStep("Put your phone on silent or Do Not Disturb.", 15),
                        ),
                    wellBeingDomain = WellBeingDomain.SLEEP,
                    isAutoAdvance = false,
                ),
                // ==========================================
                // DIGITAL DOMAIN
                // ==========================================
                InterventionAction.MicroIntervention(
                    id = "micro_rule_20_20_20",
                    priority = 10,
                    steps =
                        listOf(
                            InterventionAction.MicroIntervention.MicroStep("Look away from your screen.", 5),
                            InterventionAction.MicroIntervention.MicroStep("Find an object about 20 feet away.", 5),
                            InterventionAction.MicroIntervention.MicroStep("Focus on it continuously to let your eyes fully relax.", 20),
                        ),
                    wellBeingDomain = WellBeingDomain.DIGITAL,
                    isAutoAdvance = false,
                ),
                InterventionAction.MicroIntervention(
                    id = "micro_digital_distance",
                    priority = 11,
                    steps =
                        listOf(
                            InterventionAction.MicroIntervention.MicroStep("Lock your device screen.", 5),
                            InterventionAction.MicroIntervention.MicroStep("Place it face down, out of arm's reach.", 10),
                            InterventionAction.MicroIntervention.MicroStep("Take three slow, deep breaths entirely offline.", 30),
                        ),
                    wellBeingDomain = WellBeingDomain.DIGITAL,
                    isAutoAdvance = false,
                ),
                InterventionAction.MicroIntervention(
                    id = "micro_notification_sweep",
                    priority = 12,
                    steps =
                        listOf(
                            InterventionAction.MicroIntervention.MicroStep("Swipe away any non-essential notifications.", 20),
                            InterventionAction.MicroIntervention.MicroStep("Close apps you are mindlessly switching between.", 15),
                            InterventionAction.MicroIntervention.MicroStep(
                                "Consider turning on Do Not Disturb for the next 30 minutes.",
                                20,
                            ),
                        ),
                    wellBeingDomain = WellBeingDomain.DIGITAL,
                    isAutoAdvance = false,
                ),
                // ==========================================
                // SOCIAL DOMAIN
                // ==========================================
                InterventionAction.MicroIntervention(
                    id = "micro_quick_connection",
                    priority = 13,
                    steps =
                        listOf(
                            InterventionAction.MicroIntervention.MicroStep("Think of someone who has supported you recently.", 15),
                            InterventionAction.MicroIntervention.MicroStep("Draft a short message expressing your appreciation.", 30),
                            InterventionAction.MicroIntervention.MicroStep("Send it, or save it to share with them later.", 15),
                        ),
                    wellBeingDomain = WellBeingDomain.SOCIAL,
                    isAutoAdvance = false,
                ),
            )
        }
    }
