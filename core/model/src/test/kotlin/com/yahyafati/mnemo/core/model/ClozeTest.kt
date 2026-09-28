package com.yahyafati.mnemo.core.model

import com.yahyafati.mnemo.core.model.Cloze.Segment
import kotlin.test.Test
import kotlin.test.assertEquals

class ClozeTest {
    @Test
    fun `parses deletions with and without hints`() {
        val segments = Cloze.parse("The {{c1::amygdala}} handles {{c2::fear::emotion}}.")
        assertEquals(
            listOf(
                Segment.Text("The "),
                Segment.Deletion(1, "amygdala", null),
                Segment.Text(" handles "),
                Segment.Deletion(2, "fear", "emotion"),
                Segment.Text("."),
            ),
            segments,
        )
    }

    @Test
    fun `ordinals are distinct and sorted`() {
        assertEquals(listOf(1, 3), Cloze.ordinals("{{c3::a}} {{c1::b}} {{c3::c}} {{c0::ignored}}"))
        assertEquals(4, Cloze.nextOrdinal("{{c3::a}}"))
        assertEquals(1, Cloze.nextOrdinal("no deletions"))
    }

    @Test
    fun `reveal keeps answers only`() {
        assertEquals("Paris is in France", Cloze.reveal("{{c1::Paris::city}} is in {{c2::France}}"))
    }

    @Test
    fun `card ordinals follow the note kind`() {
        assertEquals(listOf(0), NoteKind.Basic.cardOrdinals(listOf("q", "a")))
        assertEquals(listOf(0, 1), NoteKind.Reversed.cardOrdinals(listOf("q", "a")))
        assertEquals(listOf(0, 2), NoteKind.Cloze.cardOrdinals(listOf("{{c1::x}} {{c3::y}}", "")))
    }

    @Test
    fun `reversed card swaps sides`() {
        val sides = CardSides.of(NoteKind.Reversed, listOf("front", "back"), templateOrd = 1)
        assertEquals(CardSides("back", "front"), sides)
    }
}
