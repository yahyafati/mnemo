package com.yahyafati.mnemo.core.ui.scroll

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable

// Touch scrolling needs no bar.
@Composable
actual fun BoxScope.ScrollbarFor(state: ScrollState) = Unit

@Composable
actual fun BoxScope.ScrollbarFor(state: LazyListState) = Unit

@Composable
actual fun BoxScope.ScrollbarFor(state: LazyGridState) = Unit
