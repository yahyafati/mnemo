package com.yahyafati.mnemo.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.data.desktop.DesktopAppDirectories
import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.data.repository.ReviewRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.security.SecretCipher
import com.yahyafati.mnemo.core.testing.security.SoftwareSecretCipher
import com.yahyafati.mnemo.core.ui.card.desktop.DesktopCardAudio
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
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
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

    private fun ComposeUiTest.showApp(session: CollectionSession) {
        setContent { DesktopApp(mediaDirectory = session.koin.get<AppDirectories>().media) }
    }

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
    fun aSoundThatCannotPlayIsExplained() {
        val unsupported = audioProblemMessage(DesktopCardAudio.AudioProblem.UnsupportedFormat("[sound:a.m4a]"))
        assertTrue("WAV, MP3 and Ogg" in unsupported)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
