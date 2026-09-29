package com.yahyafati.mnemo.core.data.mapper

import com.yahyafati.mnemo.core.database.dao.ReviewCounts
import com.yahyafati.mnemo.core.database.entity.CardEntity
import com.yahyafati.mnemo.core.database.entity.DeckEntity
import com.yahyafati.mnemo.core.database.entity.NoteEntity
import com.yahyafati.mnemo.core.database.entity.ReviewLogEntity
import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.DailyReviewCounts
import com.yahyafati.mnemo.core.model.Deck
import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.NoteSource
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.model.ReviewLog
import java.time.Instant

// Entity ↔ model. Entities never leave the data layer (ARCHITECTURE §2).

private fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)

internal fun DeckEntity.toModel() = Deck(
    id = id,
    name = name,
    parentId = parentId,
    description = description,
    category = category,
    starred = starred,
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
)

internal fun Deck.toEntity() = DeckEntity(
    id = id,
    parentId = parentId,
    name = name,
    description = description,
    category = category,
    starred = starred,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

internal fun NoteEntity.toModel() = Note(
    id = id,
    deckId = deckId,
    noteTypeId = noteTypeId,
    fields = fields,
    tags = tags,
    source = NoteSource.entries.firstOrNull { it.name == source } ?: NoteSource.Manual,
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
    guid = guid,
)

internal fun Note.toEntity() = NoteEntity(
    id = id,
    deckId = deckId,
    noteTypeId = noteTypeId,
    fields = fields,
    tags = tags,
    source = source.name,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    guid = guid,
)

internal fun CardEntity.toModel() = Card(
    id = id,
    noteId = noteId,
    deckId = deckId,
    templateOrd = templateOrd,
    state = CardState.fromValue(state),
    due = due.toInstant(),
    stability = stability,
    difficulty = difficulty,
    step = step,
    lastReview = lastReview?.toInstant(),
    reps = reps,
    lapses = lapses,
    flagged = flagged,
    starred = starred,
    suspended = suspended,
    buriedUntil = buriedUntil?.toInstant(),
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
)

internal fun Card.toEntity() = CardEntity(
    id = id,
    noteId = noteId,
    deckId = deckId,
    templateOrd = templateOrd,
    state = state.value,
    due = due.toEpochMilli(),
    stability = stability,
    difficulty = difficulty,
    step = step,
    lastReview = lastReview?.toEpochMilli(),
    reps = reps,
    lapses = lapses,
    flagged = flagged,
    starred = starred,
    suspended = suspended,
    buriedUntil = buriedUntil?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

internal fun ReviewLog.toEntity() = ReviewLogEntity(
    id = id,
    cardId = cardId,
    rating = rating.value,
    stateBefore = stateBefore.value,
    reviewedAt = reviewedAt.toEpochMilli(),
    elapsedDays = elapsedDays,
    scheduledDays = scheduledDays,
    durationMs = durationMs,
    stabilityAfter = stabilityAfter,
    difficultyAfter = difficultyAfter,
    createdAt = reviewedAt.toEpochMilli(),
    updatedAt = reviewedAt.toEpochMilli(),
)

internal fun ReviewLogEntity.toModel() = ReviewLog(
    id = id,
    cardId = cardId,
    rating = Rating.entries.first { it.value == rating },
    stateBefore = CardState.fromValue(stateBefore),
    reviewedAt = reviewedAt.toInstant(),
    elapsedDays = elapsedDays,
    scheduledDays = scheduledDays,
    durationMs = durationMs,
    stabilityAfter = stabilityAfter,
    difficultyAfter = difficultyAfter,
)

internal fun ReviewCounts.toModel() = DailyReviewCounts(newStudied, reviewsDone, total)
