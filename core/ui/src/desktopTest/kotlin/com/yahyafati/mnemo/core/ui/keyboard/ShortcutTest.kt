package com.yahyafati.mnemo.core.ui.keyboard

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What counts as a shortcut being pressed, and what it is called on each system (desktop ROADMAP D7). */
@OptIn(InternalComposeUiApi::class) // the only way to make a key event without a window
class ShortcutTest {
    // A key going down, as the window delivers it: the key, what it would type, and which modifiers are down.
    private fun down(key: Key, char: Char? = null, ctrl: Boolean = false, meta: Boolean = false, shift: Boolean = false) = KeyEvent(
        key = key,
        type = KeyEventType.KeyDown,
        codePoint = char?.code ?: 0,
        isCtrlPressed = ctrl,
        isMetaPressed = meta,
        isShiftPressed = shift,
    )

    // The typed character of a keystroke, which comes after the key going down and before it comes up.
    private fun typed(char: Char, ctrl: Boolean = false, meta: Boolean = false) = KeyEvent(
        key = Key.Unknown,
        type = KeyEventType.Unknown,
        codePoint = char.code,
        isCtrlPressed = ctrl,
        isMetaPressed = meta,
    )

    @Test
    fun theCommandKeyIsCommandOnMacAndControlElsewhere() {
        val save = Shortcuts.Save
        assertTrue(save.matches(down(Key.Enter, meta = true), mac = true))
        assertFalse(save.matches(down(Key.Enter, ctrl = true), mac = true))
        assertTrue(save.matches(down(Key.Enter, ctrl = true), mac = false))
        assertFalse(save.matches(down(Key.Enter, meta = true), mac = false))
        // The keypad's Enter is an Enter.
        assertTrue(save.matches(down(Key.NumPadEnter, ctrl = true), mac = false))
    }

    @Test
    fun aKeyShortcutNeedsExactlyItsModifiers() {
        // New note is Ctrl+N, not Ctrl+Shift+N, and not the Windows key.
        val newNote = Shortcuts.NewNote
        assertTrue(newNote.matches(down(Key.N, ctrl = true), mac = false))
        assertFalse(newNote.matches(down(Key.N, ctrl = true, shift = true), mac = false))
        assertFalse(newNote.matches(down(Key.N, ctrl = true, meta = true), mac = false))
        assertFalse(newNote.matches(down(Key.N), mac = false))
        // The cloze shortcut needs Shift.
        assertTrue(Shortcuts.Cloze.matches(down(Key.C, ctrl = true, shift = true), mac = false))
        assertFalse(Shortcuts.Cloze.matches(down(Key.C, ctrl = true), mac = false))
        // Escape is a key with no modifiers, and a key coming up is not a press.
        assertTrue(Shortcuts.Back.matches(down(Key.Escape), mac = false))
        assertFalse(Shortcuts.Back.matches(KeyEvent(key = Key.Escape, type = KeyEventType.KeyUp), mac = false))
    }

    @Test
    fun aCharacterShortcutIsTheCharacterTyped() {
        val edit = Shortcuts.EditNote
        assertTrue(edit.matches(typed('e'), mac = false))
        assertTrue(edit.matches(typed('E'), mac = false)) // caps lock
        assertFalse(edit.matches(typed('x'), mac = false))
        // Ctrl+E types a control character, not an e; and a typed e with Ctrl held is not E.
        assertFalse(edit.matches(typed('\u0005', ctrl = true), mac = false))
        assertFalse(edit.matches(typed('e', ctrl = true), mac = false))
        // The digits, on the number row or the keypad, type the same characters.
        assertTrue(Shortcuts.Answers[2].matches(typed('3'), mac = false))
        assertFalse(Shortcuts.Answers[2].matches(typed('4'), mac = false))
        // Enter types a line feed or a carriage return, depending on the system.
        assertTrue(Shortcuts.Reveal.any { it.matches(typed('\n'), mac = false) })
        assertTrue(Shortcuts.Reveal.any { it.matches(typed('\r'), mac = false) })
        assertTrue(Shortcuts.Reveal.any { it.matches(typed(' '), mac = false) })
        // A question mark is whatever types one, on any layout.
        assertTrue(Shortcuts.ShowShortcuts.matches(typed('?'), mac = false))
    }

