package com.yahyafati.mnemo.feature.create

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AiTask
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
    fun linkSectionsLight() = captureRoboImage("src/androidHostTest/screenshots/create_link_sections_light.png") { LinkSections(dark = false) }

    @Test
    fun linkSectionsDark() = captureRoboImage("src/androidHostTest/screenshots/create_link_sections_dark.png") { LinkSections(dark = true) }

    @Test
    fun batchRunLight() = captureRoboImage("src/androidHostTest/screenshots/create_batch_run_light.png") { BatchRun(dark = false) }

    @Test
    fun batchRunDark() = captureRoboImage("src/androidHostTest/screenshots/create_batch_run_dark.png") { BatchRun(dark = true) }

    @Test
    fun pdfPagesLight() = captureRoboImage("src/androidHostTest/screenshots/create_pdf_pages_light.png") { PdfPages(dark = false, error = null) }

    @Test
    fun pdfPagesDark() = captureRoboImage("src/androidHostTest/screenshots/create_pdf_pages_dark.png") {
        PdfPages(dark = true, error = PdfPagesError.TooMany(selected = 400, limit = 300))
    }

    @Test
    fun pdfChaptersLight() = captureRoboImage("src/androidHostTest/screenshots/create_pdf_chapters_light.png") { PdfChapters(dark = false) }

    @Test
    fun pdfChaptersDark() = captureRoboImage("src/androidHostTest/screenshots/create_pdf_chapters_dark.png") { PdfChapters(dark = true) }

    @Test
    fun bookImportDrm() = captureRoboImage("src/androidHostTest/screenshots/create_book_import_drm.png") {
        MnemoTheme { BookImportScreen(BookImportUiState(problem = SourceProblem.Drm), onAction = {}, onClose = {}) }
    }

    /** Smart Extract with a 612-page PDF open: the Pages field (docs/pdf/ROADMAP.md, P1), valid or with an error under it. */
    @androidx.compose.runtime.Composable
    private fun PdfPages(dark: Boolean, error: PdfPagesError?) {
        val provider = AiProvider(id = "p", name = "Groq", baseUrl = "https://api.groq.com/openai/v1", defaultModel = "llama-3.3-70b", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val text = "Mitochondria make most of the cell's ATP by oxidative phosphorylation."
        MnemoTheme(darkTheme = dark) {
            SmartExtractScreen(
                uiState = SmartExtractUiState(
                    isLoading = false,
                    route = AiRoute(AiTask.Extract, provider, "llama-3.3-70b", AiCapabilities(), usesDefault = true),
                    decks = listOf(DeckOption("d", "Cell biology")),
                    deckId = "d",
                    sourceKind = SourceKind.Pdf,
                    text = text,
                    title = "Cell biology",
                    pdf = PdfSummary(
                        title = "Cell biology",
                        pageCount = 612,
                        pages = if (error == null) "1-300" else "1-400",
                        error = error,
                        printedPages = "i–xii, 1–600",
                        chapters = pdfChapters(),
                    ),
                ),
                onAction = {},
                onSetUpAi = {},
            )
        }
    }

    private fun pdfChapters() = listOf(
        PdfChapterOption(0, "Preface", 1, 13, "ii", 1),
        PdfChapterOption(1, "Part I: Foundations", 1, 14, "1", 140),
        PdfChapterOption(2, "Chapter 1: Cells", 2, 14, "1", 62),
        PdfChapterOption(3, "Chapter 2: Tissues", 2, 76, "63", 78),
        PdfChapterOption(4, "Part II: Systems", 1, 154, "141", 460),
    )

    /** The list inside Smart Extract's chapter dialog for a textbook: parts, chapters under them, two ticked. */
    @androidx.compose.runtime.Composable
    private fun PdfChapters(dark: Boolean) {
        MnemoTheme(darkTheme = dark) {
            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh) {
                androidx.compose.foundation.layout.Column(Modifier.padding(24.dp)) {
                    androidx.compose.material3.Text("Chapters", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                    PdfChapterList(pdfChapters(), selected = setOf(2, 3), onToggle = {})
                }
            }
        }
    }

    /** Smart Extract in the middle of a book run: chapter 3 of 8 is done and waits for its review. */
    @androidx.compose.runtime.Composable
    private fun BatchRun(dark: Boolean) {
        val provider = AiProvider(id = "p", name = "Groq", baseUrl = "https://api.groq.com/openai/v1", defaultModel = "llama-3.3-70b", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val text = "Natural selection acts on the variation that already exists in every population."
        val chapters = listOf("Variation under domestication", "Variation under nature", "Struggle for existence", "Natural selection", "Laws of variation", "Difficulties", "Instinct", "Hybridism")
            .mapIndexed { index, title -> ChapterOption(index, title, 4000, ChapterKind.Content, false) }
        MnemoTheme(darkTheme = dark) {
            SmartExtractScreen(
                uiState = SmartExtractUiState(
                    isLoading = false,
                    route = AiRoute(AiTask.Extract, provider, "llama-3.3-70b", AiCapabilities(), usesDefault = true),
                    decks = listOf(DeckOption("d", "Origin::03 Struggle for existence")),
                    deckId = "d",
                    text = text,
                    title = "Origin — Struggle for existence",
                    batch = BookBatch(chapters, position = 2),
                    generation = GenerationState.Done(2),
                    queue = listOf(
                        QueueItem(GeneratedCard("1", NoteKind.Basic, "What does natural selection act on?", "The variation that already exists in a population.", listOf("selection")), text),
                        QueueItem(GeneratedCard("2", NoteKind.Cloze, "Natural selection acts on {{c1::existing variation}}.", ""), text),
                    ),
                ),
                onAction = {},
                onSetUpAi = {},
            )
        }
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

    /** The list inside Smart Extract's section dialog, for an article: the lead, sections and a subsection. */
    @androidx.compose.runtime.Composable
    private fun LinkSections(dark: Boolean) {
        val sections = SectionsSummary(
            options = listOf(
                SectionOption(0, null, 0, 180),
                SectionOption(1, "Structure", 2, 640),
                SectionOption(2, "Hemispheric specializations", 3, 310),
                SectionOption(3, "Function", 2, 1250),
                SectionOption(4, "Clinical significance", 2, 980),
            ),
            selected = setOf(0, 1, 2, 3),
        )
        MnemoTheme(darkTheme = dark) {
            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh) {
                androidx.compose.foundation.layout.Column(Modifier.padding(24.dp)) {
                    androidx.compose.material3.Text("Sections of Amygdala", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                    SectionOptionList(sections, onToggle = {})
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
