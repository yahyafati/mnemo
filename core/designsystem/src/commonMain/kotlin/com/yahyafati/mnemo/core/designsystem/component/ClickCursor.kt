package com.yahyafati.mnemo.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/**
 * The hand cursor over something that can be clicked (desktop ROADMAP D7). Material's components leave
 * the arrow, which a mouse user reads as "nothing here"; a phone has no cursor and ignores this.
 */
fun Modifier.clickCursor(): Modifier = pointerHoverIcon(PointerIcon.Hand)
