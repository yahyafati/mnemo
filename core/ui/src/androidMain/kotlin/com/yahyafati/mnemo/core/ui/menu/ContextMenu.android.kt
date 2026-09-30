package com.yahyafati.mnemo.core.ui.menu

import androidx.compose.runtime.Composable

// A phone has no right click; its overflow buttons and long presses do this.
@Composable
actual fun ContextMenuHost(actions: List<ContextAction>, content: @Composable () -> Unit) = content()
