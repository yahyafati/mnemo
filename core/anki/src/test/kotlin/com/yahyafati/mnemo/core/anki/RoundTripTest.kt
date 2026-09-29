package com.yahyafati.mnemo.core.anki

import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.Deck
import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteType
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.model.ReviewLog
import com.yahyafati.mnemo.core.model.cardOrdinals
import com.yahyafati.mnemo.core.scheduler.Fsrs
import com.yahyafati.mnemo.core.scheduler.FsrsCard
import com.yahyafati.mnemo.core.scheduler.FsrsParameters
import com.yahyafati.mnemo.core.scheduler.FsrsRating
import com.yahyafati.mnemo.core.scheduler.FsrsState
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Mnemo → `.apkg` → Mnemo loses nothing (ROADMAP Phase 2 exit criterion). */
class RoundTripTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val start = Instant.parse("2026-02-01T08:30:00Z")
    private val now = start.plus(Duration.ofDays(40))
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)
    private val pngHash = Fixtures.sha256(png)

    private val deck = Deck("deck-1", "Japanese", parentId = "deck-0", category = "Language", starred = true, description = "Kana", createdAt = start, updatedAt = start)
    private val parent = Deck("deck-0", "Languages", createdAt = start, updatedAt = start)

    private data class Source(val note: Note, val kind: NoteKind, val cards: List<Card>, val reviews: List<ReviewLog>)

    /**
     * A note whose cards were really studied with FSRS, so the history is self-consistent. Each
     * note is studied at a different [offsetMinutes]: Anki review ids are timestamps and must be
     * unique, so answers in the same millisecond would be nudged apart.
     */
    private fun studied(
        kind: NoteKind,
        fields: List<String>,
        tags: List<String>,
        offsetMinutes: Long,
        answers: Map<Int, List<Pair<Long, Rating>>>,
    ): Source {
        val note = Note(UUID.randomUUID().toString(), deck.id, NoteType.builtIn(kind).id, fields, tags, createdAt = start, updatedAt = start)
        val fsrs = Fsrs(FsrsParameters(learningSteps = listOf(Duration.ofMinutes(1), Duration.ofMinutes(10))))
        val cards = mutableListOf<Card>()
        val reviews = mutableListOf<ReviewLog>()
        for (ord in kind.cardOrdinals(fields)) {
            val created = start.plusMillis(ord.toLong())
            var card = Card(UUID.randomUUID().toString(), note.id, deck.id, ord, due = created, createdAt = created, updatedAt = start)
            var fsrsCard: FsrsCard? = null
            for ((minutes, rating) in answers[ord].orEmpty()) {
                val at = start.plus(Duration.ofMinutes(offsetMinutes + minutes))
                val next = fsrs.review(fsrsCard ?: FsrsCard(due = at), FsrsRating.entries.first { it.value == rating.value }, at)
                reviews += ReviewLog(
                    UUID.randomUUID().toString(), card.id, rating, card.state, at,
                    elapsedDays = card.lastReview?.let { AnkiImportMapper.wholeDays(it, at) } ?: 0,
                    scheduledDays = AnkiImportMapper.wholeDays(at, next.due),
                    durationMs = 4_000 + minutes, stabilityAfter = next.stability!!, difficultyAfter = next.difficulty!!,
                )
                card = card.copy(
                    state = when (next.state) {
                        FsrsState.Learning -> CardState.Learning
                        FsrsState.Review -> CardState.Review
                        FsrsState.Relearning -> CardState.Relearning
                    },
                    due = next.due, stability = next.stability, difficulty = next.difficulty, step = next.step,
                    lastReview = at, reps = card.reps + 1,
                    lapses = card.lapses + if (card.state == CardState.Review && rating == Rating.Again) 1 else 0,
                )
                fsrsCard = next
            }
            cards += card
        }
        return Source(note, kind, cards, reviews)
    }

    @Test
    fun exportThenImportRestoresEverything() {
        val day = 24 * 60L
        val sources = listOf(
            studied(
                NoteKind.Basic, listOf("What do **mitochondria** make?\n2 * 3 and a\\*b", "ATP ![cell](media:$pngHash)"), listOf("bio::cells"), 0,
                mapOf(0 to listOf(0L to Rating.Good, 10L to Rating.Good, 3 * day to Rating.Good, 12 * day to Rating.Hard)),
            ),
            studied(NoteKind.Reversed, listOf("猫", "cat"), listOf("jp"), 1, mapOf(0 to listOf(0L to Rating.Again, 2L to Rating.Good))),
            studied(
                NoteKind.Cloze, listOf("{{c1::\\(E=mc^2\\)}} and {{c2::**ATP**::energy}}", "> quote\n\n- a\n- b"), emptyList(), 2,
                mapOf(1 to listOf(0L to Rating.Easy, 20 * day to Rating.Again, 20 * day + 11 to Rating.Good)),
            ),
        ).let { list ->
            // Star, flag and suspend a few cards.
            list.mapIndexed { i, s ->
                s.copy(
                    cards = s.cards.map { c ->
                        when {
                            i == 0 -> c.copy(starred = true)
                            i == 1 && c.templateOrd == 1 -> c.copy(suspended = true, flagged = true)
                            else -> c
                        }
                    },
                )
            }
        }

        // Export.
        val output = tmp.newFile("export.apkg")
        val mapper = AnkiExportMapper(start.minus(Duration.ofHours(4)), desiredRetention = 0.9, learningSteps = 2, relearningSteps = 1)
        AnkiPackageWriter(Fixtures.driver, tmp.newFolder()).use { writer ->
            writer.begin(mapper.collectionCreated, listOf(mapper.deck(parent, "Languages"), mapper.deck(deck, "Languages::Japanese")), mapper.notetypes)
            for (s in sources) {
                val exported = mapper.note(s.note, s.kind, s.cards, s.reviews) { hash -> if (hash == pngHash) "cell.png" else null }
                writer.addNotes(listOf(exported.note))
                writer.addCards(exported.cards)
                writer.addRevlog(exported.revlog)
            }
            writer.addMedia("cell.png") { png.inputStream() }
            output.outputStream().use { writer.finish(it) }
        }

        // Import.
        val imported = ApkgReader(Fixtures.driver).open(output, tmp.newFolder()).use { pkg ->
            assertEquals(AnkiFormat.Legacy1, pkg.format)
            val japanese = pkg.decks.single { it.name == "Languages::Japanese" }
            assertEquals("Kana", japanese.description)
            assertEquals("Language", japanese.mnemoCategory)
            assertTrue(japanese.mnemoStarred)
            val refs = Fixtures.mediaRefs(pkg)
            assertEquals(mapOf("cell.png" to "media:$pngHash"), refs)
            val importer = AnkiImportMapper(pkg.collectionCreated, pkg.notetypes, now, learningSteps = 2, relearningSteps = 1)
            pkg.notes().flatten().map { note ->
                val cards = pkg.cardsForNotes(listOf(note.id))
                val revlog = pkg.revlogForCards(cards.map { it.id }).groupBy { it.cardId }
                importer.map(note, cards, revlog, importer.mnemoIdOf(note)!!, { deck.id }, refs::get)!!
            }.toList()
        }

        assertEquals(sources.size, imported.size)
        for (source in sources) {
            val back = imported.single { it.note.id == source.note.id }
            assertEquals(source.note, back.note.copy(source = source.note.source, guid = null))
            assertEquals(ankiGuidFor(source.note.id), back.note.guid)
            assertEquals(0, back.skippedCards)
            assertEquals(source.cards.size, back.cards.size)
            for ((original, restored) in source.cards.sortedBy { it.templateOrd }.zip(back.cards)) {
                assertEquals(original, restored.copy(id = original.id, updatedAt = original.updatedAt))
            }
            val cardIds = source.cards.associate { it.id to it.templateOrd }
            val restoredOrd = back.cards.associate { it.id to it.templateOrd }
            val originalReviews = source.reviews.sortedWith(compareBy({ cardIds[it.cardId] }, { it.reviewedAt }))
            val restoredReviews = back.reviews.sortedWith(compareBy({ restoredOrd[it.cardId] }, { it.reviewedAt }))
            assertEquals(originalReviews.size, restoredReviews.size)
            for ((original, restored) in originalReviews.zip(restoredReviews)) {
                assertEquals(original, restored.copy(id = original.id, cardId = original.cardId))
            }
        }
    }
}
