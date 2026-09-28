package com.yahyafati.mnemo.core.model

/**
 * The Markdown shown on each side of a card.
 *
 * For cloze cards, [front] is the cloze text and [clozeOrdinal] says which deletion to hide; the
 * renderer handles the `{{c…}}` markup and reveals it in place. [back] is then the Extra field.
 */
data class CardSides(
    val front: String,
    val back: String,
    val clozeOrdinal: Int? = null,
) {
    companion object {
        fun of(kind: NoteKind, fields: List<String>, templateOrd: Int): CardSides {
            val first = fields.getOrElse(0) { "" }
            val second = fields.getOrElse(1) { "" }
            return when (kind) {
                NoteKind.Basic -> CardSides(first, second)
                NoteKind.Reversed -> if (templateOrd == 0) CardSides(first, second) else CardSides(second, first)
                NoteKind.Cloze -> CardSides(front = first, back = second, clozeOrdinal = templateOrd + 1)
            }
        }
    }
}

/** The [Card.templateOrd] values a note with these [fields] produces. */
fun NoteKind.cardOrdinals(fields: List<String>): List<Int> = when (this) {
    NoteKind.Basic -> listOf(0)
    NoteKind.Reversed -> listOf(0, 1)
    NoteKind.Cloze -> Cloze.ordinals(fields.getOrElse(0) { "" }).map { it - 1 }
}
