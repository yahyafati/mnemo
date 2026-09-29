package com.yahyafati.mnemo.core.model

/** What an Anki import added. */
data class ImportSummary(
    /** Decks created (existing decks with the same name are reused). */
    val decks: Int,
    val notes: Int,
    val cards: Int,
    val reviews: Int,
    val media: Int,
    /** Notes skipped because a note with the same Anki guid is already in the collection. */
    val duplicateNotes: Int,
    /** Cards Mnemo can't represent, e.g. the extra templates of a custom note type (ADR 0003). */
    val skippedCards: Int,
)

enum class ExportFormat {
    /** An Anki package, with scheduling and media. */
    Apkg,

    /** The whole collection as JSON, for other tools. */
    Json,
}

/** Why a background transfer (import, export, backup, restore) failed. */
enum class TransferError {
    /** The file is not an Anki package or a Mnemo backup. */
    UnsupportedFile,

    /** The file looks right but can't be read. */
    Corrupt,

    /** Reading or writing a file failed (e.g. out of space, or the folder is gone). */
    Storage,

    Unknown,
}

/** The state of a long-running transfer that runs in the background. */
sealed interface TransferState<out R> {
    data object Idle : TransferState<Nothing>

    /** [progress] is 0–1, or null while the amount of work isn't known yet. */
    data class Running(val progress: Float?) : TransferState<Nothing>

    data class Succeeded<out R>(val result: R) : TransferState<R>

    data class Failed(val error: TransferError) : TransferState<Nothing>
}

/** Which cards the card browser shows. */
data class CardQuery(
    /** Matched against note fields and tags, case-insensitively. Blank matches everything. */
    val text: String = "",
    /** A deck and its subdecks, or every deck when null. */
    val deckId: String? = null,
    val tag: String? = null,
    val status: CardStatus? = null,
    val sort: CardSort = CardSort.Newest,
)

enum class CardStatus {
    New,
    Learning,
    Review,
    /** Due by the end of today. */
    Due,
    Suspended,
    Flagged,
    Starred,
}

enum class CardSort {
    Newest,
    Oldest,
    DueFirst,
}
