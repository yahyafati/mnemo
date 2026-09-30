package com.yahyafati.mnemo.core.ui.keyboard

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent

/** What a [Shortcut] does. A shortcut can have several keys (Space and Enter both reveal the answer). */
class ShortcutBinding(val shortcuts: List<Shortcut>, val action: () -> Unit)

infix fun Shortcut.does(action: () -> Unit) = ShortcutBinding(listOf(this), action)

infix fun List<Shortcut>.does(action: () -> Unit) = ShortcutBinding(this, action)

/** Runs the first binding whose shortcut is [event]; true if there was one, which consumes the key. */
fun List<ShortcutBinding>.dispatch(event: KeyEvent): Boolean {
    val binding = firstOrNull { b -> b.shortcuts.any { it.matches(event) } } ?: return false
    binding.action()
    return true
}

/**
 * Handles [bindings] for keys pressed while focus is in this element or below it. The key reaches
 * this handler after the focused child declined it, so a text field keeps its own keys (typing,
 * Ctrl+Z as text undo).
 */
fun Modifier.shortcuts(vararg bindings: ShortcutBinding): Modifier {
    val all = bindings.toList()
    return onKeyEvent { all.dispatch(it) }
}

/**
 * Like [shortcuts], but sees the key before the focused child does: for shortcuts that must win
 * over text fields (Ctrl+Enter saving from inside the editor's fields).
 */
fun Modifier.previewShortcuts(vararg bindings: ShortcutBinding): Modifier {
    val all = bindings.toList()
    return onPreviewKeyEvent { all.dispatch(it) }
}

/**
 * For a text field that sits inside something that reacts to keys. A focused field leaves the key going
 * down and up for its parents (only the typed character is its own), and a parent that is clickable
 * takes Space and Enter as a click: typing a space in the study card's answer box would flip the card.
 * This stops the keys a person types with (none of Ctrl, ⌘ or Alt held) from going further. Escape and
 * Tab still do, and so does anything with a command key, so the app's shortcuts keep working in the field.
 *
 * Desktop only: on a phone with a hardware keyboard the keyboard service gets a key after Compose has had
 * it, and a key consumed here would never be typed.
 */
fun Modifier.consumeTypingKeys(): Modifier = onKeyEvent { event ->
    (event.type == KeyEventType.KeyDown || event.type == KeyEventType.KeyUp) &&
        !event.isCtrlPressed && !event.isMetaPressed && !event.isAltPressed &&
        event.key != Key.Escape && event.key != Key.Tab
}
