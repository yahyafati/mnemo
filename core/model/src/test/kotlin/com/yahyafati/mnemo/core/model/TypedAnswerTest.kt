package com.yahyafati.mnemo.core.model

import com.yahyafati.mnemo.core.model.TypedAnswer.Kind.Extra
import com.yahyafati.mnemo.core.model.TypedAnswer.Kind.Missing
import com.yahyafati.mnemo.core.model.TypedAnswer.Kind.Same
import com.yahyafati.mnemo.core.model.TypedAnswer.Segment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TypedAnswerTest {
    @Test
    fun matchesIgnoringCaseSpacingMarkupAndEndPunctuation() {
        assertTrue(TypedAnswer.check("  lima ", "**Lima**").correct)
        assertTrue(TypedAnswer.check("New  York", "New York.").correct)
        // Composed and decomposed accents are the same answer.
        assertTrue(TypedAnswer.check("Bogotá", "Bogotá").correct)
        assertFalse(TypedAnswer.check("Bogota", "Bogotá").correct)
        assertFalse(TypedAnswer.check("", "Lima").correct)
    }

    @Test
    fun diffMarksExtraAndMissingLetters() {
        val diff = TypedAnswer.diff("Mitocoondria", "Mitochondria")
        // Reading the typed parts gives what was typed; the expected parts give the answer.
        assertEquals("Mitocoondria", diff.filter { it.kind != Missing }.joinToString("") { it.text })
        assertEquals("Mitochondria", diff.filter { it.kind != Extra }.joinToString("") { it.text })
        assertEquals("o", diff.single { it.kind == Extra }.text)
        assertEquals("h", diff.single { it.kind == Missing }.text)

        assertEquals(listOf(Segment(Missing, "Lima")), TypedAnswer.diff("", "Lima"))
    }

    @Test
    fun veryLongAnswersAreComparedWhole() {
        val long = "a".repeat(500)
        assertEquals(listOf(Segment(Extra, "b"), Segment(Missing, long)), TypedAnswer.diff("b", long))
    }
}
