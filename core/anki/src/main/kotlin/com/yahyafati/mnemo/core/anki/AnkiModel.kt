package com.yahyafati.mnemo.core.anki

// Anki's own records, as stored in a package. Field names follow Anki's columns; units are noted
// where Anki mixes them (seconds vs milliseconds vs day numbers).

/** Which collection file a package carries. */
enum class AnkiFormat {
    /** `collection.anki2`: schema 11, Anki 2.0 and later. */
    Legacy1,

    /** `collection.anki21`: schema 11 with 2.1 features. */
    Legacy2,

    /** `collection.anki21b`: zstd-compressed schema 18, Anki 2.1.50 and later. */
    Latest,
}

data class AnkiNotetype(
    val id: Long,
    val name: String,
    val isCloze: Boolean,
    val fields: List<String>,
    val templates: List<AnkiTemplate>,
)

data class AnkiTemplate(
    val ord: Int,
    val name: String,
    /** The question template, e.g. `{{Front}}`. */
    val front: String,
    /** The answer template, e.g. `{{FrontSide}}<hr id=answer>{{Back}}`. */
    val back: String,
)

data class AnkiDeck(
    val id: Long,
    /** Full name with `::` between levels. */
    val name: String,
    val description: String = "",
    /** A filtered (custom study) deck: it holds cards temporarily and is never an import target. */
    val filtered: Boolean = false,
    /** Mnemo's deck category, kept in exported packages so a round trip restores it. */
    val mnemoCategory: String? = null,
    val mnemoStarred: Boolean = false,
)

data class AnkiNote(
    /** Creation time in epoch milliseconds (unique within a collection). */
    val id: Long,
    val guid: String,
    val notetypeId: Long,
    /** Last modified, epoch seconds. */
    val modified: Long,
    val tags: List<String>,
    val fields: List<String>,
    /** Unused by Anki; Mnemo stores its original Markdown here on export (see [MnemoNoteData]). */
    val data: String = "",
)

data class AnkiCard(
    val id: Long,
    val noteId: Long,
    val deckId: Long,
    val ord: Int,
    /** Epoch seconds. */
    val modified: Long,
    /** 0 new, 1 learning, 2 review, 3 relearning. */
    val type: Int,
    /** -3/-2 buried, -1 suspended, 0 new, 1 learning, 2 review, 3 day-learning, 4 preview. */
    val queue: Int,
    /** New: position. Learning (queue 1): epoch seconds. Review and day-learning: days since collection creation. */
    val due: Long,
    /** Days, or negative seconds while learning. */
    val interval: Int,
    /** Ease in permille (2500 = 2.5). */
    val factor: Int,
    val reps: Int,
    val lapses: Int,
    /** Learning steps left: `left % 1000` in total. */
    val left: Int,
    /** In a filtered deck: the home deck's due and id. */
    val originalDue: Long = 0,
    val originalDeckId: Long = 0,
    /** Low 3 bits: the flag color, 0 for none. */
    val flags: Int = 0,
    /** JSON; newer Anki keeps FSRS memory state here (`s`, `d`, `lrt`). */
    val data: String = "",
)

data class AnkiRevlog(
    /** Review time, epoch milliseconds. */
    val id: Long,
    val cardId: Long,
    /** 1–4 again…easy; 0 for manual rescheduling. */
    val ease: Int,
    val interval: Int,
    val lastInterval: Int,
    val factor: Int,
    val timeMs: Int,
    /** 0 learn, 1 review, 2 relearn, 3 filtered, 4 manual, 5 rescheduled. */
    val type: Int,
)

/** A media file in a package: its entry in the zip and the name note fields use for it. */
data class AnkiMedia(
    val entryName: String,
    val name: String,
)

/** A file isn't an Anki package, or is damaged. */
class AnkiFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)
