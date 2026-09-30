package com.yahyafati.mnemo.core.ui.files

import androidx.compose.ui.Modifier

/**
 * Takes files dragged from the file manager and dropped on this element (desktop ROADMAP D7). A phone
 * has no such drag, so there this does nothing.
 *
 * - [accepts] says whether a file name is one this target opens; a drag with none of those is not
 *   taken, so a target further out in the layout can have it, and the pointer doesn't show a drop.
 * - [onHover] is told when a drag that the target would take enters it and when it leaves, to draw a
 *   "drop here" state.
 * - [onDrop] gets the paths of the accepted files, in the order the file manager gave them.
 */
expect fun Modifier.fileDropTarget(
    accepts: (fileName: String) -> Boolean = { true },
    onHover: (Boolean) -> Unit = {},
    onDrop: (paths: List<String>) -> Unit,
): Modifier
