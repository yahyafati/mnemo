package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.data.mapper.toEntity
import com.yahyafati.mnemo.core.data.mapper.toModel
import com.yahyafati.mnemo.core.database.dao.CardDao
import com.yahyafati.mnemo.core.database.dao.ReviewLogDao
import com.yahyafati.mnemo.core.database.entity.CardEntity
import com.yahyafati.mnemo.core.database.entity.ReviewLogEntity
import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.Rating
import java.time.Instant

/**
 * Makes a card's schedule agree with the reviews it has, after other devices' changes were applied (ADR 0013).
 *
 * Review logs are facts and the schedule is derived from them. Normally the schedule that arrived with the logs is
 * right: one device reviewed, the others take its result. When two devices answered the same card without seeing
 * each other's answer, each log carries a schedule that doesn't include the other's, so the answers are **replayed**
 * in the order they were given, starting from the schedule after the last answer both devices have.
 *
 * That "both have" is read off the logs: answers on one device count 1, 2, 3 … in `repsAfter` and each starts in the
 * state the previous one left (a chain). A log that doesn't continue the one before it, in `reviewedAt` order, was
 * made without it, so the chain forks there. A log that starts over (a new card, `repsAfter` 1, after a longer
 * chain) is a card that was reset, not a fork. A replay is a function of the logs and the settings alone, so every
 * device that has the same logs computes the same schedule, and it is a normal local write: the outcome travels to
 * the devices that haven't computed it.
 *
 * Without a fork, the schedule is still checked against the newest answer: if the card is older than a live log (the
 * answer it was based on was undone on another device, say, and a third one answered on top of it), the answers after
 * the card's last review are replayed on the card.
 *
 * Only logs that carry the schedule they produced take part (not Anki imports, nor rows from before schema v6).
 */
internal class CardReconciler(
    private val cardDao: CardDao,
    private val reviewLogDao: ReviewLogDao,
) {
    /** Returns whether the card's schedule changed. */
    suspend fun reconcile(cardId: String, answerer: CardAnswerer): Boolean {
        val entity = cardDao.getCard(cardId) ?: return false
        val logs = reviewLogDao.getLiveForCard(cardId).filter { it.hasSnapshot() }
        if (logs.isEmpty()) return false

        val card = entity.toModel()
        val fork = firstFork(logs)
        val (start, replay) = if (fork != null) {
            logs[fork - 1].restoreOn(card) to logs.subList(fork, logs.size)
        } else {
            val last = card.lastReview?.toEpochMilli()
            card to logs.filter { last == null || it.reviewedAt > last }
        }
        if (replay.isEmpty()) return false

        var replayed = start
        for (log in replay) {
            replayed = answerer.answer(replayed, Rating.entries.first { it.value == log.rating }, Instant.ofEpochMilli(log.reviewedAt))
        }
        val updated = entity.withScheduleOf(replayed)
        if (updated == entity) return false
        cardDao.update(updated)
        return true
    }

    /** The index of the first log that doesn't continue the one before it, or null. */
    private fun firstFork(logs: List<ReviewLogEntity>): Int? {
        for (i in 1 until logs.size) if (!continues(logs[i - 1], logs[i])) return i
        return null
    }

    private fun continues(previous: ReviewLogEntity, log: ReviewLogEntity): Boolean {
        val next = log.repsAfter == previous.repsAfter!! + 1 && log.stateBefore == previous.stateAfter
        val startedOver = log.stateBefore == CardState.New.value && log.repsAfter == 1 && previous.repsAfter!! > 1
        return next || startedOver
    }

    private fun ReviewLogEntity.hasSnapshot() = stateAfter != null && dueAfter != null && repsAfter != null && lapsesAfter != null

    /** [card] with the schedule this answer produced. */
    private fun ReviewLogEntity.restoreOn(card: Card) = card.copy(
        state = CardState.fromValue(stateAfter!!),
        due = Instant.ofEpochMilli(dueAfter!!),
        stability = stabilityAfter,
        difficulty = difficultyAfter,
        step = stepAfter,
        lastReview = Instant.ofEpochMilli(reviewedAt),
        reps = repsAfter!!,
        lapses = lapsesAfter!!,
    )

    private fun CardEntity.withScheduleOf(card: Card): CardEntity {
        val schedule = card.toEntity()
        return copy(
            state = schedule.state,
            due = schedule.due,
            stability = schedule.stability,
            difficulty = schedule.difficulty,
            step = schedule.step,
            lastReview = schedule.lastReview,
            reps = schedule.reps,
            lapses = schedule.lapses,
        )
    }
}
