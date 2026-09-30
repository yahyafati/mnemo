package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.NoteType
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DuplicateFinderTest {
    private var created = 0L

    private fun note(front: String, back: String = "a") =
        Note("n${created}", "d", NoteType.Basic.id, listOf(front, back), createdAt = Instant.ofEpochSecond(created++), updatedAt = Instant.EPOCH)

    @Test
    fun sameQuestionIgnoringMarkupIsADuplicate() {
        val a = note("What do **mitochondria** make?")
        val b = note("what do mitochondria make")
        val c = note("{{c1::Mitochondria}} make ATP")
        val groups = DuplicateFinder.find(listOf(a, b, c, note("Where is DNA stored?")))
        assertEquals(listOf(listOf(a, b)), groups.map { it.notes })
        assertEquals(1.0, groups.single().similarity)
    }

    @Test
    fun nearlyTheSameWordsAreDuplicatesButShortQuestionsAreNot() {
        val a = note("Which enzyme unwinds the DNA double helix during replication?")
        val b = note("During replication, which enzyme unwinds the DNA double helix")
        val c = note("Which enzyme joins Okazaki fragments during replication?")
        val groups = DuplicateFinder.find(listOf(a, b, c, note("What is ATP?"), note("What is ADP?")))
        assertEquals(listOf(listOf(a, b)), groups.map { it.notes })
        assertTrue(groups.single().similarity >= DuplicateFinder.NEAR)
    }

    @Test
    fun largeDecksAreFast() {
        val random = kotlin.random.Random(7)
        val vocabulary = List(3_000) { i -> "w" + i.toString(36) + "x" }
        val fronts = List(5_000) { List(8) { vocabulary[random.nextInt(vocabulary.size)] }.joinToString(" ") }
        val notes = fronts.map { note(it) } + note(fronts.first().uppercase() + "?")
        val started = System.nanoTime()
        val groups = DuplicateFinder.find(notes)
        assertTrue((System.nanoTime() - started) / 1_000_000 < 2_000, "took too long")
        assertEquals(1, groups.size)
        assertEquals(2, groups.single().notes.size)
    }
}