    @Test
    fun typingInATextFieldNeverTriggersACharacterShortcut() {
        // What a parent of a focused text field is given while someone types "e", a space and "?": the keys
        // going down and up, which the field leaves for its parents, but not the typed characters, which it
        // consumes. None of those is a shortcut.
        val seenByParents = listOf(
            down(Key.E, 'e'), KeyEvent(key = Key.E, type = KeyEventType.KeyUp, codePoint = 'e'.code),
            down(Key.Spacebar, ' '), KeyEvent(key = Key.Spacebar, type = KeyEventType.KeyUp, codePoint = ' '.code),
            down(Key.Slash, '?', shift = true), KeyEvent(key = Key.Slash, type = KeyEventType.KeyUp, codePoint = '?'.code),
            down(Key.Three, '3'),
        )
        val characterShortcuts = Shortcuts.Reveal + Shortcuts.Answers + Shortcuts.EditNote + Shortcuts.ShowShortcuts
        for (event in seenByParents) {
            assertFalse(characterShortcuts.any { it.matches(event, mac = false) }, "matched $event")
        }
    }

    @Test
    fun labelsFollowTheSystemsConvention() {
        assertEquals("⌘N", Shortcuts.NewNote.label(mac = true))
        assertEquals("Ctrl+N", Shortcuts.NewNote.label(mac = false))
        assertEquals("⇧⌘C", Shortcuts.Cloze.label(mac = true))
        assertEquals("Ctrl+Shift+C", Shortcuts.Cloze.label(mac = false))
        assertEquals(listOf("Space", "Enter"), Shortcuts.Reveal.map { it.label(mac = true) })
        assertEquals("E", Shortcuts.EditNote.label(mac = false))
        assertEquals("3", Shortcuts.Answers[2].label(mac = false))
        assertEquals("Esc", Shortcuts.Back.label(mac = false))
        assertEquals("?", Shortcuts.ShowShortcuts.label(mac = false))
        assertEquals("⌘,", Shortcuts.Settings.label(mac = true))
        // Every shortcut the app has can be printed, on both systems.
        val all = listOf(
            Shortcuts.NewNote, Shortcuts.Search, Shortcuts.Settings, Shortcuts.Import, Shortcuts.ShowShortcuts, Shortcuts.Back,
            Shortcuts.Undo, Shortcuts.EditNote, Shortcuts.Save, Shortcuts.Cloze,
        ) + Shortcuts.Tabs + Shortcuts.Reveal + Shortcuts.Answers
        for (shortcut in all) {
            assertTrue(shortcut.label(mac = true).isNotEmpty())
            assertTrue(shortcut.label(mac = false).isNotEmpty())
        }
    }

    @Test
    fun theFirstMatchingBindingRunsAndTheKeyIsConsumed() {
        val ran = mutableListOf<String>()
        val bindings = listOf(
            Shortcuts.EditNote does { ran += "edit" },
            Shortcuts.Reveal does { ran += "reveal" },
        )
        assertTrue(bindings.dispatch(typed(' ')))
        assertTrue(bindings.dispatch(typed('\n')))
        assertTrue(bindings.dispatch(typed('e')))
        // Something that is not bound is left for whoever handles it next.
        assertFalse(bindings.dispatch(typed('x')))
        assertFalse(bindings.dispatch(down(Key.E, 'e')))
        assertEquals(listOf("reveal", "reveal", "edit"), ran)
    }

    @Test
    fun aShortcutIsAKeyOrACharacter() {
        assertTrue(runCatching { Shortcut(key = Key.A, char = 'a') }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { Shortcut(char = 'a', primary = true) }.exceptionOrNull() is IllegalArgumentException)
    }
}
