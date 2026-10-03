package com.yahyafati.mnemo.core.ui.keyboard

import androidx.compose.ui.input.key.Key

/**
 * Every keyboard shortcut of the app, in one place so the handlers, the menu bar and the shortcuts
 * sheet (`?`) can't disagree (desktop ROADMAP D7).
 *
 * Plain characters (Space, Enter, the digits, E, `?`) are matched as typed characters, which a focused text
 * field consumes, so typing in a field never triggers them (see [Shortcut]). Shortcuts with the command key
 * are for actions that a text field has no use for; Esc leaves a page, also from a field.
 */
object Shortcuts {
    // Anywhere
    val NewNote = Shortcut(Key.N, primary = true)
    val Search = Shortcut(Key.F, primary = true)
    val Settings = Shortcut(Key.Comma, primary = true)
    val Import = Shortcut(Key.O, primary = true)
    val ShowShortcuts = Shortcut.Question

    /** Runs one sync round now (or opens Settings › Sync while sync is off). */
    val SyncNow = Shortcut(Key.S, primary = true, shift = true)

    /** Leaves a full-screen page (the editor, Settings, a study session) for the one before it. */
    val Back = Shortcut(Key.Escape)

    /** ⌘1–⌘4 / Ctrl+1–4: Decks, Study, Create, Analytics, in the order of the tabs. */
    val Tabs = listOf(Key.One, Key.Two, Key.Three, Key.Four).map { Shortcut(it, primary = true) }

    // Studying
    val Reveal = listOf(Shortcut(char = ' '), Shortcut(char = '\n'))

    /** 1–4: Again, Hard, Good, Easy (and, before the answer shows, the options of a multiple choice card). */
    val Answers = listOf('1', '2', '3', '4').map { Shortcut(char = it) }
    val Undo = Shortcut(Key.Z, primary = true)
    val EditNote = Shortcut(char = 'e')

    // Editing
    val Save = Shortcut(Key.Enter, primary = true)
    val Cloze = Shortcut(Key.C, primary = true, shift = true)
}
