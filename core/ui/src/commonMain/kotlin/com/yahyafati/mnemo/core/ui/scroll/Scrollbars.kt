package com.yahyafati.mnemo.core.ui.scroll

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Scrollbars for a mouse (desktop ROADMAP D7). A phone scrolls by touch and draws a fleeting indicator
// of its own; a computer needs a bar to see where it is in a long page and to drag. These draw one on the
// end edge of the box they sit in, over the content, and only on the desktop.

/** A bar for content scrolled with [ScrollState], on the end edge of this box. */
@Composable
expect fun BoxScope.ScrollbarFor(state: ScrollState)

/** A bar for a [LazyListState] list (`LazyColumn`). */
@Composable
expect fun BoxScope.ScrollbarFor(state: LazyListState)

/** A bar for a [LazyGridState] grid. */
@Composable
expect fun BoxScope.ScrollbarFor(state: LazyGridState)

/** [content] with a scrollbar for [state] over its end edge. */
@Composable
fun ScrollbarBox(state: ScrollState, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        content()
        ScrollbarFor(state)
    }
}

/** [content] (a `LazyColumn` with [state]) with a scrollbar over its end edge. */
@Composable
fun ScrollbarBox(state: LazyListState, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        content()
        ScrollbarFor(state)
    }
}

/** [content] (a lazy grid with [state]) with a scrollbar over its end edge. */
@Composable
fun ScrollbarBox(state: LazyGridState, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        content()
        ScrollbarFor(state)
    }
}
