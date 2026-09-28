package com.yahyafati.mnemo.core.model

import java.time.Instant

enum class CardState(val value: Int) {
    New(0),
    Learning(1),
    Review(2),
    Relearning(3),
    ;

    companion object {
        fun fromValue(value: Int): CardState = entries.first { it.value == value }
    }
}

/**
 * One reviewable card of a [Note], with its FSRS memory state (ADR 0001).
 *
 * @property templateOrd which card of the note this is: 0/1 for front/back of a reversed note,
 *   the cloze number minus one for a cloze note.
 * @property step learning or relearning step; null for New and Review cards.
 * @property buriedUntil hidden from study until this instant (the next day's start).
 */
data class Card(
    val id: String,
    val noteId: String,
    val deckId: String,
    val templateOrd: Int,
    val state: CardState = CardState.New,
    val due: Instant,
    val stability: Double? = null,
    val difficulty: Double? = null,
    val step: Int? = null,
    val lastReview: Instant? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
    val flagged: Boolean = false,
    val starred: Boolean = false,
    val suspended: Boolean = false,
    val buriedUntil: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
