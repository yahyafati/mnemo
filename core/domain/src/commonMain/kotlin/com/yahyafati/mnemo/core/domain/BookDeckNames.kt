package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.model.Deck

/**
 * Deck names for a book (docs/epub/ROADMAP.md, B3): `Book::01 Chapter`. [CreateBookDecksUseCase] makes the decks
 * with them, and "generate cards later" (B5) finds a chapter's deck by computing the same name, so both go
 * through here.
 *
 * Names never contain the deck separator, control characters or runs of spaces, are cut at a word boundary,
 * and carry the chapter's zero-padded position: the deck list sorts by name, so the book's order survives, and
 * two chapters with the same title don't merge (a deck is matched by name).
 */
object BookDeckNames {
    const val MAX_BOOK_NAME = 60
    const val MAX_CHAPTER_TITLE = 80

    /** Used when the book's name is empty. Callers show a name of their own first, in the user's language. */
    const val DEFAULT_BOOK_NAME = "Book"

    private const val MIN_NUMBER_WIDTH = 2
    private const val ELLIPSIS = "…"
    private const val SEPARATOR_REPLACEMENT = " – "
    private val SEPARATOR_RUN = Regex(":{2,}")

    /** The book's (root) deck name for what the user typed. */
    fun book(name: String): String = clean(name, MAX_BOOK_NAME).ifEmpty { DEFAULT_BOOK_NAME }

    /**
     * The deck name of a chapter. [position] is 1-based, [chapterCount] the number of chapters of the whole book
     * (not of the ones being created), so a later import of more chapters pads the same way. A title that is
     * empty becomes "Chapter [position]": callers that want it in the user's language fill it in first.
     */
    fun chapter(position: Int, chapterCount: Int, title: String): String {
        require(position in 1..maxOf(chapterCount, 1)) { "Chapter $position is outside the book's $chapterCount chapters" }
        val width = maxOf(MIN_NUMBER_WIDTH, chapterCount.toString().length)
        val label = clean(title, MAX_CHAPTER_TITLE).ifEmpty { "Chapter $position" }
        return "${position.toString().padStart(width, '0')} $label"
    }

    /** The full path of a chapter's deck. [bookName] is used as given: pass [book]'s result. */
    fun path(bookName: String, chapterName: String): String = bookName + Deck.PATH_SEPARATOR + chapterName

    private fun clean(raw: String, max: Int): String {
        // Controls go first: removing one could join two colons. A colon at an end would join the separator
        // ("Notes:" + "::" = ":::") and split the path in the wrong place, so ends are trimmed before the
        // separator inside the name is replaced.
        val plain = raw.filter { it.isWhitespace() || !it.isISOControl() }
            .trim { it == ':' || it.isWhitespace() }
            .replace(SEPARATOR_RUN, SEPARATOR_REPLACEMENT)
        val text = StringBuilder()
        var space = false
        for (ch in plain) {
            if (ch.isWhitespace()) {
                space = true
            } else {
                if (space) text.append(' ')
                space = false
                text.append(ch)
            }
        }
        return cut(text.toString(), max)
    }

    private fun cut(text: String, max: Int): String {
        if (text.length <= max) return text
        var end = max - ELLIPSIS.length
        if (text[end - 1].isHighSurrogate()) end--
        var head = text.substring(0, end)
        val space = head.lastIndexOf(' ')
        if (space > max / 2) head = head.substring(0, space)
        return head.trimEnd() + ELLIPSIS
    }
}
