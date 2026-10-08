package com.moodified.app.presentation.insight.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.DeepSage
import com.moodified.app.core.theme.SageDim
import com.moodified.app.core.theme.TextTertiary
import com.moodified.app.core.theme.ValenceNegative
import com.moodified.app.core.utils.DateTimeUtils
import com.moodified.app.domain.model.sleep.ManualSleepEntry
import com.moodified.app.domain.model.sleep.SleepTrends
import com.moodified.app.presentation.insight.InsightUiState
import com.moodified.app.presentation.insight.SleepBarPoint
import com.moodified.app.presentation.insight.components.InsightChartSurface
import com.moodified.app.presentation.insight.components.InsightDomainEmptyState
import com.moodified.app.presentation.insight.components.InsightDomainTemplate
import com.moodified.app.presentation.insight.components.InsightLegendDot
import com.moodified.app.presentation.insight.components.InsightMetric
import com.moodified.app.presentation.insight.components.InsightTodayCard
import com.moodified.app.presentation.insight.components.ManualSleepEntrySheet
import com.moodified.app.presentation.insight.components.drawInsightGridLines
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SleepTab(
    state: InsightUiState,
    onBarTap: (LocalDate) -> Unit,
    sleepCorrectionSheetDate: LocalDate?,
    sleepCorrectionEntries: List<ManualSleepEntry>,
    onAddManualEntry: (LocalDate, Long, Long) -> Unit,
    onDeleteManualEntry: (Long) -> Unit,
    onRevertToInferred: (LocalDate) -> Unit,
    onSheetDismiss: () -> Unit,
) {
    val isReady = state.domainReadiness.sleep.isReady
    InsightDomainTemplate(
        title = "Sleep",
        subtitle = "Last 7 days",
        isTracking = isReady,
        status =
            if (!isReady) {
                {
                    InsightDomainEmptyState(
                        title = "Sleep insights are on the way",
                        expectation =
                            "Your sleep window is inferred overnight — the first summary lands " +
                                "the morning after you sleep with your phone nearby.",
                    )
                }
            } else {
                null
            },
        stats =
            if (isReady) {
                {
                    val trendMetrics = state.sleepTrends?.let { sleepTrendMetrics(it) }
                    val night = state.sleepLastNight
                    if (night != null) {
                        val fellAsleepAt =
                            night.sleepOnsetMinutes?.let { DateTimeUtils.offsetMinutesToClockTime(it) } ?: "—"
                        InsightTodayCard(
                            title = "Last night",
                            metrics =
                                listOf(
                                    InsightMetric("Total Sleep", DateTimeUtils.formatMinutes(night.totalSleepMinutes)),
                                    InsightMetric("Awakenings", "${night.awakenings}"),
                                    InsightMetric("Fell asleep", fellAsleepAt),
                                ),
                            trendMetrics = trendMetrics,
                        )
                    } else if (trendMetrics != null) {
                        InsightTodayCard(title = "This week", metrics = trendMetrics)
                    }
                }
            } else {
                null
            },
        breakdown =
            if (isReady) {
                {
                    SleepTabBarChart(
                        points = state.sleepBarPoints,
                        onBarTap = onBarTap,
                        insight = state.sleepMoodInsight,
                    )
                }
            } else {
                null
            },
    )

    if (sleepCorrectionSheetDate != null) {
        ManualSleepEntrySheet(
            date = sleepCorrectionSheetDate,
            existingEntries = sleepCorrectionEntries,
            onAdd = { startMs, endMs -> onAddManualEntry(sleepCorrectionSheetDate, startMs, endMs) },
            onDelete = onDeleteManualEntry,
            onRevert = { onRevertToInferred(sleepCorrectionSheetDate) },
            onDismiss = onSheetDismiss,
        )
    }
}

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

private fun sleepTrendMetrics(trends: SleepTrends): List<InsightMetric> {
    val avgHours = trends.averageSleepMinutes / 60
    val avgMins = trends.averageSleepMinutes % 60
    val metrics = mutableListOf(InsightMetric("avg", "${avgHours}h ${avgMins}m"))
    if (trends.totalSleepDebtMinutes > 0) {
        val dh = trends.totalSleepDebtMinutes / 60
        val dm = trends.totalSleepDebtMinutes % 60
        metrics += InsightMetric("lost rest", "${dh}h ${dm}m", warn = true)
    }
    metrics += InsightMetric("consistency", "${trends.consistencyScore}%")
    return metrics
}

@Composable
private fun SleepTabBarChart(
    points: List<SleepBarPoint>,
    onBarTap: (LocalDate) -> Unit,
    insight: String? = null,
) {
    if (points.isEmpty()) return

    val maxDataMinutes = points.maxOfOrNull { it.totalSleepMinutes } ?: 0
    val maxMinutes = sleepTabDynamicChartMaxMinutes(maxDataMinutes)
    val maxHours = maxMinutes / 60
    val goalLine = 420
    val dayFmt = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val chartHeight = 160.dp

    InsightChartSurface {
        Row(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            Column(modifier = Modifier.fillMaxHeight().width(38.dp)) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxSize().offset(y = (-6).dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${maxHours}h", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                        Text(
                            "${maxHours * 2 / 3}h",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = TextTertiary,
                        )
                        Text("${maxHours / 3}h", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                        Text("0h", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(" ", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
            }

            Row(
                modifier =
                    Modifier.weight(1f).fillMaxHeight().drawWithContent {
                        drawContent()
                        drawInsightGridLines()
                    },
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                points.forEach { pt ->
                    val fraction = (pt.totalSleepMinutes.toFloat() / maxMinutes).coerceIn(0f, 1f)
                    val isGoalMet = pt.totalSleepMinutes >= goalLine

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).fillMaxHeight().clickable { onBarTap(pt.date) },
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            val barColor = if (isGoalMet) Color(0xAA67C967) else ValenceNegative.copy(alpha = 0.6f)

                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(0.55f)
                                        .fillMaxHeight(fraction)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(barColor),
                            )
                            if (pt.totalSleepMinutes > 0) {
                                Column(
                                    modifier = Modifier.fillMaxHeight(fraction),
                                    verticalArrangement = Arrangement.Top,
                                ) {
                                    Text(
                                        text = DateTimeUtils.formatMinutes(pt.totalSleepMinutes),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        color = TextTertiary,
                                        modifier = Modifier.offset(y = (-14).dp),
                                    )
                                }
                            }
                            if (!pt.isEstimated) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(DeepSage)
                                            .align(Alignment.TopCenter),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = pt.date.format(dayFmt),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = TextTertiary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            InsightLegendDot(color = Color(0xAA67C967), label = "Restful sleep")
            InsightLegendDot(color = ValenceNegative.copy(alpha = 0.6f), label = "Short sleep")
            InsightLegendDot(color = DeepSage, label = "Manually logged")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Tap any bar to add or correct that night's sleep.",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = TextTertiary,
        )
        if (insight != null) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SageDim.copy(alpha = 0.3f),
                tonalElevation = 0.dp,
            ) {
                Text(
                    text = insight,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private fun sleepTabDynamicChartMaxMinutes(maxValue: Int): Int {
    val maxHours = (maxValue + 59) / 60
    var chartMaxHours = maxHours
    while (chartMaxHours % 3 != 0) {
        chartMaxHours++
    }
    if (chartMaxHours == 0) chartMaxHours = 3
    return chartMaxHours * 60
}
