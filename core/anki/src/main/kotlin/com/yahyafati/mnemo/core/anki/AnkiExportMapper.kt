package com.yahyafati.mnemo.core.anki

import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.Deck
import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.ReviewLog
import com.yahyafati.mnemo.core.scheduler.Fsrs
import com.yahyafati.mnemo.core.scheduler.FsrsMemoryState
import java.time.Duration
import java.time.Instant

/** One note's rows for the package. */
data class ExportedNote(
    val note: AnkiNote,
    val cards: List<AnkiCard>,
    val revlog: List<AnkiRevlog>,
)

/**
 * Turns Mnemo notes into Anki rows for [AnkiPackageWriter] (ADR 0003).
 *
 * Cards get both schedules: SM-2 fields (type, queue, due, interval, ease) that any Anki
 * understands, and FSRS memory state in card data, which Anki uses when FSRS is on. Fields become
 * HTML; the original Markdown and exact dates ride along for a lossless import back into Mnemo.
 *
 * Anki ids are millisecond timestamps and must be unique, so creation times are nudged by a
 * millisecond where they collide. Stateful: use one mapper per export.
 *
 * @param collectionCreated the package's `crt`: review due days count from here. It must not be
 *   after any exported review's due date (use the start of the earliest note's study day).
 */
class AnkiExportMapper(
    val collectionCreated: Instant,
    private val desiredRetention: Double,
    private val learningSteps: Int,
    private val relearningSteps: Int,
) {
    private val fsrs = Fsrs()
    private val deckIds = mutableMapOf<String, Long>()
    private val usedDeckIds = mutableSetOf(AnkiPackageWriter.DEFAULT_DECK_ID)
    private val usedNoteIds = mutableSetOf<Long>()
    private val usedCardIds = mutableSetOf<Long>()
    private val usedRevlogIds = mutableSetOf<Long>()
    private var newPosition = 0L

    val notetypes: List<AnkiNotetype> get() = MnemoNotetypes.All

    /** [deck] as an Anki deck named by its full [path]. Call for every deck before its notes. */
    fun deck(deck: Deck, path: String): AnkiDeck {
        val id = deckIds.getOrPut(deck.id) { claim(usedDeckIds, deck.createdAt.toEpochMilli()) }
        return AnkiDeck(
            id = id,
            name = path,
            description = deck.description,
            mnemoCategory = deck.category,
            mnemoStarred = deck.starred,
        )
    }

    /**
     * [note] with its [cards] and their [reviews]. [mediaName] gives the package file name for a
     * media hash, or null if the file is missing.
     */
    fun note(
        note: Note,
        kind: NoteKind,
        cards: List<Card>,
        reviews: List<ReviewLog>,
        mediaName: (String) -> String?,
    ): ExportedNote {
        val noteId = claim(usedNoteIds, note.createdAt.toEpochMilli())
        val html = note.fields.map { AnkiHtml.fromMarkdown(it, mediaName) }
        val starred = cards.any { it.starred }
        val ankiNote = AnkiNote(
            id = noteId,
            guid = note.guid ?: ankiGuidFor(note.id),
            notetypeId = MnemoNotetypes.forKind(kind).id,
            modified = note.updatedAt.epochSecond,
            tags = note.tags + if (starred) listOf(AnkiImportMapper.MARKED) else emptyList(),
            fields = html,
            data = MnemoNoteData.encode(note, kind, html),
        )
        val byCard = reviews.groupBy { it.cardId }
        val ankiCards = mutableListOf<AnkiCard>()
        val revlog = mutableListOf<AnkiRevlog>()
        for (card in cards.sortedBy { it.templateOrd }) {
            val cardId = claim(usedCardIds, card.createdAt.toEpochMilli())
            ankiCards += card(card, cardId, noteId)
            var lastInterval = 0
            for (review in byCard[card.id].orEmpty().sortedBy { it.reviewedAt }) {
                val interval = review.scheduledDays
                revlog += AnkiRevlog(
                    id = claim(usedRevlogIds, review.reviewedAt.toEpochMilli()),
                    cardId = cardId,
                    ease = review.rating.value,
                    interval = interval,
                    lastInterval = lastInterval,
                    factor = factor(review.stabilityAfter, review.difficultyAfter),
                    timeMs = review.durationMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                    type = when (review.stateBefore) {
                        CardState.New, CardState.Learning -> REVLOG_LEARN
                        CardState.Review -> REVLOG_REVIEW
                        CardState.Relearning -> REVLOG_RELEARN
                    },
                )
                lastInterval = interval
            }
        }
        return ExportedNote(ankiNote, ankiCards, revlog)
    }

    private fun card(card: Card, cardId: Long, noteId: Long): AnkiCard {
        val deckId = checkNotNull(deckIds[card.deckId]) { "Deck ${card.deckId} was not exported" }
        val learningLeft = when (card.state) {
            CardState.Learning -> (learningSteps - (card.step ?: 0)).coerceAtLeast(1)
            CardState.Relearning -> (relearningSteps - (card.step ?: 0)).coerceAtLeast(1)
            else -> 0
        }
        val (type, due, interval) = when (card.state) {
            CardState.New -> Triple(0, ++newPosition, 0)
            CardState.Learning -> Triple(1, card.due.epochSecond, 0)
            CardState.Relearning -> Triple(3, card.due.epochSecond, 1)
            CardState.Review -> Triple(
                2,
                Math.floorDiv(Duration.between(collectionCreated, card.due).seconds, DAY_SECONDS),
                card.lastReview?.let { AnkiImportMapper.wholeDays(it, card.due) }?.coerceAtLeast(1) ?: 1,
            )
        }
        val queue = when {
            card.suspended -> QUEUE_SUSPENDED
            card.state == CardState.New -> 0
            card.state == CardState.Review -> 2
            else -> 1
        }
        val data = AnkiCardData(
            stability = card.stability,
            difficulty = card.difficulty,
            lastReviewSeconds = card.lastReview?.epochSecond,
            extras = AnkiCardData.CardExtras(
                due = card.due.toEpochMilli(),
                lastReview = card.lastReview?.toEpochMilli(),
                step = card.step,
                createdAt = card.createdAt.toEpochMilli(),
            ),
        )
        return AnkiCard(
            id = cardId,
            noteId = noteId,
            deckId = deckId,
            ord = card.templateOrd,
            modified = card.updatedAt.epochSecond,
            type = type,
            queue = queue,
            due = due,
            interval = interval,
            factor = if (card.state == CardState.New) 0 else factor(card.stability, card.difficulty),
            reps = card.reps,
            lapses = card.lapses,
            left = learningLeft * 1000 + learningLeft,
            flags = if (card.flagged) 1 else 0,
            data = data.encode(desiredRetention),
        )
    }

    /** An SM-2 ease (permille) matching the FSRS state, for Anki users on SM-2. */
    private fun factor(stability: Double?, difficulty: Double?): Int {
        if (stability == null || difficulty == null) return DEFAULT_FACTOR
        return Math.round(fsrs.sm2EaseFactor(FsrsMemoryState(stability, difficulty)) * 1000).toInt()
    }

    private companion object {
        const val DAY_SECONDS = 86_400L
        const val DEFAULT_FACTOR = 2500
        const val QUEUE_SUSPENDED = -1
        const val REVLOG_LEARN = 0
        const val REVLOG_REVIEW = 1
        const val REVLOG_RELEARN = 2

        fun claim(used: MutableSet<Long>, preferred: Long): Long {
            var id = preferred.coerceAtLeast(1)
            while (!used.add(id)) id++
            return id
        }
    }
}
