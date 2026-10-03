package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.data.repository.OfflineCardRepository
import com.yahyafati.mnemo.core.data.repository.OfflineDeckRepository
import com.yahyafati.mnemo.core.data.repository.OfflineReviewRepository
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.RoomTransactionRunner
import com.yahyafati.mnemo.core.database.sync.SYNCED_TABLES
import com.yahyafati.mnemo.core.database.sync.SyncRows
import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.model.ReviewLog
import com.yahyafati.mnemo.core.model.UserSettings
import com.yahyafati.mnemo.core.scheduler.Fsrs
import com.yahyafati.mnemo.core.scheduler.FsrsCard
import com.yahyafati.mnemo.core.scheduler.FsrsParameters
import com.yahyafati.mnemo.core.scheduler.FsrsRating
import com.yahyafati.mnemo.core.scheduler.FsrsState
import com.yahyafati.mnemo.core.sync.SyncRemote
import com.yahyafati.mnemo.core.sync.SyncStore
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.inMemoryDatabase
import com.yahyafati.mnemo.core.testing.repository.FakeUserSettingsRepository
import kotlinx.coroutines.Dispatchers
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/**
 * FSRS for the merge tests: the same mapping as `StudyScheduler` in `:core:domain` (which `:core:data` can't see), so
 * a replay here computes what a device that answered the cards one after the other would have.
 */
internal class TestScheduler(settings: UserSettings = UserSettings()) {
    private val fsrs = Fsrs(
        FsrsParameters(
            desiredRetention = settings.desiredRetention,
            learningSteps = settings.learningSteps,
            relearningSteps = settings.relearningSteps,
        ),
    )

    fun answer(card: Card, rating: Rating, reviewedAt: Instant, durationMs: Long = 2_000): Pair<Card, ReviewLog> {
        val next = fsrs.review(card.toFsrs(), FsrsRating.entries.first { it.value == rating.value }, reviewedAt, Random(card.id.hashCode().toLong() * 31 + card.reps))
        val state = when (next.state) {
            FsrsState.Learning -> CardState.Learning
            FsrsState.Review -> CardState.Review
            FsrsState.Relearning -> CardState.Relearning
        }
        val updated = card.copy(
            state = state, due = next.due, stability = next.stability, difficulty = next.difficulty, step = next.step,
            lastReview = reviewedAt, reps = card.reps + 1,
            lapses = card.lapses + if (card.state == CardState.Review && rating == Rating.Again) 1 else 0,
            updatedAt = reviewedAt,
        )
        val log = ReviewLog(
            id = UUID.randomUUID().toString(), cardId = card.id, rating = rating, stateBefore = card.state, reviewedAt = reviewedAt,
            elapsedDays = 0, scheduledDays = 0, durationMs = durationMs, stabilityAfter = checkNotNull(next.stability),
            difficultyAfter = checkNotNull(next.difficulty), stateAfter = updated.state, stepAfter = updated.step,
            dueAfter = updated.due, repsAfter = updated.reps, lapsesAfter = updated.lapses,
        )
        return updated to log
    }

    private fun Card.toFsrs(): FsrsCard = when (state) {
        CardState.New -> FsrsCard(due = due)
        CardState.Learning -> FsrsCard(due, FsrsState.Learning, step ?: 0, stability, difficulty, lastReview)
        CardState.Review -> FsrsCard(due, FsrsState.Review, null, stability, difficulty, lastReview)
        CardState.Relearning -> FsrsCard(due, FsrsState.Relearning, step ?: 0, stability, difficulty, lastReview)
    }
}

internal object TestReplayer : ScheduleReplayer {
    override fun answerer(settings: UserSettings): CardAnswerer {
        val scheduler = TestScheduler(settings)
        return CardAnswerer { card, rating, reviewedAt -> scheduler.answer(card, rating, reviewedAt).first }
    }
}

internal class InMemoryMediaFiles : SyncMediaFiles {
    val files = HashMap<String, ByteArray>()

