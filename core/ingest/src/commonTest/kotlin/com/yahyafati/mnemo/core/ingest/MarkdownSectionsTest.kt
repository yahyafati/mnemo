package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.SourceSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownSectionsTest {
    private fun titles(text: String) = MarkdownSections.of(text).map { it.title }

    @Test
    fun oneSectionPerHeadingWithTheLeadFirst() {
        val text = "Intro text.\n\n# One\n\nFirst.\n\n## Two\n\nSecond.\n\n## Three\n\nThird."
        val sections = MarkdownSections.of(text)
        assertEquals(listOf(null, "One", "Two", "Three"), sections.map { it.title })
        assertEquals(listOf(0, 1, 2, 2), sections.map { it.level })
        assertEquals(listOf("Intro text.", "# One\n\nFirst.", "## Two\n\nSecond.", "## Three\n\nThird."), sections.map { text.substring(it.start, it.end) })
        assertEquals(listOf(0, 1, 2, 3), sections.map { it.id })
    }

    @Test
    fun noLeadWhenTheTextStartsWithAHeading() {
        val sections = MarkdownSections.of("# A\n\na\n\n## B\n\nb\n\n## C\n\nc")
        assertEquals(listOf("A", "B", "C"), sections.map { it.title })
        assertEquals(SourceSection(0, "A", 1, 0, 6), sections.first())
    }

    @Test
    fun fewerThanThreeHeadingsAreNotSections() {
        assertTrue(MarkdownSections.of("# A\n\na\n\n## B\n\nb").isEmpty())
        assertTrue(MarkdownSections.of("just text").isEmpty())
    }

    @Test
    fun hashLinesInsideFencesAreNotHeadings() {
        val text = "# A\n\n```sh\n# comment\n## also\n```\n\n## B\n\nb\n\n## C\n\nc"
        assertEquals(listOf("A", "B", "C"), titles(text))
        val tilde = "# A\n\n~~~\n# x\n~~~\n\n## B\n\nb\n\n## C\n\nc"
        assertEquals(listOf("A", "B", "C"), titles(tilde))
    }

    @Test
    fun aHashWithoutASpaceOrDeeperThanSixIsText() {
        assertEquals(listOf("A", "C", "D"), titles("# A\n\n#tag\n\n####### seven\n\n## C\n\n## D"))
    }
}
