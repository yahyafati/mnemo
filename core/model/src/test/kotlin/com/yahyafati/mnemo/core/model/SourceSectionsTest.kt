package com.yahyafati.mnemo.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceSectionsTest {
    private val text = "Lead.\n\n## History\n\nOld.\n\n### Early years\n\nYoung.\n\n## Gefühl\n\nFeeling."

    private val source = run {
        var offset = 0
        val parts = listOf(null to 0, "History" to 2, "Early years" to 3, "Gefühl" to 2)
        val ranges = listOf("Lead.", "## History\n\nOld.", "### Early years\n\nYoung.", "## Gefühl\n\nFeeling.")
        val sections = parts.mapIndexed { id, (title, level) ->
            val start = text.indexOf(ranges[id], offset)
            offset = start + ranges[id].length
            SourceSection(id, title, level, start, offset)
        }
        SourceText(text, "Page", sections = sections)
    }

    @Test
    fun aFragmentNamesASectionAndItsSubsections() {
        assertEquals(setOf(1, 2), SourceSections.forFragment(source.sections, "History"))
        assertEquals(setOf(2), SourceSections.forFragment(source.sections, "#Early_years"))
        assertEquals(setOf(3), SourceSections.forFragment(source.sections, "gef%C3%BChl"))
    }

    @Test
    fun aFragmentThatNamesNothingIsNull() {
        assertNull(SourceSections.forFragment(source.sections, ""))
        assertNull(SourceSections.forFragment(source.sections, "Nowhere"))
        assertNull(SourceSections.forFragment(source.sections, "100%"))
    }

    @Test
    fun theSelectedSectionsJoinInTextOrder() {
        assertEquals("Lead.\n\n## Gefühl\n\nFeeling.", SourceSections.join(source, setOf(3, 0)))
        assertEquals("", SourceSections.join(source, emptySet()))
    }
}
