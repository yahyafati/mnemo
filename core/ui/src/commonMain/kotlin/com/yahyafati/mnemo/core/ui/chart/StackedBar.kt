package com.yahyafati.mnemo.core.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A thin bar split into [segments] (value, color) in proportion; empty with no values. */
@Composable
fun StackedBar(
    segments: List<Pair<Int, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        segments.filter { it.first > 0 }.forEach { (value, color) ->
            Box(
                Modifier
                    .weight(value.toFloat())
                    .fillMaxHeight()
                    .background(color),
            )
        }
    }
}
