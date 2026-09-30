package com.yahyafati.mnemo.core.ui.keyboard

import androidx.compose.runtime.Immutable
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

/**
 * A key combination (desktop ROADMAP D7). There are two kinds.
 *
 * - A **key** with modifiers ([key], [primary], [shift], [alt]): Ctrl/⌘+N, Ctrl+Enter, Esc. [primary] is the
 *   platform's command key, ⌘ on macOS and Ctrl elsewhere. It matches the key going down with exactly
 *   its modifiers, so Ctrl+N doesn't fire on Ctrl+Shift+N.
 * - A **typed character** ([char]): Space, `1`, `e`, `?`. It matches the character the keystroke types, and
 *   only when nothing took it: a focused text field consumes the typed character, so typing "e" or a space
 *   in an answer never reaches a shortcut. (The key going down is not enough: the field leaves that
 *   one for its parents, which would make every `e` typed into a field an "edit" command.) A computer is
 *   the only platform that reports typed characters this way, so these shortcuts never fire on a phone.
 */
@Immutable
data class Shortcut(
    val key: Key? = null,
    val primary: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val char: Char? = null,
) {
    init {
        require((key == null) != (char == null)) { "A shortcut is a key or a character" }
        require(char == null || (!primary && !shift && !alt)) { "A typed character has no modifiers of its own" }
    }

    /** Whether [event] is this shortcut: a key going down, or a typed character. */
    fun matches(event: KeyEvent, mac: Boolean = isMacOs): Boolean {
        val command = if (mac) event.isMetaPressed else event.isCtrlPressed
        // The other one (Ctrl on macOS, the Windows key elsewhere) is never part of a shortcut.
        val other = if (mac) event.isCtrlPressed else event.isMetaPressed
        if (char != null) {
            // A typed character has no key, and its codepoint is the character (Ctrl+E types a control character, not `e`).
            if (event.type != KeyEventType.Unknown || command || other || event.isAltPressed) return false
            val typed = event.utf16CodePoint
            return typed == char.code || typed in ALSO_TYPED[char].orEmpty() || (char.isLetter() && typed == char.uppercaseChar().code)
        }
        if (event.type != KeyEventType.KeyDown) return false
        if (command != primary || other || event.isAltPressed != alt) return false
        return event.key in equivalents(key!!) && event.isShiftPressed == shift
    }

    /** What to print for this shortcut: `⇧⌘C` on macOS, `Ctrl+Shift+C` elsewhere. */
    fun label(mac: Boolean = isMacOs): String {
        if (char != null) return charName(char)
        val name = keyNames[key!!] ?: error("No label for $key: add it to keyNames")
        return if (mac) {
            (if (alt) "⌥" else "") + (if (shift) "⇧" else "") + (if (primary) "⌘" else "") + name
        } else {
            listOfNotNull("Ctrl".takeIf { primary }, "Alt".takeIf { alt }, "Shift".takeIf { shift }, name).joinToString("+")
        }
    }

    companion object {
        /** `?`, wherever the layout puts it. */
        val Question = Shortcut(char = '?')
    }
}

/** Enter types a line feed on some systems and a carriage return on others. */
private val ALSO_TYPED: Map<Char, List<Int>> = mapOf('\n' to listOf('\r'.code))

private fun charName(char: Char): String = when (char) {
    ' ' -> "Space"
    '\n' -> "Enter"
    else -> char.uppercaseChar().toString()
}

/** What the active platform calls its command key; the `os.name` property is "Mac OS X" on macOS. */
internal val isMacOs: Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)

/** The keys that mean the same as [key]: the number row and the keypad's digits, the two Enter keys. */
private fun equivalents(key: Key): List<Key> = when (key) {
    Key.Enter -> listOf(Key.Enter, Key.NumPadEnter)
    Key.One -> listOf(Key.One, Key.NumPad1)
    Key.Two -> listOf(Key.Two, Key.NumPad2)
    Key.Three -> listOf(Key.Three, Key.NumPad3)
    Key.Four -> listOf(Key.Four, Key.NumPad4)
    else -> listOf(key)
}

private val keyNames: Map<Key, String> = mapOf(
    Key.Enter to "Enter",
    Key.Spacebar to "Space",
    Key.Escape to "Esc",
    Key.Comma to ",",
    Key.Slash to "/",
    Key.Zero to "0",
    Key.One to "1",
    Key.Two to "2",
    Key.Three to "3",
    Key.Four to "4",
    Key.Five to "5",
    Key.Six to "6",
    Key.Seven to "7",
    Key.Eight to "8",
    Key.Nine to "9",
    Key.A to "A",
    Key.B to "B",
    Key.C to "C",
    Key.D to "D",
    Key.E to "E",
    Key.F to "F",
    Key.G to "G",
    Key.H to "H",
    Key.I to "I",
    Key.J to "J",
    Key.K to "K",
    Key.L to "L",
    Key.M to "M",
    Key.N to "N",
    Key.O to "O",
    Key.P to "P",
    Key.Q to "Q",
    Key.R to "R",
    Key.S to "S",
    Key.T to "T",
    Key.U to "U",
    Key.V to "V",
    Key.W to "W",
    Key.X to "X",
    Key.Y to "Y",
    Key.Z to "Z",
)
