package com.yahyafati.mnemo.core.model

/** A card with what the study screen needs to show it. */
data class StudyCard(
    val card: Card,
    val note: Note,
    val kind: NoteKind,
    val deckName: String,
) {
    val sides: CardSides get() = CardSides.of(kind, note.fields, card.templateOrd)
}
