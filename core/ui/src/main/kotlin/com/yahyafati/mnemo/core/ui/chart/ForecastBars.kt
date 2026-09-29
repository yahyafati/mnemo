package com.yahyafati.mnemo.core.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One bar per day for the due forecast. Bars scale to the largest value; a day with nothing due
 * shows a stub so the timeline stays readable. [highlight] marks the bars drawn in [accent].
 */
@Composable
fun ForecastBars(
    values: List<Int>,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
    accent: Color = color,
    highlight: (index: Int) -> Boolean = { false },
    height: Dp = 56.dp,
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description },
    ) {
        if (values.isEmpty()) return@Canvas
        val max = values.max().coerceAtLeast(1)
        val gap = 4.dp.toPx()
        val barWidth = (size.width - gap * (values.size - 1)) / values.size
        val stub = 2.dp.toPx()
        values.forEachIndexed { i, value ->
            val barHeight = if (value == 0) stub else (size.height * value / max).coerceAtLeast(stub)
            drawRoundRect(
                color = if (highlight(i)) accent else color,
                topLeft = Offset(i * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ForecastBarsPreview() {
    ForecastBars(listOf(12, 22, 18, 31, 0, 9, 14), Color.Gray, "", accent = Color.Blue, highlight = { it == 3 })
}
