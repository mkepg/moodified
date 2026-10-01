package com.moodified.app.presentation.insight.tabs

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.ArousalLow
import com.moodified.app.core.theme.ErrorRed
import com.moodified.app.core.theme.MilkDeep
import com.moodified.app.core.theme.SageDim
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.core.theme.TextTertiary
import com.moodified.app.core.theme.ValenceNeutral
import com.moodified.app.core.utils.DateTimeUtils
import com.moodified.app.domain.model.interaction.InteractionTrends
import com.moodified.app.presentation.insight.InsightUiState
import com.moodified.app.presentation.insight.ScreenTimeBarPoint
import com.moodified.app.presentation.insight.components.InsightChartSurface
import com.moodified.app.presentation.insight.components.InsightDomainEmptyState
import com.moodified.app.presentation.insight.components.InsightDomainTemplate
import com.moodified.app.presentation.insight.components.InsightLegendDot
import com.moodified.app.presentation.insight.components.InsightMetric
import com.moodified.app.presentation.insight.components.InsightTodayCard
import com.moodified.app.presentation.insight.components.drawInsightGridLines
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ScreenUseTab(state: InsightUiState) {
    val isReady = state.domainReadiness.phone.isReady
    InsightDomainTemplate(
        title = "Screen Use",
        subtitle = "Last 7 days",
        isTracking = isReady,
        status =
            if (!isReady) {
                {
                    InsightDomainEmptyState(
                        title = "Screen-use insights are on the way",
                        expectation =
                            "Your first summary appears within about an hour of enabling tracking. " +
                                "Make sure Usage Access is granted so unlocks and screen time can be recorded.",
                    )
                }
            } else {
                null
            },
        stats =
            if (isReady) {
                {
                    state.phoneToday?.let { today ->
                        InsightTodayCard(
                            title = "Today",
                            metrics =
                                listOf(
                                    InsightMetric("Screen Time", DateTimeUtils.formatMinutes(today.totalScreenTimeMinutes)),
                                    InsightMetric("Unlocks", "${today.unlockCount}"),
                                    InsightMetric("Late Night", DateTimeUtils.formatMinutes(today.lateNightUsageMinutes)),
                                ),
                        )
                    }
                    state.interactionTrends?.let { ScreenUseTabTrendRow(it) }
                }
            } else {
                null
            },
        breakdown =
            if (isReady) {
                {
                    ScreenUseTabBarChart(
                        points = state.screenTimePoints,
                        moodPoints = if (state.weeklyMoodPoints.size == state.screenTimePoints.size) state.weeklyMoodPoints else emptyList(),
                    )
                }
            } else {
                null
            },
    )
}

// ---------------------------------------------------------------------------
// Private helpers (duplicated from InsightScreen.kt — Task 5 removes them there)
// ---------------------------------------------------------------------------

@Composable
private fun ScreenUseTabTrendRow(trends: InteractionTrends) {
    val sh = trends.averageScreenTimeMinutes / 60
    val sm = trends.averageScreenTimeMinutes % 60
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenUseTabTrendPill(label = "avg screen", value = "${sh}h ${sm}m")
        if (trends.averageLateNightMinutes > 0) {
            ScreenUseTabTrendPill(
                label = "late night",
                value = "${trends.averageLateNightMinutes}m",
                warn = trends.averageLateNightMinutes > 30,
            )
        }
    }
}

@Composable
private fun ScreenUseTabTrendPill(
    label: String,
    value: String,
    warn: Boolean = false,
) {
    Column {
        Text(text = value, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = if (warn) ErrorRed else TextPrimary)
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
            color = TextTertiary,
        )
    }
}

@Composable
private fun ScreenUseTabBarChart(
    points: List<ScreenTimeBarPoint>,
    moodPoints: List<Float?> = emptyList(),
) {
    if (points.isEmpty()) return

    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    val maxDataMinutes = points.maxOfOrNull { it.totalScreenMinutes } ?: 0
    val maxMinutes = screenUseTabDynamicChartMaxMinutes(maxDataMinutes)
    val maxHours = maxMinutes / 60
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
                        if (moodPoints.size == points.size) {
                            drawMoodSparkline(moodPoints)
                        }
                    },
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                points.forEach { pt ->
                    val totalFraction = (pt.totalScreenMinutes.toFloat() / maxMinutes).coerceIn(0f, 1f)
                    val isHigh = pt.totalScreenMinutes > 240

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { selectedDate = if (selectedDate == pt.date) null else pt.date },
                                ),
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(0.55f)
                                        .fillMaxHeight(totalFraction)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                val safeTotal = pt.totalScreenMinutes.coerceAtLeast(1).toFloat()
                                val safeLate = minOf(pt.lateNightMinutes, pt.totalScreenMinutes).toFloat()
                                val remaining = safeTotal - safeLate

                                if (safeLate > 0) {
                                    Box(
                                        modifier =
                                            Modifier.fillMaxWidth().weight(
                                                safeLate / safeTotal,
                                            ).background(ValenceNeutral.copy(alpha = 0.6f)),
                                    )
                                }
                                if (remaining > 0) {
                                    val barColor = if (isHigh) ArousalLow.copy(alpha = 0.6f) else SageDim
                                    Box(modifier = Modifier.fillMaxWidth().weight(remaining / safeTotal).background(barColor))
                                }
                            }

                            if (pt.totalScreenMinutes > 0) {
                                Column(
                                    modifier = Modifier.fillMaxHeight(totalFraction),
                                    verticalArrangement = Arrangement.Top,
                                ) {
                                    Text(
                                        text = DateTimeUtils.formatMinutes(pt.totalScreenMinutes),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        color = TextTertiary,
                                        modifier = Modifier.offset(y = (-14).dp),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Box(
                            modifier = Modifier.height(32.dp),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            AnimatedContent(targetState = selectedDate == pt.date, label = "screenBreakdown") { isSelected ->
                                if (isSelected) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (pt.lateNightMinutes > 0) {
                                            Text(
                                                "${DateTimeUtils.formatMinutes(pt.lateNightMinutes)}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, color = ValenceNeutral),
                                            )
                                        }
                                        val dayMins = pt.totalScreenMinutes - pt.lateNightMinutes
                                        if (dayMins > 0) {
                                            Text(
                                                "${DateTimeUtils.formatMinutes(dayMins)}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, color = TextSecondary),
                                            )
                                        }
                                    }
                                } else {
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
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InsightLegendDot(color = ArousalLow.copy(alpha = 0.6f), label = "Screen time")
            InsightLegendDot(color = ValenceNeutral.copy(alpha = 0.6f), label = "Late night")
            Spacer(modifier = Modifier.weight(1f))
            InsightLegendDot(color = MilkDeep, label = "Tap bar")
        }
    }
}

private fun screenUseTabDynamicChartMaxMinutes(maxValue: Int): Int {
    val maxHours = (maxValue + 59) / 60
    var chartMaxHours = maxHours
    while (chartMaxHours % 3 != 0) {
        chartMaxHours++
    }
    if (chartMaxHours == 0) chartMaxHours = 3
    return chartMaxHours * 60
}
