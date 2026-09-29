package com.yahyafati.mnemo.core.anki

import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteSource
import com.yahyafati.mnemo.core.model.NoteType
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.model.ReviewLog
import com.yahyafati.mnemo.core.model.cardOrdinals
import com.yahyafati.mnemo.core.scheduler.Fsrs
import com.yahyafati.mnemo.core.scheduler.FsrsCard
import com.yahyafati.mnemo.core.scheduler.FsrsParameters
import com.yahyafati.mnemo.core.scheduler.FsrsRating
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** A converted note and everything to insert with it. */
data class ImportedNote(
    val note: Note,
    val cards: List<Card>,
    val reviews: List<ReviewLog>,
    /** Anki cards of this note that Mnemo can't represent (ADR 0003). */
    val skippedCards: Int,
)

/**
 * Turns Anki notes, cards and review logs into Mnemo ones (ADR 0001, ADR 0003).
 *
 * Note types: a cloze type becomes a Cloze note; a type whose second template is the first one
 * reversed becomes Basic + Reversed; anything else becomes Basic from its first template. The
 * fields a template shows on the question side make the front, the ones the answer adds make the
 * back. Other templates' cards are skipped and counted.
 *
 * Scheduling: FSRS memory state comes from the card's own FSRS data when Anki has it, otherwise
 * from replaying its review log, otherwise it is estimated from the SM-2 interval and ease. Due
 * dates are kept, so imported cards aren't all due at once.
 *
 * Replay counts days the way the history was made: Anki counts calendar days (from the
 * collection's day boundary), Mnemo whole 24-hour periods. A review at 12:15 and the next one four
 * days later at 12:00 are 4 days apart in Anki and 3 in Mnemo, which changes the memory state.
 * Packages Mnemo exported (recognized by their note stash) replay on real timestamps, so a round
 * trip is exact; everything else replays on Anki's day numbers, matching what Anki computes.
 *
 * @param learningSteps how many learning steps the user has, to place learning cards on one.
 */
