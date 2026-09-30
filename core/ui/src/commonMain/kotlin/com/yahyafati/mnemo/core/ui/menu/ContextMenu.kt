package com.yahyafati.mnemo.core.ui.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/** One entry of a right-click menu. */
@Immutable
class ContextAction(val label: String, val onClick: () -> Unit)

/**
 * Shows [actions] in a menu at the pointer when [content] is right-clicked (desktop ROADMAP D7). The
 * entries are the ones the same thing already offers from its overflow button: a right click is a
 * faster way to them, never the only way. On a phone there is no right click and this is just [content].
 *
 * [actions] are read when the menu opens, so they can depend on state the click is about.
 */
@Composable
expect fun ContextMenuHost(actions: List<ContextAction>, content: @Composable () -> Unit)
