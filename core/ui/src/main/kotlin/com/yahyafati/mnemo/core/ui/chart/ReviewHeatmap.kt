package com.yahyafati.mnemo.core.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme

/** One calendar day in [ReviewHeatmap]: its answer count, or null for a day still to come. */
data class HeatmapDay(val reviews: Int?, val isToday: Boolean = false, val description: String = "")

/**
 * The review calendar from the Analytics mockup: one row per week, one cell per day with the
 * day's answer count, shaded by how busy the day was compared with the busiest one.
 */
@Composable
fun ReviewHeatmap(
    weeks: List<List<HeatmapDay>>,
    weekdayLabels: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val max = weeks.flatten().maxOfOrNull { it.reviews ?: 0 }?.coerceAtLeast(1) ?: 1
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            weekdayLabels.forEach { label ->
                Text(
                    text = label,
                    style = MnemoTheme.typography.metricSm.copy(fontSize = 10.sp),
                    color = colors.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        weeks.forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { day -> Cell(day, max, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Cell(day: HeatmapDay, max: Int, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val reviews = day.reviews
    val intensity = if (reviews == null || reviews == 0) 0f else 0.25f + 0.75f * reviews / max
    val (background, content) = when {
        reviews == null -> colors.surfaceContainerLow to colors.outlineVariant
        reviews == 0 -> colors.surfaceContainer to colors.outline
        intensity > 0.75f -> colors.primary to colors.onPrimary
        else -> lerp(colors.surfaceContainerHigh, colors.primaryContainer, intensity) to colors.primary
    }
    Box(
        modifier = modifier
            .height(24.dp)
            .background(background, MaterialTheme.shapes.large)
            .semantics(mergeDescendants = true) { contentDescription = day.description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when (reviews) {
                null -> "–"
                0 -> ""
                else -> reviews.toString()
            },
            style = MnemoTheme.typography.metricSm.copy(
                fontSize = 9.sp,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
            ),
            color = content,
            maxLines = 1,
        )
    }
}

private fun lerp(from: Color, to: Color, fraction: Float): Color =
    androidx.compose.ui.graphics.lerp(from, to, fraction.coerceIn(0f, 1f))

@Preview(showBackground = true)
@Composable
private fun ReviewHeatmapPreview() {
    MnemoTheme {
        ReviewHeatmap(
            weeks = List(5) { w -> List(7) { d -> HeatmapDay(if (w == 4 && d > 3) null else (w * 7 + d) * 3 % 90, w == 4 && d == 3) } },
            weekdayLabels = listOf("M", "T", "W", "T", "F", "S", "S"),
        )
    }
}
