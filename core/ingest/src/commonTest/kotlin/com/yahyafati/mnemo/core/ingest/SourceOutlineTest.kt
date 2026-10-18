package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.WordCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceOutlineTest {
    private fun titles(section: SourceOutline.Section): List<String?> = section.children.map { it.title }

    @Test
    fun aTextWithoutHeadingsIsTheRootAlone() {
        val text = "First paragraph.\n\nSecond paragraph."
        val root = SourceOutline.of(text).root
        assertNull(root.title)
        assertEquals(0, root.level)
        assertTrue(root.children.isEmpty())
        assertEquals(text, root.body)
        assertEquals(text, root.own)
        assertEquals(text, root.text)
    }

    @Test
    fun headingsNestByLevel() {
        val text = "# A\nIntro.\n\n## A1\nOne.\n\n### A1a\nDeep.\n\n## A2\nTwo.\n\n# B\nBee."
        val root = SourceOutline.of(text).root
        assertEquals(listOf("A", "B"), titles(root))
        val a = root.children[0]
        assertEquals(1, a.level)
        assertEquals(listOf("A1", "A2"), titles(a))
        assertEquals(listOf("A1a"), titles(a.children[0]))
        assertEquals(3, a.children[0].children[0].level)
        assertEquals("Intro.", a.body)
        assertEquals("# A\nIntro.", a.own)
        assertEquals("# A\nIntro.\n\n## A1\nOne.\n\n### A1a\nDeep.\n\n## A2\nTwo.", a.text)
        assertEquals("# B\nBee.", root.children[1].text)
        assertEquals("", root.body)
    }

    @Test
    fun aSkippedLevelIsStillASubsection() {
        val root = SourceOutline.of("# A\n### Deep\ntext\n## B\nmore").root
        assertEquals(listOf("A"), titles(root))
        assertEquals(listOf("Deep", "B"), titles(root.children[0]))
        assertEquals("", root.children[0].body)
        assertEquals("# A", root.children[0].own)
    }

    @Test
    fun textBeforeTheFirstHeadingIsTheRootsBody() {
        val root = SourceOutline.of("A lead paragraph.\n\n# A\nBody.").root
        assertEquals("A lead paragraph.", root.body)
        assertEquals("A lead paragraph.", root.own)
        assertEquals(listOf("A"), titles(root))
    }

    @Test
    fun linesInFencedBlocksAreNotHeadings() {
        val text = "# A\n\n```bash\n# not a heading\n```\n\n~~~~\n## nor this\n~~~\n## still inside\n~~~~\n\n## B\nText."
        val a = SourceOutline.of(text).root.children.single()
        assertEquals(listOf("B"), titles(a))
        assertTrue("# not a heading" in a.body)
        assertTrue("## still inside" in a.body)
    }

    @Test
    fun titlesLoseTheirMarks() {
        val text = listOf(
            "# 1. Introduction",
            "## **Nash** equilibrium ##",
            "## [Linked](https://example.org) and `code`",
            "## Learn C#",
            "## \\*Escaped\\*",
        ).joinToString("\n\n")
        val root = SourceOutline.of(text).root
        val first = root.children.single()
        assertEquals("1. Introduction", first.title)
        assertEquals(listOf("Nash equilibrium", "Linked and code", "Learn C#", "Escaped"), titles(first))
    }

    @Test
    fun hashesWithoutASpaceAreNotHeadings() {
        val root = SourceOutline.of("#hashtag\n\n#5 on the list").root
        assertTrue(root.children.isEmpty())
    }

    @Test
    fun sizesAreWordCounts() {
        val text = "# 章\n\n" + "あ".repeat(100) + "\n\n## 節\n\nword word"
        val chapter = SourceOutline.of(text).root.children.single()
        assertEquals(WordCount.count(chapter.text), chapter.words)
        assertEquals(50, chapter.bodyWords)
        assertEquals(WordCount.count(chapter.own), chapter.ownWords)
    }

    @Test
    fun windowsLineEndingsAreRead() {
        val root = SourceOutline.of("# A\r\nOne.\r\n\r\n## B\r\nTwo.").root
        assertEquals(listOf("A"), titles(root))
        assertEquals(listOf("B"), titles(root.children.single()))
    }
}
