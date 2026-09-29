package com.yahyafati.mnemo.feature.create

import androidx.compose.ui.text.input.TextFieldValue
import com.github.takahirom.roborazzi.captureRoboImage
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.DuplicateGroup
import com.yahyafati.mnemo.core.model.GeneratedCard
import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteType
import com.yahyafati.mnemo.feature.create.coauthor.CoAuthorMessage
import com.yahyafati.mnemo.feature.create.coauthor.CoAuthorScreen
import com.yahyafati.mnemo.feature.create.coauthor.CoAuthorUiState
import com.yahyafati.mnemo.feature.create.coauthor.SuggestedCard
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * Screenshots of Create (docs/design/ai-card-creator.html): the manual editor with the new card
 * types, and AI Co-Author. Record: ./gradlew :feature:create:recordRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1500dp-xxhdpi")
class CreateScreenshotTest {
    @Test
    fun editorMultipleChoice() = captureRoboImage("src/test/screenshots/create_editor_choice.png") {
        MnemoTheme {
            NoteEditorScreen(
                uiState = NoteEditorUiState(
                    isLoading = false,
                    decks = listOf(DeckOption("d", "Neuroscience")),
                    deckId = "d",
                    kind = NoteKind.MultipleChoice,
                    front = TextFieldValue("Which structure drives fear conditioning?"),
                    back = TextFieldValue("Amygdala"),
                    wrong = TextFieldValue("Hippocampus\nCerebellum\nThalamus"),
                    hint = "Almond-shaped",
                    tags = listOf("limbic-system"),
                ),
                onAction = {},
            )
        }
    }

    @Test
    fun coAuthorLight() = captureRoboImage("src/test/screenshots/create_coauthor_light.png") { CoAuthor(dark = false) }

    @Test
    fun coAuthorDark() = captureRoboImage("src/test/screenshots/create_coauthor_dark.png") { CoAuthor(dark = true) }

    @androidx.compose.runtime.Composable
    private fun CoAuthor(dark: Boolean) {
        val now = Instant.EPOCH
        fun note(id: String, front: String) = Note(id, "d", NoteType.Basic.id, listOf(front, "ATP"), createdAt = now, updatedAt = now)
        MnemoTheme(darkTheme = dark) {
            CoAuthorScreen(
                uiState = CoAuthorUiState(
                    isLoading = false,
                    decks = listOf(DeckOption("d", "Biology::Cells")),
                    deckId = "d",
                    messages = listOf(
                        CoAuthorMessage.User("1", "What topics am I missing?"),
                        CoAuthorMessage.Reply("2", "You have nothing on **enzymes** or the **cell cycle** yet.", done = true),
                        CoAuthorMessage.Suggestions(
                            "3", "enzymes",
                            listOf(
                                SuggestedCard(GeneratedCard("g1", NoteKind.Basic, "What do enzymes lower?", "Activation energy")),
                                SuggestedCard(GeneratedCard("g2", NoteKind.Cloze, "Enzymes bind substrates at the {{c1::active site}}.", "")),
                            ),
                            done = true,
                        ),
                        CoAuthorMessage.Duplicates(
                            "4",
                            listOf(DuplicateGroup(listOf(note("a", "What do mitochondria make?"), note("b", "What do **mitochondria** make?")), 1.0)),
                        ),
                    ),
                ),
                onAction = {},
                onSetUpAi = {},
            )
        }
    }
}
