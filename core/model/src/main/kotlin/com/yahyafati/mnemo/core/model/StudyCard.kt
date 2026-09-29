package com.yahyafati.mnemo.core.model

/** A card with what the study screen needs to show it. */
data class StudyCard(
    val card: Card,
    val note: Note,
    val kind: NoteKind,
    val deckName: String,
) {
    /** Computed once per instance: the study screen reads it on every recomposition. */
    val sides: CardSides by lazy(LazyThreadSafetyMode.PUBLICATION) {
        CardSides.of(kind, note.fields, card.templateOrd, seed = card.id.hashCode().toLong())
    }

    /** The note's hint, if it has one. */
    val hint: String? get() = note.hint?.takeIf { it.isNotBlank() }
}
