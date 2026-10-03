package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.sync.SyncRemote
import com.yahyafati.mnemo.core.sync.store.InMemorySyncStore
import com.yahyafati.mnemo.core.testing.PlatformTest
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The property the merge exists for (docs/sync/ROADMAP.md S3, working rule 3): two or three devices make random edits,
 * reviews, undos, deletes and deck creations while offline, sync in random orders, and once everyone has synced they
 * hold the same rows, with every review that was ever given still in the log.
 */
class SyncConvergenceTest : PlatformTest() {
    @Test
    fun twoDevicesConverge() = runTest(timeout = LIMIT) {
        for (seed in 1..SEEDS) converge(seed, count = 2, steps = 70)
        println("sync: $replays card schedules replayed during the two-device runs")
    }

    @Test
    fun threeDevicesConverge() = runTest(timeout = LIMIT) {
        for (seed in 100..100 + SEEDS) converge(seed, count = 3, steps = 90)
    }

    private suspend fun converge(seed: Int, count: Int, steps: Int) {
        val random = Random(seed)
        val store = InMemorySyncStore()
        SyncRemote.create(store, "collection", 0)
        val devices = List(count) { SyncDevice("device${'A' + it}", store, TestClockAt(random)) }
        // What each device can see of the location: files may reach a folder late and in any order.
        val views = devices.associateWith { InterceptingStore(store) }
        devices.forEach { it.turnOn() }
        val reviews = HashSet<String>()
        val lastAnswer = HashMap<String, Answered?>()

        try {
            devices.first().settings.setDesiredRetention(0.88)
            devices.first().sync(SyncRemote.open(views.getValue(devices.first())), announceSettings = true)
            repeat(steps) { step ->
                val device = devices.random(random)
                val label = "seed $seed step $step on ${device.name}"
                try {
                    act(device, random, reviews, lastAnswer, store, views.getValue(device))
                } catch (e: Throwable) {
                    throw AssertionError("$label failed: ${e.message}", e)
                }
            }
            // Everyone settles: sync in turn until nothing moves.
            views.values.forEach { it.hidden.clear() }
            var settled = false
            repeat(12) {
                if (!settled) settled = devices.map { it.sync(SyncRemote.open(views.getValue(it))) }.all { it.isQuiet }
            }
            assertTrue(settled, "seed $seed never settled")

            val first = devices.first().dump(withUpdatedAt = false)
            for (other in devices.drop(1)) {
                val second = other.dump(withUpdatedAt = false)
                val differences = (first - second.toSet()).map { "${devices.first().name}: $it" } + (second - first.toSet()).map { "${other.name}: $it" }
                assertTrue(differences.isEmpty(), "seed $seed: the devices differ:\n" + differences.take(6).joinToString("\n"))
            }
            for (device in devices) {
                val logs = device.db.queryStrings("SELECT id FROM review_logs").toSet()
                assertEquals(emptySet(), reviews - logs, "seed $seed: ${device.name} lost reviews")
                assertEquals(0, device.db.syncDao().countChanges(), "seed $seed: ${device.name} still has unsent changes")
            }
        } finally {
            devices.forEach { it.close() }
        }
    }

    private suspend fun act(
        device: SyncDevice,
        random: Random,
        reviews: MutableSet<String>,
        lastAnswer: MutableMap<String, Answered?>,
        store: InMemorySyncStore,
        view: InterceptingStore,
    ) {
        val liveDecks = device.db.queryStrings("SELECT id FROM decks WHERE deletedAt IS NULL")
        val liveNotes = device.db.queryStrings("SELECT id FROM notes WHERE deletedAt IS NULL")
        val liveCards = device.db.queryStrings("SELECT id FROM cards WHERE deletedAt IS NULL")
        device.clock.advanceBy(Duration.ofMinutes(random.nextLong(1, 90)))

        when (random.nextInt(100)) {
            in 0..7 -> device.deck(DECK_NAMES.random(random))
            in 8..24 -> if (liveDecks.isNotEmpty()) device.note(liveDecks.random(random), "q${random.nextInt(1000)}", "a${random.nextInt(1000)}")
            in 25..36 -> if (liveNotes.isNotEmpty()) {
                val id = liveNotes.random(random)
                val note = device.cards.getNote(id) ?: return
                val fields = note.fields.toMutableList()
                fields[random.nextInt(fields.size)] = "edit${random.nextInt(1000)}"
                device.cards.updateNote(id, note.deckId, fields, if (random.nextBoolean()) note.tags else listOf("t${random.nextInt(5)}"), note.hint)
            }
            in 37..39 -> if (liveNotes.size > 2) device.cards.deleteNote(liveNotes.random(random))
            in 40..41 -> if (liveDecks.size > 2) device.decks.deleteDeck(liveDecks.random(random))
            // A device can hold a note whose deck's file is still on its way (files arrive late and in any order), so no live deck is possible.
            in 42..44 -> if (liveNotes.isNotEmpty() && liveDecks.isNotEmpty()) {
                val note = device.cards.getNote(liveNotes.random(random)) ?: return
                device.cards.updateNote(note.id, liveDecks.random(random), note.fields, note.tags, note.hint)
            }
            in 45..66 -> if (liveCards.isNotEmpty()) {
                val answered = device.answer(liveCards.random(random), Rating.entries.random(random))
                reviews += answered.log.id
                lastAnswer[device.name] = answered
            }
            in 67..71 -> lastAnswer[device.name]?.let { answered ->
                val card = device.cards.getCard(answered.log.cardId)
                // Undo is only offered for the answer just given, while the card is as that answer left it.
                if (card != null && card.lastReview == answered.log.reviewedAt && answered.log.id in device.liveLogs(card.id)) {
                    device.undo(answered)
                    lastAnswer[device.name] = null
                }
            }
            in 72..75 -> if (liveCards.isNotEmpty()) device.cards.setFlagged(liveCards.random(random), random.nextBoolean())
            in 76..77 -> device.settings.setNewCardsPerDay(random.nextInt(5, 40))
            in 78..79 -> device.clock.advanceBy(Duration.ofHours(random.nextLong(-30, 30)))
            in 80..83 -> {
                // A file of another device is slow to arrive here.
                val others = store.list("devices/").filter { "changes" in it && "/${device.deviceId()}/" !in it && !it.contains("__${device.deviceId()}__") }
                if (others.isNotEmpty()) view.hidden += others.random(random)
            }
            in 84..85 -> view.hidden.clear()
            else -> device.sync(SyncRemote.open(view)).also { replays += it.replayedCards }
        }
    }

    private fun TestClockAt(random: Random) =
        com.yahyafati.mnemo.core.testing.TestClock(Instant.parse("2026-03-01T09:00:00Z").plusSeconds(random.nextLong(-5_000, 5_000)))

    private var replays = 0

    private companion object {
        // runTest stops after a minute by default: `MNEMO_SYNC_SEEDS=1500` needs more.
        val LIMIT = 30.minutes
        val SEEDS = System.getenv("MNEMO_SYNC_SEEDS")?.toInt() ?: 14
        val DECK_NAMES = listOf("Spanish", "spanish", "French", "Math", "Lang::Spanish", "Lang::spanish", "Lang::French")
    }
}
