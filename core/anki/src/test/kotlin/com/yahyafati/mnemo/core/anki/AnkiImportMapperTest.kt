package com.yahyafati.mnemo.core.anki

import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteSource
import com.yahyafati.mnemo.core.model.NoteType
import com.yahyafati.mnemo.core.model.Rating
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnkiImportMapperTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val now = Instant.now()

    /** Every note of a fixture, converted, keyed by its first field. */
    private fun importAll(name: String): Map<String, ImportedNote> = Fixtures.open(name, tmp).use { pkg ->
        val mapper = AnkiImportMapper(pkg.collectionCreated, pkg.notetypes, now, learningSteps = 2, relearningSteps = 1)
        val refs = Fixtures.mediaRefs(pkg)
        val decks = pkg.decks.associate { it.id to it.name }
        pkg.notes().flatten().mapNotNull { note ->
            val cards = pkg.cardsForNotes(listOf(note.id))
            val revlog = pkg.revlogForCards(cards.map { it.id }).groupBy { it.cardId }
            mapper.map(note, cards, revlog, UUID.randomUUID().toString(), { decks.getValue(it) }, refs::get)
        }.associateBy { it.note.fields[0] }
    }

    @Test
    fun noteTypesBecomeMnemoKinds() {
        for (fixture in listOf(Fixtures.MODERN, Fixtures.LEGACY)) {
            val notes = importAll(fixture)
            assertEquals(7, notes.size, fixture)

            val basic = notes.getValue("What do **mitochondria** make?")
            assertEquals(NoteType.Basic.id, basic.note.noteTypeId, fixture)
            assertEquals("*ATP*, via oxidative phosphorylation\n2 < 3 & done", basic.note.fields[1], fixture)
            assertEquals(NoteSource.Import, basic.note.source, fixture)
            assertEquals("Biology", basic.note.deckId, fixture)
            assertNotNull(basic.note.guid, fixture)
            // "marked" becomes a star on the cards rather than a tag.
            assertEquals(listOf("bio::cells"), basic.note.tags, fixture)
            assertTrue(basic.cards.single().starred, fixture)

            val reversed = notes.getValue("猫")
            assertEquals(NoteType.Reversed.id, reversed.note.noteTypeId, fixture)
            assertEquals("Languages::Japanese", reversed.note.deckId, fixture)
            assertEquals(listOf(0, 1), reversed.cards.map { it.templateOrd }, fixture)

            val cloze = notes.getValue("{{c1::Mitochondria}} make {{c2::ATP::energy molecule}}.")
            assertEquals(NoteType.Cloze.id, cloze.note.noteTypeId, fixture)
            assertEquals("Extra **context**", cloze.note.fields[1], fixture)

            // A custom type: the first template's fields, and its second template's cards skipped.
            val vocab = notes.getValue("こんにちは")
            assertEquals(NoteType.Basic.id, vocab.note.noteTypeId, fixture)
            val sound = vocab.note.fields[1]
            assertTrue(sound.matches(Regex("""konnichiwa \[sound:media:[0-9a-f]{64}]\n\nhello""")), sound)
            assertEquals(1, vocab.skippedCards, fixture)

            val image = notes.getValue(notes.keys.single { it.startsWith("A cell:") })
            assertTrue(image.note.fields[0].matches(Regex("""A cell:\n!\[]\(media:[0-9a-f]{64}\)""")), fixture)
            assertEquals("\\(E = mc^2\\)", notes.getValue("Mass-energy equivalence").note.fields[1], fixture)
            assertEquals("1. Prophase\n2. Metaphase\n\n- `x` stays", notes.getValue("Steps of mitosis").note.fields[1], fixture)
        }
    }

    @Test
    fun schedulingAndHistory() {
        val notes = importAll(Fixtures.MODERN)

        // SM-2 review card with a log: replayed through FSRS, history kept.
        val replayed = notes.getValue("What do **mitochondria** make?")
        val card = replayed.cards.single()
        assertEquals(CardState.Review, card.state)
        assertNotNull(card.stability)
        assertEquals(listOf(Rating.Good, Rating.Good, Rating.Good), replayed.reviews.map { it.rating })
        assertEquals(listOf(CardState.New, CardState.Review, CardState.Review), replayed.reviews.map { it.stateBefore })
        assertEquals(listOf(0, 1, 4), replayed.reviews.map { it.elapsedDays })
        assertEquals(listOf(0, 3, 8), replayed.reviews.map { it.scheduledDays })
        assertEquals(card.stability, replayed.reviews.last().stabilityAfter)
        assertEquals(replayed.reviews.last().reviewedAt, card.lastReview)

        // SM-2 review card without a log: estimated from interval and ease; flag kept.
        val (forward, backward) = notes.getValue("猫").cards
        assertEquals(CardState.Review, forward.state)
        assertEquals(30.0, forward.stability!!, 1e-9)
        assertEquals(6.747611004245584, forward.difficulty!!, 1e-9)
        assertEquals(forward.due.minus(Duration.ofDays(30)), forward.lastReview)
        assertTrue(forward.flagged)
        assertTrue(backward.suspended)
        assertEquals(CardState.New, backward.state)
        assertNull(backward.stability)

        // FSRS card: memory state straight from Anki's card data. Learning card: due is a timestamp.
        val (c1, c2) = notes.getValue("{{c1::Mitochondria}} make {{c2::ATP::energy molecule}}.").cards
        assertEquals(5.1, c1.stability)
        assertEquals(3.2, c1.difficulty)
        assertEquals(CardState.Learning, c2.state)
        assertEquals(1, c2.step) // one of two steps left
        val learningDue = Fixtures.open(Fixtures.MODERN, tmp).use { pkg ->
            pkg.cardsForNotes(pkg.notes().flatten().map { it.id }.toList()).single { it.type == 1 }.due
        }
        assertEquals(Instant.ofEpochSecond(learningDue), c2.due)
    }

    @Test
    fun cardsWithoutSchedulingAreNew() {
        val notes = importAll(Fixtures.NO_SCHEDULING)
        assertTrue(notes.values.flatMap { it.cards }.all { it.state == CardState.New && !it.suspended })
        assertTrue(notes.values.all { it.reviews.isEmpty() })
    }

    /** Anki 26.09's own `compute_memory_state` for these histories (noon-aligned, so day counts agree). */
    @Test
    fun replayMatchesAnki() {
        val day = 86_400L
        val cases = listOf(
            listOf(Triple(0L, 3, 0), Triple(600L, 3, 0), Triple(day, 3, 1), Triple(5 * day, 1, 1), Triple(5 * day + 900, 3, 2), Triple(9 * day, 4, 1))
                to (10.155006408691406 to 6.48682975769043),
            listOf(Triple(0L, 1, 0), Triple(60L, 3, 0), Triple(day, 2, 1), Triple(4 * day, 3, 1), Triple(12 * day, 3, 1))
                to (14.98178482055664 to 7.5720601081848145),
            listOf(Triple(0L, 4, 0), Triple(10 * day, 3, 1)) to (44.09696578979492 to 1.0),
        )
        val noon = Instant.parse("2026-01-10T12:00:00Z")
        val type = AnkiNotetype(1, "Basic", false, listOf("Front", "Back"), listOf(AnkiTemplate(0, "Card 1", "{{Front}}", "{{Back}}")))
        val mapper = AnkiImportMapper(noon, listOf(type), noon.plus(Duration.ofDays(60)), learningSteps = 2, relearningSteps = 1)
        for ((rows, expected) in cases) {
            val note = AnkiNote(noon.toEpochMilli(), "g", 1, noon.epochSecond, emptyList(), listOf("q", "a"))
            val card = AnkiCard(7, note.id, 1, 0, 0, type = 2, queue = 2, due = 20, interval = 10, factor = 2500, reps = rows.size, lapses = 0, left = 0)
            val revlog = rows.map { (offset, ease, kind) ->
                AnkiRevlog(noon.plusSeconds(offset).toEpochMilli(), 7, ease, 1, 0, 0, 5000, kind)
            }
            val imported = mapper.map(note, listOf(card), mapOf(7L to revlog), "n", { "d" }, { null })!!.cards.single()
            assertEquals(expected.first, imported.stability!!, expected.first * 1e-4, "stability for $rows")
            assertEquals(expected.second, imported.difficulty!!, 1e-4, "difficulty for $rows")
        }
    }

    @Test
    fun resetStartsTheHistoryOver() {
        val t0 = Instant.parse("2026-01-10T12:00:00Z")
        val type = AnkiNotetype(1, "Basic", false, listOf("Front", "Back"), listOf(AnkiTemplate(0, "Card 1", "{{Front}}", "{{Back}}")))
        val mapper = AnkiImportMapper(t0, listOf(type), t0.plus(Duration.ofDays(30)), 2, 1)
        val note = AnkiNote(t0.toEpochMilli(), "g", 1, t0.epochSecond, emptyList(), listOf("q", "a"))
        val card = AnkiCard(7, note.id, 1, 0, 0, type = 1, queue = 1, due = t0.epochSecond + 86_400 * 3, interval = 0, factor = 0, reps = 1, lapses = 0, left = 2002)
        val revlog = listOf(
            AnkiRevlog(t0.toEpochMilli(), 7, 3, 1, 0, 2500, 1000, 0),
            AnkiRevlog(t0.plus(Duration.ofDays(1)).toEpochMilli(), 7, 0, 0, 0, 0, 0, 4), // forget
            AnkiRevlog(t0.plus(Duration.ofDays(2)).toEpochMilli(), 7, 1, 0, 0, 0, 1000, 0),
        )
        val imported = mapper.map(note, listOf(card), mapOf(7L to revlog), "n", { "d" }, { null })!!
        assertEquals(listOf(Rating.Again), imported.reviews.map { it.rating })
        assertEquals(CardState.New, imported.reviews.single().stateBefore)
        assertEquals(0, imported.cards.single().step)
    }

    @Test
    fun emptyQuestionsAndUnknownTypesAreSkipped() {
        val t0 = Instant.parse("2026-01-10T12:00:00Z")
        val type = AnkiNotetype(1, "Basic", false, listOf("Front", "Back"), listOf(AnkiTemplate(0, "Card 1", "{{Front}}", "{{Back}}")))
        val mapper = AnkiImportMapper(t0, listOf(type), t0, 2, 1)
        val card = AnkiCard(7, 1, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0)
        val empty = AnkiNote(1, "g", 1, 0, emptyList(), listOf("<br>", "a"))
        assertNull(mapper.map(empty, listOf(card), emptyMap(), "n", { "d" }, { null }))
        assertNull(mapper.map(empty.copy(notetypeId = 99, fields = listOf("q", "a")), listOf(card), emptyMap(), "n", { "d" }, { null }))
        // A cloze note gets a card for each deletion even if the package lacks one.
        val clozeType = AnkiNotetype(2, "Cloze", true, listOf("Text", "Extra"), listOf(AnkiTemplate(0, "Cloze", "{{cloze:Text}}", "{{cloze:Text}}{{Extra}}")))
        val clozeMapper = AnkiImportMapper(t0, listOf(clozeType), t0, 2, 1)
        val note = AnkiNote(1, "g", 2, 0, emptyList(), listOf("{{c1::a}} {{c2::b}}", ""))
        val imported = clozeMapper.map(note, listOf(card), emptyMap(), "n", { "d" }, { null })!!
        assertEquals(listOf(0, 1), imported.cards.map { it.templateOrd })
        assertEquals(NoteKind.Cloze, NoteType.byId(imported.note.noteTypeId)?.kind)
    }

    @Test
    fun typeInTemplatesBecomeTypeInNotes() {
        val t0 = Instant.parse("2026-01-10T12:00:00Z")
        // Anki's stock "Basic (type in the answer)".
        val type = AnkiNotetype(
            1, "Basic (type in the answer)", false, listOf("Front", "Back"),
            listOf(AnkiTemplate(0, "Card 1", "{{Front}}\n\n{{type:Back}}", "{{Front}}\n\n<hr id=answer>\n\n{{type:Back}}")),
        )
        val mapper = AnkiImportMapper(t0, listOf(type), t0, 2, 1)
        val card = AnkiCard(7, 1, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0)
        val imported = mapper.map(AnkiNote(1, "g", 1, 0, emptyList(), listOf("Capital of Peru?", "Lima")), listOf(card), emptyMap(), "n", { "d" }, { null })!!
        assertEquals(NoteKind.TypeIn, NoteType.byId(imported.note.noteTypeId)?.kind)
        assertEquals(listOf("Capital of Peru?", "Lima"), imported.note.fields)
    }
}
