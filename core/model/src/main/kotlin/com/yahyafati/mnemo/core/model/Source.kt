package com.yahyafati.mnemo.core.model

/** Where Smart Extract's text comes from. Pasted and dictated text needs no reading. */
sealed interface SourceInput {
    /** A PDF picked through the Storage Access Framework (a `content://` URI). */
    data class Pdf(val uri: String) : SourceInput

    /** A plain text or Markdown file (a file dropped on the desktop window). */
    data class TextFile(val uri: String) : SourceInput

    /** A web page, or a PDF behind a link. */
    data class Link(val url: String) : SourceInput
}

/** Plain text read from a [SourceInput], ready to be edited and sent. */
data class SourceText(
    val text: String,
    /** The document's title or file name, when known. */
    val title: String? = null,
    /** Some of the source was left out (too many pages or characters). */
    val truncated: Boolean = false,
) {
    val wordCount: Int get() = countWords(text)

    companion object {
        private val WORD = Regex("""\S+""")

        fun countWords(text: String): Int = WORD.findAll(text).count()
    }
}

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

    /** The picked file can't be opened any more. */
    FileUnavailable,

    /** A book protected by DRM: Mnemo only reads DRM-free books and never tries to get around the protection. */
    Drm,
}

sealed interface SourceResult {
    data class Success(val source: SourceText) : SourceResult

    data class Failure(val problem: SourceProblem, val detail: String? = null) : SourceResult
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
