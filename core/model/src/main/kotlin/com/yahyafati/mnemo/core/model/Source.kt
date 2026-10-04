package com.yahyafati.mnemo.core.model

/** Where Smart Extract's text comes from. Pasted and dictated text needs no reading. */
sealed interface SourceInput {
    /** A PDF picked through the Storage Access Framework (a `content://` URI). */
    data class Pdf(val uri: String) : SourceInput

    /** A plain text or Markdown file (a file dropped on the desktop window). */
    data class TextFile(val uri: String) : SourceInput

    /** A web page, or a PDF behind a link. */
    data class Link(val url: String) : SourceInput

    /** An EPUB book. It is read into chapters (`SourceRepository.readBook`), not into one text. */
    data class Epub(val uri: String) : SourceInput
}

/** Plain text read from a [SourceInput], ready to be edited and sent. */
data class SourceText(
    val text: String,
    /** The document's title or file name, when known. */
    val title: String? = null,
    /** Some of the source was left out (too many pages or characters). */
    val truncated: Boolean = false,
    /**
     * The parts of [text] a reader can pick from (a Wikipedia article's sections), in text order; empty for a
     * source that has none. Only extractors that know the structure fill it.
     */
    val sections: List<SourceSection> = emptyList(),
    /** The page is a disambiguation page: it lists other articles and says little itself. */
    val disambiguation: Boolean = false,
) {
    val wordCount: Int get() = countWords(text)

    /** What [section] says: its range of [text]. */
    fun textOf(section: SourceSection): String = text.substring(section.start.coerceIn(0, text.length), section.end.coerceIn(0, text.length))

    companion object {
        /** The most text Smart Extract's box holds: what a source or a read of PDF pages adds beyond it is cut. */
        const val MAX_CHARS = 400_000

        /** See [WordCount.count]: Japanese and Chinese have no spaces, so their characters count too. */
        fun countWords(text: String): Int = WordCount.count(text)
    }
}

/**
 * One part of a [SourceText] the user can pick: the range [start] until [end] of its text (the heading and what
 * is above the first subsection). [id] is the section's position, 0-based: a section's own anchor is only stable
 * within one revision of a page, so sections are identified by position and [title].
 * [title] is null for the lead, whose [level] is 0; an `h2` section is 2, a subsection 3 and so on.
 */
data class SourceSection(val id: Int, val title: String?, val level: Int, val start: Int, val end: Int)

/** Why a source couldn't be read. */
enum class SourceProblem {
    /** No text layer: a scanned PDF, an image, or an empty page. */
    NoText,

    /** Password-protected PDF. */
    Encrypted,

    /** The file isn't a readable PDF, or the page isn't HTML, text or PDF. */
    Unsupported,

    /** Larger than Mnemo reads. */
    TooLarge,

    /** Not an http(s) link. */
    InvalidUrl,

    /** DNS, connection, TLS, or timeout. */
    Unreachable,

    /** The server answered with an error status. */
    HttpError,

    /** The link is to a page that has no article to read (Wikipedia's `Special:` pages, say). */
    NotAnArticle,

    /** The picked file can't be opened any more. */
    FileUnavailable,

    /** A book protected by DRM: Mnemo only reads DRM-free books and never tries to get around the protection. */
    Drm,

    /**
     * A PDF page that rendered with nothing on it: an image the renderer can't decode (JPEG 2000 on the desktop, JBIG2
     * without its plugin) comes out white and throws nothing, so a blank page is reported rather than sent on.
     */
    BlankPage,
}

sealed interface SourceResult {
    data class Success(val source: SourceText) : SourceResult

    data class Failure(val problem: SourceProblem, val detail: String? = null) : SourceResult
}

/** What a PDF is before any of its text is read (docs/pdf/ROADMAP.md, P1 and P2). */
data class PdfInfo(
    val pageCount: Int,
    /** The document's own title, or its file name when it has none. */
    val title: String? = null,
    /** Its bookmarks that lead to a page, in document order, [PdfOutlineItem.MAX_LEVEL] levels deep at most. */
    val outline: List<PdfOutlineItem> = emptyList(),
    /**
     * The printed label of each page (`i`, `ii`, `1`, `2`…), or null when the PDF has none or they are just the
     * positions. When set it has [pageCount] entries.
     */
    val labels: List<String>? = null,
) {
    /**
     * The pages of the [index]th outline item: from its page up to the page before the next item at the same or a
     * higher level, or to the last page for the last of them. Always at least its own page.
     */
    fun chapterPages(index: Int): IntRange {
        val item = outline[index]
        val start = item.page.coerceIn(1, pageCount)
        val next = (index + 1 until outline.size).firstOrNull { outline[it].level <= item.level }
        val end = if (next == null) pageCount else outline[next].page - 1
        return start..end.coerceIn(start, pageCount)
    }

    /** The label printed on [page] (1-based), or null when the PDF has no labels. */
    fun label(page: Int): String? = labels?.getOrNull(page - 1)?.takeIf { it.isNotBlank() }

    companion object {
        /** The most pages one read takes. The selection is limited, not the PDF: any pages of it can be chosen. */
        const val MAX_PAGES = 300
    }
}

