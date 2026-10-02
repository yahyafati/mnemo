package com.yahyafati.mnemo.feature.create

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.BookChapter
import com.yahyafati.mnemo.core.model.BookSource
import com.yahyafati.mnemo.core.model.ChapterKind
import com.yahyafati.mnemo.core.model.DuplicateGroup
import com.yahyafati.mnemo.core.model.GeneratedCard
import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteType
import com.yahyafati.mnemo.core.model.SourceProblem
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
 * types, and AI Co-Author. Record: ./gradlew :feature:create:recordRoborazziAndroidHostTest
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1500dp-xxhdpi")
class CreateScreenshotTest {
    @Test
    fun editorMultipleChoice() = captureRoboImage("src/androidHostTest/screenshots/create_editor_choice.png") {
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
    fun coAuthorLight() = captureRoboImage("src/androidHostTest/screenshots/create_coauthor_light.png") { CoAuthor(dark = false) }

    @Test
    fun coAuthorDark() = captureRoboImage("src/androidHostTest/screenshots/create_coauthor_dark.png") { CoAuthor(dark = true) }

    @Test
    fun bookImportLight() = captureRoboImage("src/androidHostTest/screenshots/create_book_import_light.png") { BookImport(dark = false) }

    @Test
    fun bookImportDark() = captureRoboImage("src/androidHostTest/screenshots/create_book_import_dark.png") { BookImport(dark = true) }

    @Test
    fun epubChaptersLight() = captureRoboImage("src/androidHostTest/screenshots/create_epub_chapters_light.png") { EpubChapters(dark = false) }

    @Test
    fun epubChaptersDark() = captureRoboImage("src/androidHostTest/screenshots/create_epub_chapters_dark.png") { EpubChapters(dark = true) }

    @Test
    fun bookImportDrm() = captureRoboImage("src/androidHostTest/screenshots/create_book_import_drm.png") {
        MnemoTheme { BookImportScreen(BookImportUiState(problem = SourceProblem.Drm), onAction = {}, onClose = {}) }
    }

    /** The list inside Smart Extract's chapter dialog (a dialog is its own window, which the screenshot doesn't take). */
    @androidx.compose.runtime.Composable
    private fun EpubChapters(dark: Boolean) {
        val chapters = listOf(
            ChapterOption(0, "Title page", 3, ChapterKind.FrontMatter, false),
            ChapterOption(1, "Introduction", 3100, ChapterKind.Content, false),
            ChapterOption(2, "Variation under domestication", 5200, ChapterKind.Content, false),
            ChapterOption(3, "Struggle for existence", 4100, ChapterKind.Content, true),
            ChapterOption(4, "Index", 900, ChapterKind.BackMatter, false),
        )
        MnemoTheme(darkTheme = dark) {
            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh) {
                androidx.compose.foundation.layout.Column(Modifier.padding(24.dp)) {
                    androidx.compose.material3.Text(
                        "Chapters of On the Origin of Species",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                    )
                    ChapterOptionList(BookSummary("On the Origin of Species", "Charles Darwin", chapters, false), selectedId = 2, onSelect = {})
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun BookImport(dark: Boolean) {
        fun chapter(id: Int, title: String, words: Int, kind: ChapterKind = ChapterKind.Content) =
            BookChapter(id, title, "word ".repeat(words), kind)
        val book = BookSource(
            title = "On the Origin of Species",
            author = "Charles Darwin",
            chapters = listOf(
                chapter(0, "Title page", 12, ChapterKind.FrontMatter),
                chapter(1, "Variation under domestication", 5200),
                chapter(2, "Variation under nature", 3100),
                chapter(3, "Struggle for existence", 4100),
                chapter(4, "Natural selection", 7300),
                chapter(5, "Glossary and index", 900, ChapterKind.BackMatter),
            ),
        )
        MnemoTheme(darkTheme = dark) {
            BookImportScreen(
                uiState = BookImportUiState(
                    book = book,
                    wordCounts = book.chapters.associate { it.id to it.wordCount },
                    bookName = book.title,
                    checked = setOf(1, 2, 3, 4),
                    existing = setOf(1),
                ),
                onAction = {},
                onClose = {},
            )
        }
    }

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
