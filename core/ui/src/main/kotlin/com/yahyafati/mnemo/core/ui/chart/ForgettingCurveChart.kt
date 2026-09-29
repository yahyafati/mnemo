package com.yahyafati.mnemo.core.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.CurvePoint
import com.yahyafati.mnemo.core.model.ForgettingCurve
import kotlin.math.roundToInt

/**
 * Recall over time for one card (Analytics mockup): the FSRS sawtooth, with a marker at each
 * scheduled review, over the passive decay of a card never reviewed again. Drawn on a Canvas, so
 * [description] is what screen readers hear.
 */
@Composable
fun ForgettingCurveChart(
    curve: ForgettingCurve,
    desiredRetention: Double,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val scheduledColor = colors.primaryContainer
    val passiveColor = colors.outlineVariant
    val gridColor = colors.surfaceContainerHigh
    val labelColor = colors.outline
    val markerLabelColor = colors.primary
    val markerBorder = colors.surfaceContainerLowest
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MnemoTheme.typography.metricSm.copy(fontSize = 9.sp, color = labelColor)
    val markerStyle = MnemoTheme.typography.metricSm.copy(fontSize = 9.sp, color = markerLabelColor)
    // The y axis shows the interesting band: from a little under the lowest passive recall to 100%.
    val floor = (curve.passive.minOf { it.recall } - 0.05).coerceIn(0.0, 0.8)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = description },
    ) {
        val left = 30.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = size.height - 6.dp.toPx()
        val right = size.width - 6.dp.toPx()
        fun x(day: Double) = left + (right - left) * (day / curve.horizonDays).toFloat()
        fun y(recall: Double) = bottom - (bottom - top) * ((recall - floor) / (1 - floor)).toFloat()

        // Reference lines: the target and the axis floor.
        listOf(desiredRetention, floor + (desiredRetention - floor) / 2, floor).forEach { level ->
            val isFloor = level == floor
            drawLine(
                color = gridColor,
                start = Offset(left, y(level)),
                end = Offset(right, y(level)),
                strokeWidth = 1.dp.toPx(),
                pathEffect = if (isFloor) null else PathEffect.dashPathEffect(floatArrayOf(4f, 6f)),
            )
            val label = textMeasurer.measure("${(level * 100).roundToInt()}%", labelStyle)
            drawText(label, topLeft = Offset(0f, y(level) - label.size.height / 2f))
        }

        drawSeries(curve.passive, ::x, ::y, bottom, passiveColor, dashed = true)
        drawSeries(curve.scheduled, ::x, ::y, bottom, scheduledColor, dashed = false)

        curve.reviewDays.forEach { day ->
            val center = Offset(x(day.toDouble()), y(1.0))
            drawCircle(markerBorder, radius = 5.5.dp.toPx(), center = center)
            drawCircle(scheduledColor, radius = 4.dp.toPx(), center = center)
            val label = textMeasurer.measure("D$day", markerStyle)
            val labelX = (center.x - label.size.width / 2f).coerceAtMost(size.width - label.size.width)
            drawText(label, topLeft = Offset(labelX, center.y - 7.dp.toPx() - label.size.height))
        }
    }
}

private fun DrawScope.drawSeries(
    points: List<CurvePoint>,
    x: (Double) -> Float,
    y: (Double) -> Float,
    bottom: Float,
    color: Color,
    dashed: Boolean,
) {
    if (points.isEmpty()) return
    val line = Path().apply {
        moveTo(x(points.first().day), y(points.first().recall))
        points.drop(1).forEach { lineTo(x(it.day), y(it.recall)) }
    }
    val area = Path().apply {
        addPath(line)
        lineTo(x(points.last().day), bottom)
        lineTo(x(points.first().day), bottom)
        close()
    }
    drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = if (dashed) 0.25f else 0.18f), Color.Transparent)))
    drawPath(
        line,
        color,
        style = Stroke(
            width = if (dashed) 1.75.dp.toPx() else 2.25.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) else null,
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun ForgettingCurveChartPreview() {
    val scheduled = buildList {
        var start = 0
        listOf(2, 13, 59, 60).forEach { next ->
            (0..(next - start) * 4).forEach { add(CurvePoint(start + it / 4.0, 1 - 0.1 * (it / 4.0) / (next - start))) }
            start = next
        }
    }
    MnemoTheme {
        ForgettingCurveChart(
            curve = ForgettingCurve(
                horizonDays = 60,
                scheduled = scheduled,
                reviewDays = listOf(2, 13, 59),
                passive = (0..240).map { CurvePoint(it / 4.0, 1 - 0.4 * it / 240.0) },
            ),
            desiredRetention = 0.9,
            description = "",
        )
    }
}