/** A bookmark of a PDF: [level] 1 is a top-level one, and [page] the 1-based position of the page it opens. */
data class PdfOutlineItem(val title: String, val level: Int, val page: Int) {
    companion object {
        /** Deeper bookmarks are left out: a textbook's parts, chapters and sections are as fine as a picker needs. */
        const val MAX_LEVEL = 3

        /** A safety limit on a hostile or broken outline. */
        const val MAX_ITEMS = 2_000
    }
}

/** A PDF opened for reading in pieces: [id] names its copy in the cache until it is closed. */
data class PdfHandle(val id: String, val info: PdfInfo)

sealed interface PdfInfoResult {
    data class Success(val info: PdfInfo) : PdfInfoResult

    data class Failure(val problem: SourceProblem, val detail: String? = null) : PdfInfoResult
}

sealed interface PdfOpenResult {
    data class Success(val handle: PdfHandle) : PdfOpenResult

    data class Failure(val problem: SourceProblem, val detail: String? = null) : PdfOpenResult
}

/** A book (EPUB) read into chapters. Nothing in it is saved until the user creates decks from it. */
data class BookSource(
    val title: String,
    val author: String? = null,
    /** The book's language tag (`dc:language`), when it names one. */
    val language: String? = null,
    val chapters: List<BookChapter>,
    /** Some of the book was left out (too many chapters, or too much text). */
    val truncated: Boolean = false,
)

/** One chapter of a [BookSource], with its plain text. */
data class BookChapter(
    /** The chapter's position in the book (0-based, after short pages were merged). Stable for the same file. */
    val id: Int,
    val title: String,
    val text: String,
    val kind: ChapterKind = ChapterKind.Content,
    /** The chapter was longer than Mnemo reads and its end was left out. */
    val truncated: Boolean = false,
) {
    val wordCount: Int get() = SourceText.countWords(text)
}

/** What a chapter is, so the picker can leave covers and copyright pages unchecked. */
enum class ChapterKind { Content, FrontMatter, BackMatter }

sealed interface BookResult {
    data class Success(val book: BookSource) : BookResult

    data class Failure(val problem: SourceProblem, val detail: String? = null) : BookResult
}

/** One event of on-device dictation. */
sealed interface DictationEvent {
    /** The recognizer is listening. */
    data object Listening : DictationEvent

    /** The words heard so far in the current phrase; replaced by the next partial or final. */
    data class Partial(val text: String) : DictationEvent

    /** A finished phrase. Dictation keeps listening for the next one until cancelled. */
    data class Final(val text: String) : DictationEvent

    /** Dictation stopped. */
    data class Failed(val problem: DictationProblem) : DictationEvent
}

enum class DictationProblem {
    /** No speech recognizer on this device. */
    Unavailable,

    /** The microphone permission was denied. */
    NoPermission,

    /** The language isn't installed for offline recognition. */
    LanguageUnavailable,

    /** The recognizer is busy or failed. */
    RecognizerError,
}

/** How large a rendered PDF page is (docs/pdf/ROADMAP.md, P3): the token knob for pages sent to a vision model. */
enum class PdfQuality(val longEdge: Int) {
    /** 1,568 px on the long side: legible body text and small print on a page of the usual size. */
    Standard(1_568),

    /** 2,048 px: for dense pages, small type and formulas, at about 1.7 times the tokens. */
    High(2_048),
    ;

    companion object {
        /** The long side of a thumbnail in a page grid. */
        const val THUMBNAIL_EDGE = 320
    }
}

sealed interface PdfPageResult {
    /** A JPEG file in the cache, for as long as the PDF stays open. Don't keep the path. */
    data class Success(val file: java.io.File) : PdfPageResult

    data class Failure(val problem: SourceProblem, val detail: String? = null) : PdfPageResult
}

/**
 * How the pages of a PDF are read into Smart Extract's text box (docs/pdf/ROADMAP.md, P5; ADR 0014). Each mode ends in
 * the same editable box. "Cards from page images" is a fourth mode and comes with P6.
 */
enum class PdfReadMode {
    /** The text layer, read on the device. */
    Text,

    /** The text layer where a page has one, an AI transcription of the page's image where it has none. */
    Auto,

    /** Every selected page is transcribed by a vision model. */
    ReadWithAi,
    ;

    /** Whether reading in this mode may send page images to a provider. */
    val usesAi: Boolean get() = this != Text
}

/** The text layer of single pages, for telling the ones that have text from the ones that are only a picture. */
sealed interface PdfPageTextsResult {
    /** [texts] has every asked-for page that exists, with an empty string for a page with no text. */
    data class Success(val texts: Map<Int, String>) : PdfPageTextsResult

    data class Failure(val problem: SourceProblem, val detail: String? = null) : PdfPageTextsResult
}

object PdfPageText {
    /** A page whose layer has fewer letters and digits than this is a picture with a stray mark, not a page of text. */
    const val MIN_CHARS = 30

    /** Whether [text], the layer of one page, is worth using instead of reading the page's image. */
    fun hasText(text: String): Boolean = text.count { !it.isWhitespace() } >= MIN_CHARS
}
