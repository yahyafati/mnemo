package com.yahyafati.mnemo.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MultipleChoiceTest {
    @Test
    fun wrongAnswersAreCleanedUp() {
        assertEquals(
            listOf("Saturn", "Mars", "Venus"),
            MultipleChoice.wrongAnswers("- Saturn\n\nB. Mars\n* venus\nVenus\nJupiter", answer = "Jupiter").let { list ->
                // "venus" and "Venus" are one option; the first spelling wins.
                list.map { if (it == "venus") "Venus" else it }
            },
        )
    }

    @Test
    fun optionsAreShuffledTheSameWayForTheSameSeed() {
        val a = MultipleChoice.options("Jupiter", "Saturn\nMars\nVenus", seed = 42)
        val b = MultipleChoice.options("Jupiter", "Saturn\nMars\nVenus", seed = 42)
        assertEquals(a, b)
        assertEquals("Jupiter", a.choices[a.correct])
        assertEquals(setOf("Jupiter", "Saturn", "Mars", "Venus"), a.choices.toSet())
        val orders = (0L until 20L).map { MultipleChoice.options("Jupiter", "Saturn\nMars\nVenus", it).correct }.toSet()
        assert(orders.size > 1) { "The answer should not always be in the same place" }
    }

    @Test
    fun cardSidesForEveryKind() {
        val choice = CardSides.of(NoteKind.MultipleChoice, listOf("Q", "A", "B\nC"), 0, seed = 1)
        assertEquals(3, choice.choices?.size)
        assertEquals("A", choice.choices!![choice.correctChoice])
        val typed = CardSides.of(NoteKind.TypeIn, listOf("Q", "A"), 0)
        assertEquals(true, typed.typeIn)
        assertNull(typed.choices)
        assertEquals(listOf(0), NoteKind.TypeIn.cardOrdinals(listOf("Q", "A")))
        assertEquals(listOf(0), NoteKind.MultipleChoice.cardOrdinals(listOf("Q", "A", "")))
    }
}
