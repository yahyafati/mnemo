package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteType
import com.yahyafati.mnemo.core.model.StudyCard
import java.time.Instant

internal val T0: Instant = Instant.parse("2026-01-01T09:00:00Z")

internal fun studyCard(
    id: String,
    state: CardState = CardState.New,
    due: Instant = T0,
    deckId: String = "deck",
): StudyCard {
    val note = Note("n-$id", deckId, NoteType.Basic.id, listOf("Q $id", "A $id"), createdAt = T0, updatedAt = T0)
    val card = Card(
        id = id, noteId = note.id, deckId = deckId, templateOrd = 0, state = state, due = due,
        stability = if (state == CardState.New) null else 3.0,
        difficulty = if (state == CardState.New) null else 5.0,
        step = if (state == CardState.Learning || state == CardState.Relearning) 0 else null,
        lastReview = if (state == CardState.New) null else due.minusSeconds(86_400),
        createdAt = T0, updatedAt = T0,
    )
    return StudyCard(card, note, NoteKind.Basic, "Deck")
}