    override fun exists(hash: String) = hash in files

    override fun read(hash: String) = files[hash]

    override fun write(hash: String, bytes: ByteArray) {
        files[hash] = bytes
    }
}

/** One answer given on a device, with what Undo needs. */
internal class Answered(val previous: Card, val log: ReviewLog)

/**
 * A device in a merge test: its own database, repositories and clock (which a test may set wrong), syncing through a
 * shared [store]. Sync is on, as it is for a user who turned it on.
 */
internal class SyncDevice(
    val name: String,
    private val store: SyncStore,
    val clock: TestClock = TestClock(Instant.parse("2026-03-01T09:00:00Z")),
    val replayer: ScheduleReplayer = TestReplayer,
    val settings: FakeUserSettingsRepository = FakeUserSettingsRepository(),
) {
    val db: MnemoDatabase = inMemoryDatabase()
    val media = InMemoryMediaFiles()
    private val transaction = RoomTransactionRunner(db)
    val decks = OfflineDeckRepository(db.deckDao(), db.noteDao(), db.cardDao(), transaction, clock, Dispatchers.Unconfined)
    val cards = OfflineCardRepository(db.noteDao(), db.cardDao(), db.deckDao(), transaction, clock)
    val reviews = OfflineReviewRepository(db.cardDao(), db.reviewLogDao(), transaction, clock)
    val engine = SyncEngine(db, transaction, SyncClock(db.syncDao(), transaction, clock), clock, media, settings, replayer, Dispatchers.Unconfined)
    private val scheduler = TestScheduler()

    suspend fun turnOn() = db.syncDao().setEnabled(true)

    suspend fun deviceId(): String = db.syncDao().getState()!!.deviceId

    suspend fun sync(remote: SyncRemote, announceSettings: Boolean = false): SyncReport =
        engine.sync(remote, DeviceDescription(name, "test", "1"), announceSettings)

    suspend fun deck(path: String): String = decks.saveDeck(path)

    /** A Basic note; returns the note id and its card id. */
    suspend fun note(deck: String, front: String, back: String): Pair<String, String> {
        val note = cards.addNote(deck, NoteKind.Basic, listOf(front, back), emptyList())
        return note.id to db.cardDao().getCardsForNote(note.id).single().id
    }

    suspend fun answer(cardId: String, rating: Rating): Answered {
        val card = checkNotNull(cards.getCard(cardId)) { "$name has no card $cardId" }
        val (updated, log) = scheduler.answer(card, rating, clock.now())
        reviews.recordAnswer(updated, log)
        return Answered(card, log)
    }

    suspend fun undo(answered: Answered) = reviews.undoAnswer(answered.previous, answered.log.id)

    suspend fun liveLogs(cardId: String): List<String> = db.reviewLogDao().getLiveForCard(cardId).map { it.id }

    /**
     * Every synced row and the scheduling settings, as text, for comparing devices. A write that changes nothing but
     * `updatedAt` (saving a deck under the name it has) is not a change to sync, so a test that does such writes at random
     * leaves `updatedAt` out.
     */
    suspend fun dump(withUpdatedAt: Boolean = true): List<String> {
        val rows = SyncRows(db)
        return buildList {
            for (table in SYNCED_TABLES) {
                val key = table.keyColumns.joinToString(" || '/' || ")
                for (id in db.queryStrings("SELECT $key FROM ${table.name} ORDER BY $key")) add("${table.name}/$id ${rows.get(table.name, id)!!.filterKeys { withUpdatedAt || it != "updatedAt" }}")
            }
            val s = settings.settings.value
            add("settings ${s.desiredRetention} ${s.newCardsPerDay} ${s.reviewsPerDay} ${s.learningSteps} ${s.relearningSteps} ${s.fsrsWeights}")
        }
    }

    fun close() = db.close()

    companion object {
        fun minutes(m: Long): Duration = Duration.ofMinutes(m)
    }
}
