package com.yahyafati.mnemo.desktop

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.data.desktop.DesktopAppDirectories
import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.data.repository.DataTransferRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.data.repository.ReviewRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.TransferState
import com.yahyafati.mnemo.core.security.SecretCipher
import com.yahyafati.mnemo.core.testing.security.SoftwareSecretCipher
import com.yahyafati.mnemo.core.ui.card.desktop.DesktopCardAudio
import com.yahyafati.mnemo.shell.AppCommand
import com.yahyafati.mnemo.shell.AppCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.dsl.module
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The shared app on the desktop (ROADMAP D6): the real desktop graph on a temporary data
 * directory, driven through the window's content. The Android counterparts are the app tests in
 * `:app` (`FirstSessionTest`, `MnemoAppNavigationTest`).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class, InternalComposeUiApi::class)
class DesktopAppTest {
    private val root: File = Files.createTempDirectory("mnemo-desktop-app").toFile()
    private val directories = DesktopAppDirectories(File(root, "data"), File(root, "cache"))
    private var session: CollectionSession? = null

    /**
     * Navigation and lifecycles insist on the main thread, which the real window's is the AWT event
     * thread; a Compose test runs on its own thread, so Main is the test's.
     */
    @BeforeTest
    fun useTestMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun close() {
        Dispatchers.resetMain()
        session?.close()
        root.deleteRecursively()
    }

    private fun open(): CollectionSession {
        val testModule = module {
            single<AppDirectories> { directories }
            // The real one would use this machine's keychain.
            single<SecretCipher> { SoftwareSecretCipher() }
        }
        return assertNotNull(openCollection(directories, desktopModules + testModule)).also { session = it }
    }

