package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.model.Deck
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CreateBookDecksUseCaseTest {
    private val decks = FakeDeckRepository()
    private val create = CreateBookDecksUseCase(decks)

    private fun chapters(vararg titles: String) = titles.mapIndexed { i, title -> ChapterDeckRequest(i, title) }

    /** Every live deck as "Parent::Child", in the order the deck list shows them. */
    private suspend fun paths(): List<String> {
        val all = decks.observeDecks().first()
        val byId = all.associateBy { it.id }
        return all.map { deck ->
            generateSequence(deck) { d -> d.parentId?.let(byId::get) }.map { it.name }.toList().asReversed()
                .joinToString(Deck.PATH_SEPARATOR)
        }.sorted()
    }

    @Test
    fun makesTheRootAndOneNumberedDeckPerChapter() = runTest {
        val result = create("Moby-Dick", chapters("Loomings", "The Carpet-Bag", "The Spouter-Inn"))

        assertEquals(listOf("Moby-Dick", "Moby-Dick::01 Loomings", "Moby-Dick::02 The Carpet-Bag", "Moby-Dick::03 The Spouter-Inn"), paths())
        assertTrue(result.rootCreated)
        assertEquals(setOf(0, 1, 2), result.createdChapterIds)
        assertEquals(listOf(0, 1, 2), result.chapterDeckIds.keys.toList())
        assertEquals("01 Loomings", decks.getDeck(result.chapterDeckIds.getValue(0))?.name)
        assertEquals(result.rootDeckId, decks.getDeck(result.chapterDeckIds.getValue(2))?.parentId)
    }

    @Test
    fun theDeckListKeepsTheBooksOrder() = runTest {
        val titles = (1..12).map { "Chapter title $it".reversed() } // names whose own order is not the book's
        create("Book", chapters(*titles.toTypedArray()))

        val names = decks.observeDecks().first().filter { it.parentId != null }.map { it.name }
        assertEquals(names.sorted(), names)
        assertEquals(titles.mapIndexed { i, t -> "${(i + 1).toString().padStart(2, '0')} $t" }, names)
    }

    @Test
    fun numbersAreZeroPaddedToTheWidthOfTheWholeBook() = runTest {
        assertEquals("09 A", BookDeckNames.chapter(9, 9, "A"))
        assertEquals("010 A", BookDeckNames.chapter(10, 100, "A"))
        assertEquals("100 A", BookDeckNames.chapter(100, 100, "A"))
        assertEquals("1000 A", BookDeckNames.chapter(1000, 1000, "A"))

        // Only the last chapter of a 120-chapter book: it pads to three digits like the rest would.
        val result = create("Big", listOf(ChapterDeckRequest(119, "End")), chapterCount = 120)
        assertEquals("120 End", decks.getDeck(result.chapterDeckIds.getValue(119))?.name)
    }

    @Test
    fun aChapterIsNumberedByItsPositionInTheBookNotAmongTheCheckedOnes() = runTest {
        val result = create("Book", listOf(ChapterDeckRequest(2, "Third"), ChapterDeckRequest(6, "Seventh")), chapterCount = 8)

        assertEquals(listOf("Book", "Book::03 Third", "Book::07 Seventh"), paths())
        assertEquals(setOf(2, 6), result.chapterDeckIds.keys)
    }

    @Test
    fun laterImportSlotsIntoTheSameTree() = runTest {
        val first = create("Book", listOf(ChapterDeckRequest(1, "Second")), chapterCount = 3)
        val second = create("Book", listOf(ChapterDeckRequest(0, "First"), ChapterDeckRequest(1, "Second")), chapterCount = 3)

        assertEquals(listOf("Book", "Book::01 First", "Book::02 Second"), paths())
        assertEquals(first.rootDeckId, second.rootDeckId)
        assertEquals(first.chapterDeckIds.getValue(1), second.chapterDeckIds.getValue(1))
        assertEquals(setOf(0), second.createdChapterIds)
        assertFalse(second.rootCreated)
    }

    @Test
    fun chaptersWithTheSameTitleStayApart() = runTest {
        val result = create("Poems", chapters("Untitled", "Untitled", "UNTITLED"))

        assertEquals(listOf("Poems", "Poems::01 Untitled", "Poems::02 Untitled", "Poems::03 UNTITLED"), paths())
        assertEquals(3, result.chapterDeckIds.values.toSet().size)
    }

    @Test
    fun theDeckSeparatorInATitleDoesNotNestDecks() = runTest {
        val result = create("Rules::Of Play", chapters("Setup::Board", "End: the game", "A::::B", "::Edges::"))

        assertEquals(
            listOf(
                "Rules – Of Play",
                "Rules – Of Play::01 Setup – Board",
                "Rules – Of Play::02 End: the game",
                "Rules – Of Play::03 A – B",
                "Rules – Of Play::04 Edges",
            ),
            paths(),
        )
        assertEquals(4, result.chapterDeckIds.size)
    }

    @Test
    fun aColonAtTheEndOfABookNameDoesNotBreakThePath() = runTest {
        create("Notes:", chapters("One"))

        assertEquals(listOf("Notes", "Notes::01 One"), paths())
    }

    @Test
    fun namesAreCleanedAndEmptyOnesFilledIn() {
        assertEquals("A B C", BookDeckNames.book("  A \t B\n\nC  "))
        assertEquals("AB", BookDeckNames.book("A\u0000\u0007B"))
        assertEquals("A – B", BookDeckNames.book("A:\u0000:B"))
        assertEquals(BookDeckNames.DEFAULT_BOOK_NAME, BookDeckNames.book(" \n "))
        assertEquals("01 Chapter 1", BookDeckNames.chapter(1, 5, "   "))
        assertEquals("07 Chapter 7", BookDeckNames.chapter(7, 20, "::"))
    }

    @Test
    fun longNamesAreCutAtAWordBoundary() {
        val words = List(40) { "word$it" }.joinToString(" ")

        val book = BookDeckNames.book(words)
        assertTrue(book.length <= BookDeckNames.MAX_BOOK_NAME, book)
        assertTrue(book.endsWith("…"))
        assertTrue(words.startsWith(book.removeSuffix("…")))
        assertTrue(words.substring(book.length - 1).first() == ' ', "cut inside a word: $book")

        val chapter = BookDeckNames.chapter(3, 9, words)
        assertTrue(chapter.startsWith("03 "))
        assertTrue(chapter.length <= "03 ".length + BookDeckNames.MAX_CHAPTER_TITLE)

        // No spaces at all: cut where it is, never over the limit.
        assertEquals(BookDeckNames.MAX_BOOK_NAME, BookDeckNames.book("x".repeat(500)).length)
        assertEquals("short", BookDeckNames.book("short"))
        assertEquals(60, BookDeckNames.book("y".repeat(60)).length)
    }

    @Test
    fun aCutNeverSplitsASurrogatePair() {
        val name = BookDeckNames.book("😀".repeat(100))

        assertTrue(name.length <= BookDeckNames.MAX_BOOK_NAME)
        assertTrue(name.removeSuffix("…").all { it.isHighSurrogate() || it.isLowSurrogate() })
        assertEquals(0, name.removeSuffix("…").length % 2)
        assertFalse(name.removeSuffix("…").last().isHighSurrogate())
    }

    @Test
    fun runningItAgainCreatesNothingNew() = runTest {
        val first = create("Book", chapters("One", "Two"))
        val before = decks.getDecks()

        val again = create("Book", chapters("One", "Two"))

        assertEquals(first.rootDeckId, again.rootDeckId)
        assertEquals(first.chapterDeckIds, again.chapterDeckIds)
        assertTrue(again.createdChapterIds.isEmpty())
        assertFalse(again.rootCreated)
        assertEquals(before, decks.getDecks())
    }

    @Test
    fun anExistingDeckKeepsItsDescriptionAndCategory() = runTest {
        val result = create("Book", chapters("One"))
        val chapterId = result.chapterDeckIds.getValue(0)
        decks.saveDeck("Book", description = "My notes on the book", category = "Reading", id = result.rootDeckId)
        decks.saveDeck("Book::01 One", description = "Read twice", category = "Hard", id = chapterId)

        create("Book", chapters("One"))

        assertEquals("My notes on the book", decks.getDeck(result.rootDeckId)?.description)
        assertEquals("Reading", decks.getDeck(result.rootDeckId)?.category)
        assertEquals("Read twice", decks.getDeck(chapterId)?.description)
        assertEquals("Hard", decks.getDeck(chapterId)?.category)
    }

    @Test
    fun anExistingDeckIsFoundIgnoringCase() = runTest {
        val root = decks.saveDeck("history of rome")

        val result = create("History of Rome", chapters("The Kings"))

        assertEquals(root, result.rootDeckId)
        assertEquals(listOf("history of rome", "history of rome::01 The Kings"), paths())

        val again = create("HISTORY OF ROME", chapters("the kings"))
        assertTrue(again.createdChapterIds.isEmpty())
        assertEquals(result.chapterDeckIds, again.chapterDeckIds)
    }

    @Test
    fun aChapterDeckOfTheSameNameElsewhereIsNotReused() = runTest {
        decks.saveDeck("Other::01 One")

        val result = create("Book", chapters("One"))

        assertEquals(setOf(0), result.createdChapterIds)
        assertEquals(listOf("Book", "Book::01 One", "Other", "Other::01 One"), paths())
    }

    @Test
    fun aRetryAfterAFailureHalfwayCreatesTheRest() = runTest {
        val flaky = FlakyDecks(decks, failOnSave = 3) // the root and one chapter get saved, then it fails
        val failing = CreateBookDecksUseCase(flaky)

        assertFailsWith<IOException> { failing("Book", chapters("One", "Two", "Three")) }
        assertEquals(listOf("Book", "Book::01 One"), paths())

        val result = create("Book", chapters("One", "Two", "Three"))

        assertEquals(listOf("Book", "Book::01 One", "Book::02 Two", "Book::03 Three"), paths())
        assertEquals(setOf(1, 2), result.createdChapterIds)
        assertFalse(result.rootCreated)
        assertEquals(1, decks.getDecks().count { it.name == "01 One" })
    }

    @Test
    fun duplicateRequestsForOneChapterMakeOneDeck() = runTest {
        val result = create("Book", listOf(ChapterDeckRequest(1, "Two"), ChapterDeckRequest(1, "Two again")))

        assertEquals(listOf("Book", "Book::02 Two"), paths())
        assertEquals(setOf(1), result.chapterDeckIds.keys)
    }

    @Test
    fun noChaptersIsAnError() = runTest {
        assertFailsWith<IllegalArgumentException> { create("Book", emptyList()) }
        assertTrue(decks.getDecks().isEmpty(), "an empty root deck was left behind")
    }

    @Test
    fun theDecksStartEmptyWithNoDescription() = runTest {
        create("Book", chapters("One"))

        assertTrue(decks.getDecks().all { it.description.isEmpty() && it.category == null && it.examDate == null })
    }

    /** A [DeckRepository] whose [failOnSave]th `saveDeck` throws. */
    private class FlakyDecks(private val delegate: DeckRepository, private val failOnSave: Int) : DeckRepository by delegate {
        private var saves = 0

        override suspend fun saveDeck(
            path: String,
            description: String,
            category: String?,
            id: String?,
            examDate: java.time.LocalDate?,
        ): String {
            if (++saves == failOnSave) throw IOException("disk full")
            return delegate.saveDeck(path, description, category, id, examDate)
        }
    }
}
