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
import androidx.compose.material3.Surface
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
import com.moodified.app.core.theme.ArousalHigh
import com.moodified.app.core.theme.ArousalMid
import com.moodified.app.core.theme.MilkDeep
import com.moodified.app.core.theme.SageDim
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.core.theme.TextTertiary
import com.moodified.app.core.theme.ValencePositive
import com.moodified.app.domain.model.activity.ActivityTrends
import com.moodified.app.presentation.insight.ActivityBarPoint
import com.moodified.app.presentation.insight.InsightUiState
import com.moodified.app.presentation.insight.components.InsightChartSurface
import com.moodified.app.presentation.insight.components.InsightDomainEmptyState
import com.moodified.app.presentation.insight.components.InsightDomainTemplate
import com.moodified.app.presentation.insight.components.InsightLegendDot
import com.moodified.app.presentation.insight.components.InsightMetric
import com.moodified.app.presentation.insight.components.InsightTodayCard
import com.moodified.app.presentation.insight.components.drawInsightGridLines
import com.moodified.app.presentation.insight.util.formatSteps
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Private constants (duplicated from InsightScreen.kt — Task 5 removes them there)
private val ColorLight = ArousalMid
private val ColorModerate = ValencePositive
private val ColorVigorous = ArousalHigh

@Composable
fun ActivityTab(state: InsightUiState) {
    val isReady = state.domainReadiness.activity.isReady
    InsightDomainTemplate(
        title = "Activity",
        subtitle = "Last 7 days",
        isTracking = isReady,
        status =
            if (!isReady) {
                {
                    InsightDomainEmptyState(
                        title = "Activity insights are on the way",
                        expectation =
                            "Your first summary appears within about an hour of enabling tracking, " +
                                "once movement is detected. Keep Moodified running in the background.",
                    )
                }
            } else {
                null
            },
        stats =
            if (isReady) {
                {
                    val trendMetrics = state.activityTrends?.let { activityTrendMetrics(it) }
                    val today = state.activityToday
                    if (today != null) {
                        InsightTodayCard(
                            title = "Today",
                            metrics =
                                listOf(
                                    InsightMetric("Steps", "%,d".format(today.totalSteps)),
                                    InsightMetric("Active", "${today.activeMinutes}m"),
                                    InsightMetric("Sedentary", "${today.sedentaryMinutes}m"),
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
                    ActivityTabStackedBarChart(
                        points = state.activityBarPoints,
                        insight = state.activityMoodInsight,
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

private fun activityTrendMetrics(trends: ActivityTrends): List<InsightMetric> =
    listOf(
        InsightMetric("avg steps", "%,d".format(trends.averageSteps)),
        InsightMetric("active min", "${trends.averageActiveMinutes}m"),
        InsightMetric("consistency", "${trends.consistencyScore}%"),
    )

@Composable
private fun ActivityTabStackedBarChart(
    points: List<ActivityBarPoint>,
    insight: String? = null,
) {
    if (points.isEmpty()) return

    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    val maxActiveMinutes = points.maxOfOrNull { it.activeMinutes } ?: 0
    val maxMinutes = activityTabDynamicChartMaxMinutes(maxActiveMinutes.coerceAtLeast(60))
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
                    },
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                points.forEach { pt ->
                    val activeMinutes = pt.activeMinutes
                    val totalFraction = (activeMinutes.toFloat() / maxMinutes).coerceIn(0f, 1f)

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
                            if (activeMinutes > 0) {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth(0.55f)
                                            .fillMaxHeight(totalFraction)
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                                    verticalArrangement = Arrangement.Bottom,
                                ) {
                                    val safeActive = activeMinutes.toFloat()

                                    if (pt.vigorousMinutes > 0) {
                                        Box(
                                            modifier =
                                                Modifier.fillMaxWidth().weight(
                                                    pt.vigorousMinutes / safeActive,
                                                ).background(ColorVigorous),
                                        )
                                    }
                                    if (pt.moderateMinutes > 0) {
                                        Box(
                                            modifier =
                                                Modifier.fillMaxWidth().weight(
                                                    pt.moderateMinutes / safeActive,
                                                ).background(ColorModerate),
                                        )
                                    }
                                    if (pt.lightMinutes > 0) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().weight(pt.lightMinutes / safeActive).background(ColorLight),
                                        )
                                    }
                                }
                            }

                            if (pt.totalSteps > 0) {
                                Column(
                                    modifier = Modifier.fillMaxHeight(totalFraction),
                                    verticalArrangement = Arrangement.Top,
                                ) {
                                    Text(
                                        text = formatSteps(pt.totalSteps),
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
                            AnimatedContent(targetState = selectedDate == pt.date, label = "activityBreakdown") { isSelected ->
                                if (isSelected) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (pt.vigorousMinutes > 0) {
                                            Text(
                                                "${pt.vigorousMinutes}m",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, color = ColorVigorous),
                                            )
                                        }
                                        if (pt.moderateMinutes > 0) {
                                            Text(
                                                "${pt.moderateMinutes}m",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, color = ColorModerate),
                                            )
                                        }
                                        if (pt.lightMinutes > 0) {
                                            Text(
                                                "${pt.lightMinutes}m",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, color = ColorLight),
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InsightLegendDot(color = ColorLight, label = "Light")
            InsightLegendDot(color = ColorModerate, label = "Moderate")
            InsightLegendDot(color = ColorVigorous, label = "Vigorous")
            Spacer(modifier = Modifier.weight(1f))
            InsightLegendDot(color = MilkDeep, label = "\"0.0k\" Step count")
        }
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
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private fun activityTabDynamicChartMaxMinutes(maxValue: Int): Int {
    val maxHours = (maxValue + 59) / 60
    var chartMaxHours = maxHours
    while (chartMaxHours % 3 != 0) {
        chartMaxHours++
    }
    if (chartMaxHours == 0) chartMaxHours = 3
    return chartMaxHours * 60
}
