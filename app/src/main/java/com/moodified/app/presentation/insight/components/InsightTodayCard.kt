package com.moodified.app.presentation.insight.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.DmSerifDisplay
import com.moodified.app.core.theme.ErrorRed
import com.moodified.app.core.theme.MilkDeep
import com.moodified.app.core.theme.SageDim
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextTertiary

@Composable
fun InsightTodayCard(
    title: String,
    metrics: List<InsightMetric>,
    modifier: Modifier = Modifier,
    trendMetrics: List<InsightMetric>? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = MilkDeep,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = title.uppercase(),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = TextTertiary,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                metrics.forEach { metric ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = metric.value,
                            style =
                                MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = DmSerifDisplay,
                                    fontSize = 22.sp,
                                ),
                            color = TextPrimary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = metric.label.uppercase(),
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    letterSpacing = 0.8.sp,
                                ),
                            color = TextTertiary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            if (!trendMetrics.isNullOrEmpty()) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = SageDim.copy(alpha = 0.5f), thickness = 0.5.dp)
                Spacer(Modifier.height(12.dp))
                InsightTrendRow(trendMetrics)
            }
        }
    }
}

@Composable
private fun InsightTrendRow(metrics: List<InsightMetric>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        metrics.forEach { metric ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = metric.value,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = if (metric.warn) ErrorRed else TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = metric.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
                    color = TextTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