    private fun ComposeUiTest.await(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.showApp(session: CollectionSession, commands: AppCommands = AppCommands()) {
        setContent { DesktopApp(mediaDirectory = session.koin.get<AppDirectories>().media, commands = commands) }
    }

    /** The key that does what Ctrl does elsewhere: ⌘ on macOS, where these tests run on a Mac too. */
    private val primaryKey = if (System.getProperty("os.name").startsWith("Mac")) Key.MetaLeft else Key.CtrlLeft

    /**
     * Presses [key] with the window's root as the target, as a keyboard does. A key that types a character
     * ([typing]) also delivers it, as the window does after the key going down; a text field would consume it.
     */
    private fun ComposeUiTest.press(key: Key, primary: Boolean = false, shift: Boolean = false, typing: Char? = null) {
        // A screen takes the keyboard focus in an effect; a person is slower than that, a test is not.
        waitForIdle()
        val root = onAllNodes(isRoot()).onFirst()
        root.performKeyInput {
            if (primary) keyDown(primaryKey)
            if (shift) keyDown(Key.ShiftLeft)
            pressKey(key)
            if (shift) keyUp(Key.ShiftLeft)
            if (primary) keyUp(primaryKey)
        }
        if (typing != null) root.performKeyPress(typedCharacter(typing))
    }

    private fun typedCharacter(char: Char) = KeyEvent(key = Key.Unknown, type = KeyEventType.Unknown, codePoint = char.code)

    private fun SemanticsNodeInteractionsProvider.field(label: String) = onNode(hasSetTextAction() and hasText(label))

    @Test
    fun firstSessionOnboardingCreateDeckStudyAndStats() = runComposeUiTest {
        val session = open()
        runBlocking { session.koin.get<UserSettingsRepository>().setOnboardingCompleted(false) }
        showApp(session)

        // Onboarding: two pages of introduction, then the first deck. No reminder row: the desktop
        // has no reminder notification.
        await("Remember more, study less")
        onNodeWithText("Next").performClick()
        await("How studying works")
        onNodeWithText("Next").performClick()
        await("Start your first deck")
        onNodeWithText("Remind me daily at", substring = true).assertDoesNotExist()
        field("Deck name").performTextInput("Biology")
        onNodeWithText("Create deck and add cards").performClick()

        // The editor opens on the new deck.
        await("Write a card")
        field("Front").performTextInput("What do mitochondria make?")
        field("Back").performTextInput("ATP")
        onNodeWithText("Add card").performClick()
        await("Card added")
        onNodeWithContentDescription("Close").performClick()

        // Study it, with undo.
        await("1 new")
        onNodeWithText("Review").performClick()
        await("What do mitochondria make?")
        onNodeWithText("Show answer").performClick()
        await("ATP")
        onNodeWithText("Easy").performClick()
        await("Session complete")
        onNodeWithText("Done").performClick()
        await("Biology")

        // Stats: the review is counted.
        onNodeWithText("Analytics").performClick()
        await("Reviews, last 30 days")
        assertEquals(1, runBlocking { session.koin.get<ReviewRepository>().observeTodayCounts().first().total })
        assertEquals(true, runBlocking { session.koin.get<UserSettingsRepository>().settings.first().onboardingCompleted })
    }

    @Test
    fun everyTabAndSettingsAreReachable() = runComposeUiTest {
        val session = open()
        runBlocking { session.koin.get<UserSettingsRepository>().setOnboardingCompleted(true) }
        showApp(session)

        await("No decks yet")
        val tabs = listOf(
            "Study" to "Nothing to study",
            "Create" to "Write a card",
            "Analytics" to "No reviews yet",
            "Decks" to "No decks yet",
        )
        for ((tab, content) in tabs) {
            onNodeWithText(tab).performClick()
            await(content)
        }
        onNodeWithContentDescription("Settings").performClick()
        await("Scheduling")
        // No reminder section, no dynamic color: what the desktop doesn't have is not offered.
        onNodeWithText("Daily reminder").assertDoesNotExist()
        onNodeWithText("Dynamic color").assertDoesNotExist()
        onNodeWithContentDescription("Back").performClick()
        await("No decks yet")
    }

    @Test
    fun licensesListComesFromTheDesktopBuild() = runComposeUiTest {
        val session = open()
        runBlocking { session.koin.get<UserSettingsRepository>().setOnboardingCompleted(true) }
        showApp(session)

        await("No decks yet")
        onNodeWithContentDescription("Settings").performClick()
        await("About")
        onNodeWithText("Open-source licenses").performScrollTo().performClick()
        await("Mnemo is licensed under GPL-3.0-or-later")
        // The generated list is on screen (the rows are lazy, so only the first ones are composed).
        await("AboutLibraries Core Library")
        // What the desktop bundles, from `desktop/config`: the Java runtime, not Android's KaTeX.
        val json = AppInfo.loadLicenses()
        assertTrue("OpenJDK runtime" in json && "py-fsrs" in json && "Newsreader" in json)
        assertFalse("KaTeX" in json)
    }

    @Test
    fun studyLoopRatesAndUndoes() = runComposeUiTest {
        val session = open()
        runBlocking {
            val koin = session.koin
            koin.get<UserSettingsRepository>().setOnboardingCompleted(true)
            val deck = koin.get<DeckRepository>().saveDeck("Biology")
            val cards = koin.get<CardRepository>()
            cards.addNote(deck, NoteKind.Basic, listOf("What do mitochondria make?", "ATP"), emptyList())
            cards.addNote(deck, NoteKind.Basic, listOf("Largest organ?", "Skin"), emptyList())
        }
        showApp(session)

        await("Biology")
        onNodeWithText("Study").performClick()
        await("Card 1 of 2")
        onNodeWithText("Show answer").performClick()
        onNodeWithText("Good").performClick()
        // Good on a new card only moves it to a learning step: the session goes on with the next.
        await("Card 2 of", substring = true)

        // Undo takes the answer back, and the card is asked again.
        onNodeWithContentDescription("Undo").performClick()
        await("Card 1 of 2")
        assertEquals(0, runBlocking { session.koin.get<ReviewRepository>().observeTodayCounts().first().total })

        onNodeWithText("Show answer").performClick()
        onNodeWithText("Easy").performClick()
        await("Card 2 of", substring = true)
        assertEquals(1, runBlocking { session.koin.get<ReviewRepository>().observeTodayCounts().first().total })
    }

    @Test
    fun aSessionWithTheKeyboardOnly() = runComposeUiTest {
        val session = open()
        runBlocking {
            val koin = session.koin
            koin.get<UserSettingsRepository>().setOnboardingCompleted(true)
            val deck = koin.get<DeckRepository>().saveDeck("Biology")
            val cards = koin.get<CardRepository>()
            cards.addNote(deck, NoteKind.Basic, listOf("What do mitochondria make?", "ATP"), emptyList())
            cards.addNote(deck, NoteKind.Basic, listOf("Largest organ?", "Skin"), emptyList())
            cards.addNote(deck, NoteKind.Basic, listOf("Powerhouse?", "Mitochondria"), emptyList())
        }
        showApp(session)

        await("Biology")
        press(Key.Two, primary = true)
        await("Card 1 of 3")
        // Space (or Enter) shows the answer, 4 rates it Easy; the next card is up. (New cards come in any
        // order, so the test waits for the buttons, not for a particular answer.)
        press(Key.Spacebar, typing = ' ')
        await("Easy")
        press(Key.Four, typing = '4')
        await("Card 2 of 3")
        press(Key.Enter, typing = '\n')
        await("Easy")
        press(Key.Four, typing = '4')
        await("Card 3 of 3")
        press(Key.Spacebar, typing = ' ')
        await("Easy")
        press(Key.Four, typing = '4')
        await("Session complete")
        assertEquals(3, runBlocking { session.koin.get<ReviewRepository>().observeTodayCounts().first().total })

        // Undo with the keyboard takes the last answer back.
        press(Key.Z, primary = true)
        await("Card 3 of 3")
        assertEquals(2, runBlocking { session.koin.get<ReviewRepository>().observeTodayCounts().first().total })
    }

    @Test
    fun shortcutsWorkFromEveryPage() = runComposeUiTest {
        val session = open()
        runBlocking { session.koin.get<UserSettingsRepository>().setOnboardingCompleted(true) }
        showApp(session)

        await("No decks yet")
        press(Key.Four, primary = true)
        await("No reviews yet")
        press(Key.Two, primary = true)
        await("Nothing to study")
        press(Key.One, primary = true)
        await("No decks yet")

        // Settings opens on the comma, and Escape goes back, from the page itself.
        press(Key.Comma, primary = true)
        await("Scheduling")
        press(Key.Escape)
        await("No decks yet")
        // A tab has nowhere to go back to.
        press(Key.Escape)
        await("No decks yet")

        // ? lists every shortcut.
        press(Key.Slash, shift = true, typing = '?')
        await("Keyboard shortcuts")
        onNodeWithText("Show the answer").assertExists()
        onNodeWithText("Close").performClick()
        onNodeWithText("Keyboard shortcuts").assertDoesNotExist()
    }

    @Test
    fun aBatchOfCardsWithoutTheMouse() = runComposeUiTest {
        val session = open()
        runBlocking {
            session.koin.get<UserSettingsRepository>().setOnboardingCompleted(true)
            session.koin.get<DeckRepository>().saveDeck("Biology")
        }
        showApp(session)

        await("Biology")
        press(Key.N, primary = true)
        await("Front")
        // The cursor starts in the front field; Ctrl+Enter adds the card, and it is back there for the next.
        repeat(3) { index ->
            field("Front").assertIsFocused()
            field("Front").performTextInput("Question $index")
            field("Back").performTextInput("Answer $index")
            press(Key.Enter, primary = true)
            await("Card added")
            field("Front").assertIsFocused()
        }
        press(Key.Escape)
        await("Biology")
        assertEquals(3, runBlocking { session.koin.get<CardRepository>().observeTotalCardCount().first() })
    }

    @Test
    fun typingAnAnswerNeverTriggersAShortcut() = runComposeUiTest {
        val session = open()
        runBlocking {
            session.koin.get<UserSettingsRepository>().setOnboardingCompleted(true)
            val deck = session.koin.get<DeckRepository>().saveDeck("Geography")
            session.koin.get<CardRepository>().addNote(deck, NoteKind.TypeIn, listOf("Capital of Spain?", "Madrid"), emptyList())
        }
        showApp(session)
        await("Geography")
        press(Key.Two, primary = true)
        await("Capital of Spain?")
        // Keys with shortcuts: Space shows the answer, E edits, ? lists shortcuts, 3 would rate. Typed into the
        // answer box they are letters of an answer: the box takes them, and only their keys going down reach its parents.
        val root = onAllNodes(isRoot()).onFirst()
        for ((key, char, shift) in listOf(
            Triple(Key.E, 'e', false), Triple(Key.Spacebar, ' ', false), Triple(Key.Slash, '?', true), Triple(Key.Three, '3', false),
        )) {
            waitForIdle()
            root.performKeyInput {
                if (shift) keyDown(Key.ShiftLeft)
                pressKey(key)
                if (shift) keyUp(Key.ShiftLeft)
            }
            // The focused text field is the one place the typed character goes; it inserts it.
            onNode(hasSetTextAction()).performTextInput(char.toString())
        }
        waitForIdle()
        onNodeWithText("Check answer").assertExists()
        onNodeWithText("Keyboard shortcuts").assertDoesNotExist()
        onNodeWithText("Edit note").assertDoesNotExist()
        onNodeWithText("Card 1 of 1", substring = true).assertExists()
    }

    @Test
    fun aDeckFileOpenedFromTheMenuOrDroppedIsImported() = runComposeUiTest {
        val session = open()
        runBlocking { session.koin.get<UserSettingsRepository>().setOnboardingCompleted(true) }
        val commands = AppCommands()
        showApp(session, commands)
        await("No decks yet")

        // Study is showing, and an Anki package is dropped on the window: it is imported and the Decks tab shows.
        press(Key.Two, primary = true)
        await("Nothing to study")
        commands.send(AppCommand.OpenFile(File("../core/anki/src/test/resources/modern.apkg").absolutePath))
        waitUntil(timeoutMillis = 60_000) { runBlocking { session.koin.get<DeckRepository>().getDecks().isNotEmpty() } }
        await("Import finished", substring = true)

        // Something that is neither a package nor a backup is turned down, with a reason.
        commands.send(AppCommand.OpenFile("/home/me/photo.png"))
        await("Mnemo opens Anki packages", substring = true)
    }

    @Test
    fun aBackupOpenedFromTheMenuAsksBeforeItReplacesEverything() = runComposeUiTest {
        val session = open()
        val backup = File(root, "backup.zip")
        val notABackup = File(root, "other.zip").apply { writeText("not a zip at all") }
        runBlocking {
            session.koin.get<UserSettingsRepository>().setOnboardingCompleted(true)
            session.koin.get<DeckRepository>().saveDeck("Biology")
            val transfers = session.koin.get<DataTransferRepository>()
            transfers.startBackup(backup.absolutePath)
            transfers.backupState.first { it is TransferState.Succeeded }
        }
        val commands = AppCommands()
        showApp(session, commands)
        await("Biology")

        commands.send(AppCommand.OpenFile(backup.absolutePath))
        await("Restore this backup?")
        onNodeWithText("Cancel").performClick()
        onNodeWithText("Restore this backup?").assertDoesNotExist()

        commands.send(AppCommand.RestoreBackup(notABackup.absolutePath))
        await("Can't restore that file")
        onNodeWithText("OK").performClick()
        await("Biology")
    }

    @Test
    fun aBackupStartedFromTheMenuSaysWhenItIsDone() = runComposeUiTest {
        val session = open()
        runBlocking { session.koin.get<UserSettingsRepository>().setOnboardingCompleted(true) }
        val commands = AppCommands()
        showApp(session, commands)
        await("No decks yet")
        val target = File(root, "menu-backup.zip")
        commands.send(AppCommand.BackUpTo(target.absolutePath))
        await("Backup saved")
        assertTrue(target.length() > 0)
    }

    @Test
    fun rightClickingADeckOffersItsActions() = runComposeUiTest {
        val session = open()
        runBlocking {
            session.koin.get<UserSettingsRepository>().setOnboardingCompleted(true)
            val deck = session.koin.get<DeckRepository>().saveDeck("Biology")
            session.koin.get<CardRepository>().addNote(deck, NoteKind.Basic, listOf("Q", "A"), emptyList())
        }
        showApp(session)
        await("Biology")
        onNodeWithText("Biology").performMouseInput { rightClick() }
        // The menu has what the card's overflow button has, and Review first.
        await("Export (.apkg)")
        onNodeWithText("Browse cards").assertExists()
        onNodeWithText("Delete deck").assertExists()
        onNodeWithText("Add cards").assertExists()
    }

    @Test
    fun iconButtonsSayWhatTheyDoWhenThePointerRestsOnThem() = runComposeUiTest {
        val session = open()
        runBlocking { session.koin.get<UserSettingsRepository>().setOnboardingCompleted(true) }
        showApp(session)
        await("No decks yet")
        onNodeWithContentDescription("Settings").performMouseInput { moveTo(center) }
        // The tip is a text of its own, which the button's description is not.
        await("Settings")
    }

    @Test
    fun aClozeIsMadeFromTheKeyboard() = runComposeUiTest {
        val session = open()
        runBlocking {
            session.koin.get<UserSettingsRepository>().setOnboardingCompleted(true)
            session.koin.get<DeckRepository>().saveDeck("Geography")
        }
        showApp(session)
        await("Geography")
        press(Key.N, primary = true)
        await("Front")
        onNodeWithText("Cloze").performClick()
        field("Text").performTextInput("Paris is the capital of France")
        press(Key.C, primary = true, shift = true)
        await("{{c1::", substring = true)
    }

    @Test
    fun aSoundThatCannotPlayIsExplained() {
        val unsupported = audioProblemMessage(DesktopCardAudio.AudioProblem.UnsupportedFormat("[sound:a.m4a]"))
        assertTrue("WAV, MP3 and Ogg" in unsupported)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
