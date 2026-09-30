package com.yahyafati.mnemo.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import com.yahyafati.mnemo.core.ui.keyboard.Shortcut
import com.yahyafati.mnemo.core.ui.keyboard.Shortcuts
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The menu bar shows the same shortcuts the app handles (desktop ROADMAP D7). */
class DesktopMenuTest {
    @Test
    fun theCommandKeyIsCommandOnMacAndControlElsewhere() {
        assertEquals(KeyShortcut(Key.N, meta = true), Shortcuts.NewNote.toKeyShortcut(mac = true))
        assertEquals(KeyShortcut(Key.N, ctrl = true), Shortcuts.NewNote.toKeyShortcut(mac = false))
        assertEquals(KeyShortcut(Key.C, meta = true, shift = true), Shortcuts.Cloze.toKeyShortcut(mac = true))
        assertEquals(KeyShortcut(Key.Escape), Shortcuts.Back.toKeyShortcut(mac = false))
        assertEquals(KeyShortcut(Key.Two, ctrl = true), Shortcuts.Tabs[1].toKeyShortcut(mac = false))
    }

    @Test
    fun aTypedCharacterCannotBeAMenuShortcut() {
        assertNull(Shortcut.Question.toKeyShortcut(mac = true))
    }
}
