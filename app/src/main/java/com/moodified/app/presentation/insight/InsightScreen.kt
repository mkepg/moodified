package com.moodified.app.presentation.insight

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodified.app.core.theme.DeepSage
import com.moodified.app.core.theme.MilkWhite
import com.moodified.app.domain.model.sleep.ManualSleepEntry
import com.moodified.app.presentation.insight.tabs.ActivityTab
import com.moodified.app.presentation.insight.tabs.OverviewTab
import com.moodified.app.presentation.insight.tabs.ScreenUseTab
import com.moodified.app.presentation.insight.tabs.SleepTab
import java.time.LocalDate

@Composable
fun InsightScreen(
    initialTab: InsightTab = InsightTab.OVERVIEW,
    viewModel: InsightViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetDate by viewModel.sleepCorrectionSheetDate.collectAsStateWithLifecycle()
    val sheetEntries by viewModel.sleepCorrectionEntries.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }

    AnimatedContent(
        targetState = state.isLoading,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
        label = "insightRoot",
    ) { loading ->
        if (loading) {
            InsightLoading()
        } else {
            InsightContent(
                state = state,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                sleepCorrectionSheetDate = sheetDate,
                sleepCorrectionEntries = sheetEntries,
                onBarTap = viewModel::openSleepCorrectionSheet,
                onAddManualEntry = viewModel::addManualSleepEntry,
                onDeleteManualEntry = viewModel::deleteManualSleepEntry,
                onRevertToInferred = viewModel::revertToInferred,
                onSheetDismiss = viewModel::closeSleepCorrectionSheet,
            )
        }
    }
}

@Composable
private fun InsightLoading() {
    Box(
        modifier = Modifier.fillMaxSize().background(MilkWhite).statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = DeepSage, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun InsightContent(
    state: InsightUiState,
    selectedTab: InsightTab,
    onTabSelected: (InsightTab) -> Unit,
    sleepCorrectionSheetDate: LocalDate?,
    sleepCorrectionEntries: List<ManualSleepEntry>,
    onBarTap: (LocalDate) -> Unit,
    onAddManualEntry: (LocalDate, Long, Long) -> Unit,
    onDeleteManualEntry: (Long) -> Unit,
    onRevertToInferred: (LocalDate) -> Unit,
    onSheetDismiss: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(MilkWhite)) {
        SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            InsightTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    text = {
                        Text(
                            text = tab.label(),
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                )
            }
        }
        when (selectedTab) {
            InsightTab.OVERVIEW -> OverviewTab(state)
            InsightTab.ACTIVITY -> ActivityTab(state)
            InsightTab.SLEEP ->
                SleepTab(
                    state = state,
                    onBarTap = onBarTap,
                    sleepCorrectionSheetDate = sleepCorrectionSheetDate,
                    sleepCorrectionEntries = sleepCorrectionEntries,
                    onAddManualEntry = onAddManualEntry,
                    onDeleteManualEntry = onDeleteManualEntry,
                    onRevertToInferred = onRevertToInferred,
                    onSheetDismiss = onSheetDismiss,
                )
            InsightTab.SCREEN_USE -> ScreenUseTab(state)
        }
    }
}

private fun InsightTab.label(): String =
    when (this) {
        InsightTab.OVERVIEW -> "Overview"
        InsightTab.ACTIVITY -> "Activity"
        InsightTab.SLEEP -> "Sleep"
        InsightTab.SCREEN_USE -> "Screen"
    }
