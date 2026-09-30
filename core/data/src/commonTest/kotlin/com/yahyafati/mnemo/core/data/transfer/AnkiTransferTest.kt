package com.yahyafati.mnemo.core.data.transfer

import com.yahyafati.mnemo.core.data.mapper.toModel
import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.MediaRef
import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.ReviewLog
import com.yahyafati.mnemo.core.testing.PlatformTest
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** Anki import and export against a real database (ROADMAP Phase 2 exit criteria). */
class AnkiTransferTest : PlatformTest() {
    private fun collection() = TransferTestCollection(tmp.newFolder())

    /** A card without what differs between collections: ids, deck ids, and when it was written. */
    private fun Card.normalized() = copy(id = "", deckId = "", updatedAt = Instant.EPOCH)

    private data class Snapshot(val note: Note, val cards: List<Card>, val reviews: List<ReviewLog>)

    /** Everything in [c], keyed by note guid. */
    private suspend fun contents(c: TransferTestCollection): Map<String?, Snapshot> {
        val notes = c.db.noteDao().getPage(0, 10_000).map { it.note.toModel() }
        val cards = c.db.cardDao().getCardsForNotes(notes.map { it.id }).map { it.toModel() }
        val reviews = c.db.reviewLogDao().getForCards(cards.map { it.id }).map { it.toModel() }
        return notes.associate { note ->
            val noteCards = cards.filter { it.noteId == note.id }.sortedBy { it.templateOrd }
            note.guid to Snapshot(note, noteCards, reviews.filter { r -> noteCards.any { it.id == r.cardId } }.sortedBy { it.reviewedAt })
        }
    }

    @Test
    fun importsARealAnkiDeck() = runTest {
        collection().use { c ->
            val summary = c.importer.import(TransferTestCollection.fixture("modern.apkg"), tmp.newFolder())
            assertEquals(7, summary.notes)
            assertEquals(9, summary.cards)
            assertEquals(4, summary.reviews)
            assertEquals(2, summary.media)
            assertEquals(1, summary.skippedCards)
            assertEquals(0, summary.duplicateNotes)

            val paths = c.decks.observeDeckSummaries().first().map { it.path }.toSet()
            assertEquals(setOf("Biology", "Languages", "Languages::Japanese"), paths)
            // Media is stored by hash and referenced from the note.
            val stored = c.media.getAll()
            assertEquals(setOf("cell.png", "konnichiwa.mp3"), stored.map { it.name }.toSet())
            assertTrue(stored.all { c.media.file(it.id).exists() })
            val image = contents(c).values.single { it.note.fields[0].startsWith("A cell:") }
            val hash = stored.single { it.name == "cell.png" }.id
            assertEquals("A cell:\n![](${MediaRef.of(hash)})", image.note.fields[0])
            // Study state survives: the flagged review card and the suspended reverse.
            val cat = contents(c).values.single { it.note.fields[0] == "猫" }
            assertEquals(listOf(CardState.Review, CardState.New), cat.cards.map { it.state })
            assertTrue(cat.cards[0].flagged && cat.cards[1].suspended)
        }
    }

    @Test
    fun importingTwiceSkipsDuplicates() = runTest {
        collection().use { c ->
            c.importer.import(TransferTestCollection.fixture("legacy.apkg"), tmp.newFolder())
            val again = c.importer.import(TransferTestCollection.fixture("modern.apkg"), tmp.newFolder())
            assertEquals(0, again.notes)
            assertEquals(7, again.duplicateNotes)
            assertEquals(0, again.decks)
            assertEquals(7, c.db.noteDao().count())
        }
    }

    @Test
    fun exportThenImportIntoAFreshCollectionLosesNothing() = runTest {
        val bytes = ByteArrayOutputStream()
        val original = collection().use { a ->
            a.importer.import(TransferTestCollection.fixture("modern.apkg"), tmp.newFolder())
            a.exporter.export(deckId = null, output = bytes, workDir = tmp.newFolder())
            contents(a) to a.media.getAll().map { it.id to it.name }.toSet()
        }
        val file = File(tmp.root, "export.apkg").apply { writeBytes(bytes.toByteArray()) }
        collection().use { b ->
            val summary = b.importer.import(file, tmp.newFolder())
            assertEquals(0, summary.skippedCards)
            val restored = contents(b)
            assertEquals(original.first.keys, restored.keys)
            for ((guid, before) in original.first) {
                val after = restored.getValue(guid)
                // The deck ids differ between collections; everything else is the same.
                assertEquals(before.note.copy(deckId = ""), after.note.copy(deckId = ""))
                assertEquals(before.cards.map { it.normalized() }, after.cards.map { it.normalized() })
                assertEquals(before.reviews.map { it.copy(id = "", cardId = "") }, after.reviews.map { it.copy(id = "", cardId = "") })
            }
            assertEquals(original.second, b.media.getAll().map { it.id to it.name }.toSet())
            assertEquals(
                setOf("Biology", "Languages", "Languages::Japanese"),
                b.decks.observeDeckSummaries().first().map { it.path }.toSet(),
            )
        }
    }

    @Test
    fun deckExportHoldsOnlyThatDeck() = runTest {
        collection().use { a ->
            a.importer.import(TransferTestCollection.fixture("modern.apkg"), tmp.newFolder())
            val japanese = a.decks.observeDeckSummaries().first().single { it.path == "Languages" }.deck.id
            val bytes = ByteArrayOutputStream()
            a.exporter.export(japanese, bytes, tmp.newFolder())
            val file = File(tmp.root, "deck.apkg").apply { writeBytes(bytes.toByteArray()) }
            collection().use { b ->
                val summary = b.importer.import(file, tmp.newFolder())
                assertEquals(2, summary.notes) // 猫 and こんにちは
                assertEquals(listOf("konnichiwa.mp3"), b.media.getAll().map { it.name })
            }
        }
    }

    @Test
    fun jsonExportHasEverything() = runTest {
        collection().use { c ->
            c.importer.import(TransferTestCollection.fixture("modern.apkg"), tmp.newFolder())
            val bytes = ByteArrayOutputStream()
            c.json.export(bytes)
            val doc = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
            assertEquals("mnemo-json", doc["format"]!!.jsonPrimitive.content)
            val notes = doc["notes"]!!.jsonArray
            assertEquals(7, notes.size)
            assertEquals(9, notes.sumOf { it.jsonObject["cards"]!!.jsonArray.size })
            assertEquals(4, notes.sumOf { n -> n.jsonObject["cards"]!!.jsonArray.sumOf { it.jsonObject["reviews"]!!.jsonArray.size } })
            assertEquals(2, doc["media"]!!.jsonArray.size)
        }
    }
}