class AnkiImportMapper(
    private val collectionCreated: Instant,
    notetypes: List<AnkiNotetype>,
    private val now: Instant,
    private val learningSteps: Int,
    private val relearningSteps: Int,
) {
    private val fsrs = Fsrs(FsrsParameters(enableFuzzing = false))
    private val layouts: Map<Long, Layout> = notetypes.associate { it.id to layoutOf(it) }

    private sealed interface Layout {
        /** Question fields, answer fields, and whether the second template reverses the first. */
        data class Standard(val front: List<Int>, val back: List<Int>, val reversible: Boolean) : Layout

        data class ClozeText(val text: Int, val extra: List<Int>) : Layout
    }

    /** The id Mnemo exported [note] with, if it came from a Mnemo export. */
    fun mnemoIdOf(note: AnkiNote): String? = MnemoNoteData.decode(note)?.id

    /**
     * Converts [note]. Returns null if it can't be imported at all (unknown note type, or an
     * empty question); all its cards then count as skipped.
     *
     * @param noteId the id for the Mnemo note (the caller decides, to keep exported ids stable).
     * @param deckIdFor the Mnemo deck for an Anki deck id.
     */
    fun map(
        note: AnkiNote,
        cards: List<AnkiCard>,
        revlog: Map<Long, List<AnkiRevlog>>,
        noteId: String,
        deckIdFor: (Long) -> String,
        mediaRef: (String) -> String?,
    ): ImportedNote? {
        val layout = layouts[note.notetypeId] ?: return null
        val stash = MnemoNoteData.decode(note)
        fun markdown(indexes: List<Int>) = indexes.map { note.fields.getOrElse(it) { "" } }
            .map { AnkiHtml.toMarkdown(it, mediaRef) }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")

        val (kind, converted) = when (layout) {
            is Layout.Standard -> {
                val reversed = layout.reversible && cards.any { it.ord == 1 }
                (if (reversed) NoteKind.Reversed else NoteKind.Basic) to listOf(markdown(layout.front), markdown(layout.back))
            }
            is Layout.ClozeText -> NoteKind.Cloze to listOf(markdown(listOf(layout.text)), markdown(layout.extra))
        }
        val fields = stash?.fields?.takeIf { it.size == 2 } ?: converted
        val ordinals = kind.cardOrdinals(fields)
        if (fields[0].isBlank() || ordinals.isEmpty()) return null

        val kept = cards.filter { it.ord in ordinals }.distinctBy { it.ord }
        val deckId = deckIdFor(kept.firstOrNull()?.homeDeckId ?: cards.firstOrNull()?.homeDeckId ?: 0)
        val createdAt = stash?.createdAt?.let(Instant::ofEpochMilli)
            ?: Instant.ofEpochMilli(note.id).takeIf { it.isAfter(ANKI_EPOCH) && it.isBefore(now) }
            ?: now
        val starred = note.tags.any { it.equals(MARKED, ignoreCase = true) }
        val mnemoNote = Note(
            id = noteId,
            deckId = deckId,
            noteTypeId = NoteType.builtIn(kind).id,
            fields = fields,
            tags = note.tags.filterNot { it.equals(MARKED, ignoreCase = true) },
            source = NoteSource.Import,
            createdAt = createdAt,
            updatedAt = stash?.updatedAt?.let(Instant::ofEpochMilli)
                ?: Instant.ofEpochSecond(note.modified).takeIf { it.isAfter(createdAt) }
                ?: createdAt,
            guid = note.guid,
        )

        val mnemoCards = mutableListOf<Card>()
        val reviews = mutableListOf<ReviewLog>()
        for (card in kept) {
            val history = replay(revlog[card.id].orEmpty(), ankiDays = stash == null)
            val mapped = card(card, noteId, deckIdFor(card.homeDeckId), createdAt, starred, history)
            mnemoCards += mapped
            reviews += history.map { it.toLog(mapped.id) }
        }
        // Mnemo keeps one card per ordinal; create any the package lacked, as a new note would.
        for (ord in ordinals - kept.map { it.ord }.toSet()) {
            mnemoCards += Card(
                id = UUID.randomUUID().toString(), noteId = noteId, deckId = deckId, templateOrd = ord,
                due = createdAt, starred = starred, createdAt = createdAt, updatedAt = now,
            )
        }
        return ImportedNote(mnemoNote, mnemoCards.sortedBy { it.templateOrd }, reviews, skippedCards = cards.size - kept.size)
    }

    private fun card(
        card: AnkiCard,
        noteId: String,
        deckId: String,
        noteCreatedAt: Instant,
        starred: Boolean,
        history: List<ReplayedReview>,
    ): Card {
        val data = AnkiCardData.decode(card.data)
        val extras = data.extras
        val state = when (card.type) {
            1 -> CardState.Learning
            2 -> CardState.Review
            3 -> CardState.Relearning
            else -> CardState.New
        }
        val createdAt = extras?.createdAt?.let(Instant::ofEpochMilli) ?: noteCreatedAt
        val rawDue = if (card.originalDeckId != 0L && card.originalDue != 0L) card.originalDue else card.due
        val due = extras?.due?.let(Instant::ofEpochMilli) ?: when (state) {
            CardState.New -> createdAt
            // Learning cards are due at a timestamp, except interday learning, which uses days.
            CardState.Learning, CardState.Relearning ->
                if (rawDue > TIMESTAMP_THRESHOLD) Instant.ofEpochSecond(rawDue) else collectionCreated.plus(Duration.ofDays(rawDue))
            CardState.Review -> collectionCreated.plus(Duration.ofDays(rawDue))
        }

        val replayed = history.lastOrNull()
        val (stability, difficulty) = when {
            state == CardState.New -> null to null
            data.stability != null && data.difficulty != null -> data.stability to data.difficulty
            replayed != null -> replayed.stability to replayed.difficulty
            state == CardState.Review || state == CardState.Relearning -> {
                val estimate = fsrs.memoryStateFromSm2((card.factor.takeIf { it > 0 } ?: DEFAULT_FACTOR) / 1000.0, card.interval.coerceAtLeast(1).toDouble())
                estimate.stability to estimate.difficulty
            }
            else -> null to null
        }
        val lastReview = when {
            state == CardState.New -> null
            extras?.lastReview != null -> Instant.ofEpochMilli(extras.lastReview)
            data.lastReviewSeconds != null -> Instant.ofEpochSecond(data.lastReviewSeconds)
            replayed != null -> replayed.reviewedAt
            state == CardState.Review -> due.minus(Duration.ofDays(card.interval.coerceAtLeast(1).toLong()))
            else -> null
        }
        val step = when (state) {
            CardState.Learning, CardState.Relearning -> extras?.step ?: run {
                val steps = if (state == CardState.Learning) learningSteps else relearningSteps
                (steps - card.left % 1000).coerceIn(0, (steps - 1).coerceAtLeast(0))
            }
            else -> null
        }
        return Card(
            id = UUID.randomUUID().toString(),
            noteId = noteId,
            deckId = deckId,
            templateOrd = card.ord,
            state = state,
            due = due,
            stability = stability,
            difficulty = difficulty,
            step = step,
            lastReview = lastReview,
            reps = card.reps,
            lapses = card.lapses,
            flagged = card.flags and FLAG_MASK != 0,
            starred = starred,
            suspended = card.queue == QUEUE_SUSPENDED,
            createdAt = createdAt,
            updatedAt = now,
        )
    }

    /** One answer from the log, with the memory state FSRS gives after it. */
    private data class ReplayedReview(
        val rating: Rating,
        val stateBefore: CardState,
        val reviewedAt: Instant,
        val elapsedDays: Int,
        val scheduledDays: Int,
        val durationMs: Long,
        val stability: Double,
        val difficulty: Double,
    ) {
        fun toLog(cardId: String) = ReviewLog(
            id = UUID.randomUUID().toString(),
            cardId = cardId,
            rating = rating,
            stateBefore = stateBefore,
            reviewedAt = reviewedAt,
            elapsedDays = elapsedDays,
            scheduledDays = scheduledDays,
            durationMs = durationMs,
            stabilityAfter = stability,
            difficultyAfter = difficulty,
        )
    }

    /**
     * Replays [log] through FSRS from a blank card. Manual reschedules carry no answer and are
     * skipped; a reset (forget) starts the memory state over, and the history before it is dropped.
     *
     * With [ankiDays], FSRS sees each review at the start of its Anki day (plus a millisecond per
     * earlier review, to keep the order), so elapsed time is counted in Anki's calendar days.
     */
    private fun replay(log: List<AnkiRevlog>, ankiDays: Boolean): List<ReplayedReview> {
        val lastReset = log.indexOfLast { it.type == REVLOG_MANUAL && it.ease == 0 && it.interval == 0 }
        val answers = log.drop(lastReset + 1).filter { it.ease in 1..4 && it.type in REVLOG_LEARN..REVLOG_FILTERED }
        val out = ArrayList<ReplayedReview>(answers.size)
        var card: FsrsCard? = null
        var previousAt: Instant? = null
        for ((index, entry) in answers.withIndex()) {
            val reviewedAt = Instant.ofEpochMilli(entry.id)
            val at = if (ankiDays) {
                val day = Math.floorDiv(Duration.between(collectionCreated, reviewedAt).toMillis(), DAY_MS)
                collectionCreated.plusMillis(day * DAY_MS + index)
            } else {
                reviewedAt
            }
            val rating = FsrsRating.entries.first { it.value == entry.ease }
            val next = fsrs.review(card ?: FsrsCard(due = at), rating, at)
            out += ReplayedReview(
                rating = Rating.entries.first { it.value == entry.ease },
                stateBefore = when {
                    index == 0 -> CardState.New
                    entry.type == REVLOG_REVIEW -> CardState.Review
                    entry.type == REVLOG_RELEARN -> CardState.Relearning
                    entry.type == REVLOG_FILTERED && entry.lastInterval > 0 -> CardState.Review
                    else -> CardState.Learning
                },
                reviewedAt = reviewedAt,
                elapsedDays = previousAt?.let { wholeDays(it, at) } ?: 0,
                scheduledDays = entry.interval.coerceAtLeast(0),
                durationMs = entry.timeMs.coerceAtLeast(0).toLong(),
                stability = checkNotNull(next.stability),
                difficulty = checkNotNull(next.difficulty),
            )
            card = next
            previousAt = at
        }
        return out
    }

    private val AnkiCard.homeDeckId: Long get() = if (originalDeckId != 0L) originalDeckId else deckId

    internal companion object {
        const val MARKED = "marked"
        private const val DEFAULT_FACTOR = 2500
        private const val FLAG_MASK = 0b111
        private const val QUEUE_SUSPENDED = -1
        private const val REVLOG_LEARN = 0
        private const val REVLOG_REVIEW = 1
        private const val REVLOG_RELEARN = 2
        private const val REVLOG_FILTERED = 3
        private const val REVLOG_MANUAL = 4

        private const val DAY_MS = 86_400_000L

        /** Larger due values are epoch seconds rather than day numbers. */
        private const val TIMESTAMP_THRESHOLD = 1_000_000_000L

        /** Anki didn't exist before 2006; older "ids" aren't creation times. */
        private val ANKI_EPOCH = Instant.parse("2006-01-01T00:00:00Z")

        fun wholeDays(from: Instant, to: Instant): Int =
            Math.floorDiv(Duration.between(from, to).toMillis(), Duration.ofDays(1).toMillis()).toInt().coerceAtLeast(0)

        private fun layoutOf(type: AnkiNotetype): Layout {
            val names = type.fields
            val first = type.templates.firstOrNull()
            if (type.isCloze) {
                val text = first?.let { AnkiTemplates.clozeFields(it.front, names).firstOrNull() } ?: 0
                val extra = first?.let { AnkiTemplates.backFields(it.front, it.back, names) }.orEmpty()
                    .filter { it != text }
                return Layout.ClozeText(text, extra)
            }
            if (first == null) return Layout.Standard(listOf(0), (1 until names.size).toList(), reversible = false)
            val front = AnkiTemplates.frontFields(first.front, names).ifEmpty { listOf(0) }
            val back = AnkiTemplates.backFields(first.front, first.back, names)
                .ifEmpty { names.indices.filter { it !in front } }
            val second = type.templates.getOrNull(1)
            val reversible = second != null &&
                AnkiTemplates.frontFields(second.front, names).toSet() == back.toSet() &&
                AnkiTemplates.backFields(second.front, second.back, names).toSet() == front.toSet()
            return Layout.Standard(front, back, reversible)
        }
    }
}
