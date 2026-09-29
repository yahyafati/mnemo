package com.yahyafati.mnemo.core.ai

import com.yahyafati.mnemo.core.ai.parse.GeneratedCardParser
import com.yahyafati.mnemo.core.ai.parse.ParsedCard
import com.yahyafati.mnemo.core.model.NoteKind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The fixture set of model replies (ROADMAP Phase 4 exit criterion): real-world mistakes from
 * models with and without structured output. Every fixture must give the same cards whether it
 * arrives whole, in small chunks, or one character at a time.
 */
class GeneratedCardParserTest {
    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/replies/$name")) { "Missing fixture $name" }.readText()

    private fun parse(text: String, chunk: Int = text.length.coerceAtLeast(1)): List<ParsedCard> {
        val parser = GeneratedCardParser()
        val cards = text.chunked(chunk).flatMap(parser::feed) + parser.finish()
        assertEquals(cards.size, parser.count)
        return cards
    }

    /** Parses [name] whole, in chunks of 7, and one character at a time; all must agree. */
    private fun cards(name: String): List<ParsedCard> {
        val text = fixture(name)
        val whole = parse(text)
        assertEquals(whole, parse(text, chunk = 7), "$name in chunks")
        assertEquals(whole, parse(text, chunk = 1), "$name one character at a time")
        return whole
    }

    @Test
    fun expectedCardCounts() {
        val expected = mapOf(
            "clean-schema.json" to 3,
            "fenced-with-prose.txt" to 3,
            "bare-array.json" to 2,
            "jsonl.txt" to 3,
            "trailing-commas-single-quotes.txt" to 2,
            "latex-escapes.json" to 2,
            "raw-newlines.json" to 2,
            "truncated.json" to 2,
            "think-block.txt" to 2,
            "stringified.json" to 2,
            "plain-qa.txt" to 3,
            "cloze-variants.json" to 2,
            "alt-keys-options.json" to 1,
            "missing-commas.txt" to 2,
            "python-literals.txt" to 1,
            "nested-wrapper.json" to 2,
            "refusal.txt" to 0,
            "invalid-cards.json" to 1,
        )
        for ((name, count) in expected) assertEquals(count, cards(name).size, name)
    }

    @Test
    fun cleanReplyKeepsKindsAndTags() {
        val cards = cards("clean-schema.json")
        assertEquals(listOf(NoteKind.Basic, NoteKind.Cloze, NoteKind.Basic), cards.map { it.kind })
        assertEquals(listOf("amygdala", "limbic-system"), cards[1].tags)
        assertEquals("", cards[1].back)
    }

    @Test
    fun latexSurvivesInvalidEscapes() {
        val (first, second) = cards("latex-escapes.json")
        assertEquals("What is \\(E\\) in \\(E = mc^2\\)?", first.front)
        assertEquals("Energy; \\[E = \\frac{1}{2}mv^2\\] is kinetic energy.", first.back)
        assertEquals("What does \\(\\nabla \\cdot \\mathbf{B} = 0\\) say?", second.front)
        assertTrue("\\theta" in second.back && "\\rho" in second.back)
    }

    @Test
    fun rawControlCharactersStayInTheText() {
        val (first, second) = cards("raw-newlines.json")
        assertEquals("Ectoderm\n\tMesoderm\nEndoderm", first.back)
        assertEquals("What does the\nectoderm form?", second.front)
    }

    @Test
    fun truncatedReplyKeepsCompleteCardsOnly() {
        assertEquals(listOf("What is ATP?", "Where is ATP made?"), cards("truncated.json").map { it.front })
    }

    @Test
    fun reasoningIsIgnored() {
        val cards = cards("think-block.txt")
        assertEquals(listOf("What is osmosis?", "What is diffusion?"), cards.map { it.front })
    }

    @Test
    fun clozeSyntaxIsNormalized() {
        val (first, second) = cards("cloze-variants.json")
        assertEquals("The {{c1::mitochondria}} is the powerhouse of the cell.", first.front)
        assertEquals(listOf("cells", "organelles"), first.tags)
        assertEquals("DNA is copied by {{c1::DNA polymerase}} during {{c2::S phase}}.", second.front)
        assertEquals(listOf("dna"), second.tags)
        assertTrue(listOf(first, second).all { it.kind == NoteKind.Cloze })
    }

    @Test
    fun otherKeyNamesAndOptionsAreUnderstood() {
        val card = cards("alt-keys-options.json").single()
        assertEquals(
            "Which gas do plants absorb?\n\n- A. Oxygen\n- B. Carbon dioxide\n- C. Nitrogen\n- D. Helium",
            card.front,
        )
        assertEquals("B. Carbon dioxide", card.back)
        assertEquals(listOf("botany", "plants"), card.tags)

        val terms = cards("nested-wrapper.json")
        assertEquals("Magna Carta", terms.first().front)
        assertEquals("1215 charter limiting the English king's power.", terms.first().back)
    }

    @Test
    fun pythonStyleAndMissingCommas() {
        assertEquals("Who wrote 'Hamlet'?", cards("python-literals.txt").single().front)
        assertEquals(listOf("About 300,000 km/s", "About 343 m/s"), cards("missing-commas.txt").map { it.back })
    }

    @Test
    fun plainTextQuestionsAndAnswers() {
        val cards = cards("plain-qa.txt")
        assertEquals("What is photosynthesis?", cards[0].front)
        assertEquals("Turning light, water and CO2 into glucose and oxygen.", cards[0].back)
        assertEquals("Which pigment absorbs light?", cards[2].front)
        assertEquals("Chlorophyll.", cards[2].back)
    }

    @Test
    fun cardsArriveAsSoonAsTheyClose() {
        val parser = GeneratedCardParser()
        assertTrue(parser.feed("""{"cards": [{"front": "One", "back": "1"}, {"front": "Tw""").size == 1)
        assertTrue(parser.feed("""o", "back": "2"""").isEmpty())
        assertEquals("Two", parser.feed("}").single().front)
        // The wrapper closing isn't another card.
        assertTrue(parser.feed("]}").isEmpty())
        assertTrue(parser.finish().isEmpty())
        assertEquals(2, parser.count)
    }
}
