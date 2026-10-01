package com.moodified.app.presentation.insight.tabs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val MoodSparklineColor = Color(0xFF4A7C59) // matches DeepSage; adjust if needed

internal fun DrawScope.drawMoodSparkline(
    moodPoints: List<Float?>,
    dotRadius: Dp = 4.dp,
) {
    if (moodPoints.isEmpty()) return
    val slotWidth = size.width / moodPoints.size.coerceAtLeast(1).toFloat()
    val path = Path()
    var pathStarted = false

    moodPoints.forEachIndexed { i, value ->
        if (value != null) {
            val x = slotWidth * i + slotWidth / 2f
            val y = size.height * (1f - value.coerceIn(0f, 1f))
            if (!pathStarted) {
                path.moveTo(x, y)
                pathStarted = true
            } else {
                path.lineTo(x, y)
            }
        } else {
            pathStarted = false // gap in line when day has no mood data
        }
    }

    drawPath(
        path = path,
        color = MoodSparklineColor,
        style = Stroke(width = 2.dp.toPx()),
        alpha = 0.75f,
    )

    moodPoints.forEachIndexed { i, value ->
        if (value != null) {
            val x = slotWidth * i + slotWidth / 2f
            val y = size.height * (1f - value.coerceIn(0f, 1f))
            drawCircle(
                color = MoodSparklineColor,
                radius = dotRadius.toPx(),
                center = Offset(x, y),
                alpha = 0.75f,
            )
        }
    }
}
