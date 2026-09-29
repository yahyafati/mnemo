package com.yahyafati.mnemo.core.model

/**
 * The Markdown shown on each side of a card.
 *
 * For cloze cards, [front] is the cloze text and [clozeOrdinal] says which deletion to hide; the
 * renderer handles the `{{c…}}` markup and reveals it in place. [back] is then the Extra field.
 *
 * Type-in cards set [typeIn]: the user types an answer, which is compared with [back]. Multiple
 * choice cards set [choices]: the options in the order to show them, the correct one at
 * [correctChoice]; [back] is the correct answer.
 */
data class CardSides(
    val front: String,
    val back: String,
    val clozeOrdinal: Int? = null,
    val typeIn: Boolean = false,
    val choices: List<String>? = null,
    val correctChoice: Int = -1,
) {
    companion object {
        /**
         * @param seed orders a multiple-choice card's options: the same seed (the card's id) always
         *   gives the same order, so answers aren't in a fixed position but don't move on redraw.
         */
        fun of(kind: NoteKind, fields: List<String>, templateOrd: Int, seed: Long = 0): CardSides {
            val first = fields.getOrElse(0) { "" }
            val second = fields.getOrElse(1) { "" }
            return when (kind) {
                NoteKind.Basic -> CardSides(first, second)
                NoteKind.Reversed -> if (templateOrd == 0) CardSides(first, second) else CardSides(second, first)
                NoteKind.Cloze -> CardSides(front = first, back = second, clozeOrdinal = templateOrd + 1)
                NoteKind.TypeIn -> CardSides(first, second, typeIn = true)
                NoteKind.MultipleChoice -> {
                    val options = MultipleChoice.options(second, fields.getOrElse(2) { "" }, seed)
                    CardSides(first, second, choices = options.choices, correctChoice = options.correct)
                }
            }
        }
    }
}

/** The [Card.templateOrd] values a note with these [fields] produces. */
fun NoteKind.cardOrdinals(fields: List<String>): List<Int> = when (this) {
    NoteKind.Basic, NoteKind.TypeIn, NoteKind.MultipleChoice -> listOf(0)
    NoteKind.Reversed -> listOf(0, 1)
    NoteKind.Cloze -> Cloze.ordinals(fields.getOrElse(0) { "" }).map { it - 1 }
}
