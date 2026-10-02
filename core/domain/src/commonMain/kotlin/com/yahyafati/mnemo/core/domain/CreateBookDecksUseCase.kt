package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.data.repository.DeckRepository

/** A chapter to make a deck for: [chapterId] is `BookChapter.id`, the chapter's position in the book (0-based). */
data class ChapterDeckRequest(val chapterId: Int, val title: String)

/** The decks of a book: the root, and each requested chapter's deck by `chapterId`, new or already there. */
data class BookDecks(
    val rootDeckId: String,
    val rootCreated: Boolean,
    val chapterDeckIds: Map<Int, String>,
    /** The chapters whose deck did not exist before this call. */
    val createdChapterIds: Set<Int>,
)

/**
 * Makes the decks of a book (docs/epub/ROADMAP.md, B3): a root deck named after the book and one empty deck per
 * chapter under it, named by [BookDeckNames]. Cards come later, through Smart Extract.
 *
 * Decks that exist already (same path, ignoring case) are reused as they are, not saved again: `saveDeck` on an
 * existing path would overwrite the deck's description and category with the empty ones. That makes the call
 * safe to repeat: a re-import, or a retry after a failure half-way, creates only what is missing. Nothing is
 * rolled back when it fails.
 */
class CreateBookDecksUseCase(
    private val deckRepository: DeckRepository,
) {
    /**
     * [chapterCount] is the number of chapters of the whole book (for the numbers' width); it defaults to what
     * the highest requested chapter implies. [chapters] must not be empty.
     */
    suspend operator fun invoke(
        bookName: String,
        chapters: List<ChapterDeckRequest>,
        chapterCount: Int = 0,
    ): BookDecks {
        val requests = chapters.distinctBy { it.chapterId }.sortedBy { it.chapterId }
        require(requests.isNotEmpty()) { "No chapters to make decks for" }
        require(requests.first().chapterId >= 0) { "A chapter's id can't be negative" }
        val count = maxOf(chapterCount, requests.last().chapterId + 1)

        val live = deckRepository.getDecks()
        val existingRoot = live.firstOrNull { it.parentId == null && it.name.equals(BookDeckNames.book(bookName), ignoreCase = true) }
        val rootName = existingRoot?.name ?: BookDeckNames.book(bookName)
        val rootId = existingRoot?.id ?: deckRepository.saveDeck(rootName)
        val children = if (existingRoot == null) emptyList() else live.filter { it.parentId == rootId }

        val ids = LinkedHashMap<Int, String>()
        val created = mutableSetOf<Int>()
        for (request in requests) {
            val name = BookDeckNames.chapter(request.chapterId + 1, count, request.title)
            val existing = children.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ids[request.chapterId] = existing?.id
                ?: deckRepository.saveDeck(BookDeckNames.path(rootName, name)).also { created += request.chapterId }
        }
        return BookDecks(rootId, rootCreated = existingRoot == null, chapterDeckIds = ids, createdChapterIds = created)
    }
}
