package com.yahyafati.mnemo.core.ui.scroll

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
private fun themedScrollbarStyle(): ScrollbarStyle {
    val onSurface = MaterialTheme.colorScheme.onSurface
    return remember(onSurface) {
        ScrollbarStyle(
            minimalHeight = 24.dp,
            thickness = 10.dp,
            shape = RoundedCornerShape(5.dp),
            hoverDurationMillis = 200,
            unhoverColor = onSurface.copy(alpha = 0.18f),
            hoverColor = onSurface.copy(alpha = 0.5f),
        )
    }
}

@Composable
actual fun BoxScope.ScrollbarFor(state: ScrollState) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 2.dp),
        style = themedScrollbarStyle(),
    )
}

@Composable
actual fun BoxScope.ScrollbarFor(state: LazyListState) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 2.dp),
        style = themedScrollbarStyle(),
    )
}

@Composable
actual fun BoxScope.ScrollbarFor(state: LazyGridState) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 2.dp),
        style = themedScrollbarStyle(),
    )
}
