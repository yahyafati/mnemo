package com.yahyafati.mnemo.core.ingest

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownTextTest {
    private fun md(html: String, onElement: (Element, Int) -> Unit = { _, _ -> }): String =
        TextCleanup.normalizeMarkdown(MarkdownText.of(Jsoup.parse("<body>$html</body>").body(), onElement))

    private fun fixture(name: String): Element {
        val stream = MarkdownTextTest::class.java.getResourceAsStream("/web/$name") ?: error("missing fixture $name")
        return Jsoup.parse(stream, "utf-8", "https://example.com/").body()
    }

    @Test
    fun headingsAndParagraphs() {
        assertEquals("# One\n\nText.\n\n### Three\n\nMore.", md("<h1>One</h1><p>Text.</p><h3>Three</h3><div>More.</div>"))
    }

    @Test
    fun inlineMarks() {
        assertEquals(
            "a **bold** and *italic* and ~~gone~~ text",
            md("<p>a <strong>bold</strong> and <i>italic</i> and <del>gone</del> text</p>"),
        )
    }

    @Test
    fun spacesStayOutsideTheMarks() {
        assertEquals("a **bold** b", md("<p>a<b> bold </b>b</p>"))
        assertEquals("a b", md("<p>a <b> </b>b</p>"))
        assertEquals("a b", md("<p>a <em></em>b</p>"))
    }

    @Test
    fun theSameMarkTwiceIsWrittenOnce() {
        assertEquals("**x**", md("<b><strong>x</strong></b>"))
    }

    @Test
    fun emphasisDoesNotRunAcrossParagraphs() {
        assertEquals("**a**\n\n**b**", md("<b><p>a</p><p>b</p></b>"))
    }

    @Test
    fun inlineCode() {
        assertEquals("Use `launch` now", md("<p>Use <code>launch</code> now</p>"))
        assertEquals("Use `` `x` `` now", md("<p>Use <code>`x`</code> now</p>"))
        assertEquals("Use ``a`b`` now", md("<p>Use <code>a`b</code> now</p>"))
        assertEquals("a b", md("<p>a <code> </code>b</p>"))
    }

    @Test
    fun fencedBlockKeepsItsTextExactly() {
        val html = "<pre><code class=\"language-kotlin\">fun main() {\n    launch {\n\n        run()\n    }\n}\n</code></pre>"
        assertEquals("```kotlin\nfun main() {\n    launch {\n\n        run()\n    }\n}\n```", md(html))
    }

    @Test
    fun aFenceIsLongerThanTheBackticksInsideIt() {
        assertEquals("````\nuse ```x``` here\n````", md("<pre>use ```x``` here</pre>"))
    }

    @Test
    fun preWithoutLanguageAndWithLineBreaks() {
        assertEquals("```\na\nb\n```", md("<pre>a<br>b</pre>"))
    }

    @Test
    fun nestedLists() {
        val html = "<ul><li>One<ul><li>Inner</li><li>Inner 2</li></ul></li><li>Two<ol><li>x</li><li>y</li></ol></li></ul>"
        assertEquals("- One\n  - Inner\n  - Inner 2\n- Two\n  1. x\n  2. y", md(html))
    }

    @Test
    fun orderedListHonoursStart() {
        assertEquals("3. a\n4. b", md("<ol start=\"3\"><li>a</li><li>b</li></ol>"))
    }

    @Test
    fun listAfterAParagraphAndBeforeOne() {
        assertEquals("Intro:\n\n- a\n- b\n\nOutro.", md("<p>Intro:</p><ul><li>a</li><li>b</li></ul><p>Outro.</p>"))
    }

    @Test
    fun anItemWithParagraphsIsALooseItem() {
        assertEquals("- a\n\n  b\n- c", md("<ul><li><p>a</p><p>b</p></li><li><p>c</p></li></ul>"))
    }

    @Test
    fun orderedItemsIndentByTheirMarker() {
        assertEquals("1. a\n   - b", md("<ol><li>a<ul><li>b</li></ul></li></ol>"))
    }

    @Test
    fun emptyItemsLeaveNothing() {
        assertEquals("- a\n- c", md("<ul><li>a</li><li></li><li>c</li></ul>"))
    }

    @Test
    fun blockquotes() {
        assertEquals("> one\n>\n> two", md("<blockquote><p>one</p><p>two</p></blockquote>"))
        assertEquals("before\n\n> quoted\n\nafter", md("<p>before</p><blockquote>quoted</blockquote><p>after</p>"))
    }

    @Test
    fun codeInsideAQuoteAndAList() {
        assertEquals("> ```\n> a\n>\n> b\n> ```", md("<blockquote><pre>a\n\nb</pre></blockquote>"))
        assertEquals("- x\n\n  ```\n  a\n\n  b\n  ```", md("<ul><li>x<pre>a\n\nb</pre></li></ul>"))
    }

    @Test
    fun tableWithHeaderAndAPipe() {
        val html = """
            <table><thead><tr><th>Builder</th><th>Returns</th></tr></thead>
            <tbody><tr><td><code>launch</code></td><td>Job</td></tr><tr><td>a | b</td><td>two<br>lines</td></tr></tbody></table>
        """.trimIndent()
        assertEquals(
            "| Builder | Returns |\n| --- | --- |\n| `launch` | Job |\n| a \\| b | two lines |",
            md(html),
        )
    }

    @Test
    fun theFirstRowIsTheHeaderWithoutAThead() {
        assertEquals("| a | b |\n| --- | --- |\n| 1 | 2 |", md("<table><tr><td>a</td><td>b</td></tr><tr><td>1</td><td>2</td></tr></table>"))
    }

    @Test
    fun shortRowsArePadded() {
        assertEquals("| a | b |\n| --- | --- |\n| 1 | |", md("<table><tr><td>a</td><td>b</td></tr><tr><td>1</td></tr></table>"))
    }

    @Test
    fun aLayoutTableIsReadAsParagraphs() {
        assertEquals("one\n\ntwo", md("<table><tr><td>one</td></tr><tr><td>two</td></tr></table>"))
        assertEquals(
            "left\n\ndata\n\nright",
            md("<table><tr><td>left</td><td><table><tr><td>data</td></tr></table></td><td>right</td></tr></table>"),
        )
    }

    @Test
    fun aTableInsideALayoutTableIsStillATable() {
        assertEquals(
            "| a | b |\n| --- | --- |\n| 1 | 2 |",
            md("<table><tr><td><table><tr><td>a</td><td>b</td></tr><tr><td>1</td><td>2</td></tr></table></td></tr></table>"),
        )
    }

    @Test
    fun linksKeepTheirTextOnly() {
        assertEquals("See the full timeline here.", md("<p>See <a href=\"https://example.com/x\">the full timeline</a> here.</p>"))
    }

    @Test
    fun imagesAndMediaAreDroppedButCaptionsAreKept() {
        assertEquals(
            "Before.\n\nAn artist's impression.\n\nAfter.",
            md("<p>Before.</p><figure><img src=\"a.jpg\" alt=\"A tram\"><figcaption>An artist's impression.</figcaption></figure><p>After.</p>"),
        )
        assertEquals("a b", md("<p>a <svg><text>no</text></svg><video>no</video><audio>no</audio>b</p>"))
    }

    @Test
    fun mathFromMathMl() {
        val html = """<p>Let <math alttext="{\displaystyle f(x)}"><mi>f</mi></math> be</p>"""
        assertEquals("Let \\(f(x)\\) be", md(html))
    }

    @Test
    fun wikipediaMathIsWrittenOnce() {
        val html = """
            <p>The value <span class="mwe-math-element mwe-math-element-inline"><span style="display: none;"><math alttext="{\displaystyle {\widehat {f}}(\xi )}"><mi>f</mi></math></span><img class="mwe-math-fallback-image-inline" alt="{\displaystyle {\widehat {f}}(\xi )}" src="x.svg"></span>, defined by</p>
            <dl><dd><span class="mwe-math-element mwe-math-element-block"><span style="display: none;"><math display="block" alttext="{\displaystyle \int _{-\infty }^{\infty }f(x)\,dx}"></math></span><img class="mwe-math-fallback-image-display" alt="{\displaystyle \int _{-\infty }^{\infty }f(x)\,dx}" src="y.svg"></span></dd></dl>
        """.trimIndent()
        assertEquals(
            "The value \\({\\widehat {f}}(\\xi )\\), defined by\n\n\\[\\int _{-\\infty }^{\\infty }f(x)\\,dx\\]",
            md(html),
        )
    }

    @Test
    fun aFallbackImageAloneStillGivesItsFormula() {
        assertEquals("x \\(a+b\\) y", md("""<p>x <img class="mwe-math-fallback-image-inline" alt="{\textstyle a+b}"> y</p>"""))
    }

    @Test
    fun superscriptsAndSubscriptsKeepTheirText() {
        assertEquals("x2 and H2O", md("<p>x<sup>2</sup> and H<sub>2</sub>O</p>"))
    }

    @Test
    fun lineBreakStaysInTheParagraph() {
        assertEquals("one\ntwo", md("<p>one<br>two</p>"))
    }

    @Test
    fun hrIsAParagraphBreak() {
        assertEquals("a\n\nb", md("a<hr>b"))
    }

    @Test
    fun headingWithABreakStaysOneLine() {
        assertEquals("## Two lines", md("<h2>Two<br>lines</h2>"))
    }

    @Test
    fun whitespaceInHtmlIsCollapsed() {
        assertEquals("one two three", md("<p>one\n   two  ⁠three</p>"))
    }

    @Test
    fun marksThatWouldStartMarkdownAreEscaped() {
        assertEquals("\\# not a heading", md("<p># not a heading</p>"))
        assertEquals("\\> not a quote", md("<p>&gt; not a quote</p>"))
        assertEquals("\\- not a list", md("<p>- not a list</p>"))
        assertEquals("1\\. not a list", md("<p>1. not a list</p>"))
        assertEquals("a \\*star\\* and \\`tick\\`", md("<p>a *star* and `tick`</p>"))
        assertEquals("2 * 3", md("<p>2 * 3</p>"))
        assertEquals("\\_x\\_", md("<p>_x_</p>"))
    }

    @Test
    fun ordinaryTextIsLeftAlone() {
        val plain = "Costs 3.5 - 4 dollars; snake_case, a#b, x_1, 2.0 and (a > b) # c."
        assertEquals(plain, md("<p>${plain.replace(">", "&gt;")}</p>"))
    }

    @Test
    fun markdownInsideACellDoesNotBreakTheRow() {
        assertEquals("| a | b |\n| --- | --- |\n| x | y |", md("<table><tr><td><p>a</p></td><td><ul><li>b</li></ul></td></tr><tr><td>x</td><td>y</td></tr></table>"))
    }

    // ---- offsets ----

    @Test
    fun offsetsPointAtTheStartOfTheirElementInTheText() {
        val html = "<h1 id=\"a\">Title</h1><p id=\"b\">First <b id=\"c\">bold</b> end.</p><ul id=\"d\"><li id=\"e\">item</li></ul><p id=\"f\">Last</p>"
        val offsets = HashMap<String, Int>()
        val text = MarkdownText.of(Jsoup.parse("<body>$html</body>").body()) { element, offset ->
            if (element.id().isNotEmpty()) offsets[element.id()] = offset
        }
        // Each id is where its text starts, give or take the line breaks that come before it.
        fun at(id: String) = text.substring(offsets.getValue(id)).trimStart('\n').trimStart('#', ' ', '-')
        assertTrue(at("a").startsWith("Title"), at("a"))
        assertTrue(at("b").startsWith("First"), at("b"))
        assertTrue(offsets.getValue("c") <= text.indexOf("**bold**") + 1, text)
        assertTrue(at("e").startsWith("item"), at("e"))
        assertTrue(at("f").startsWith("Last"), at("f"))
        assertTrue(offsets.values.all { it in 0..text.length })
        assertEquals(offsets.getValue("a"), 0)
    }

    @Test
    fun everyElementIsReportedInDocumentOrderEvenWhenDropped() {
        val html = "<p id=\"p\">a</p><img id=\"i\"><pre id=\"pre\"><code id=\"c\">x</code></pre><table id=\"t\"><tr><td id=\"td\"><b id=\"b\">1</b></td><td>2</td></tr></table>"
        val seen = mutableListOf<String>()
        MarkdownText.of(Jsoup.parse("<body>$html</body>").body()) { element, _ -> if (element.id().isNotEmpty()) seen += element.id() }
        assertEquals(listOf("p", "i", "pre", "c", "t", "td", "b"), seen)
    }

    // ---- fixtures ----

    @Test
    fun theDocsPageKeepsItsStructure() {
        val text = md(fixture("docs-page.html").selectFirst("main")!!.outerHtml())
        assertTrue(text.startsWith("# Coroutines basics\n\nA coroutine is an instance of a suspendable computation. Start one with `launch`:\n\n```kotlin\nfun main()"), text)
        assertTrue("        delay(1000L)\n\n        println(\"World!\")" in text, text)
        assertTrue("- Structured concurrency\n  - A parent waits for its children\n  - A failed child cancels its parent\n- Dispatchers\n  1. `Dispatchers.Default`\n  2. `Dispatchers.IO`" in text, text)
        assertTrue("| Builder | Returns |\n| --- | --- |\n| `launch` | `Job` |\n| `async` | `Deferred<T>` |\n| a \\| b | a pipe in a cell |" in text, text)
        assertTrue("Use `` `backticks` `` sparingly." in text, text)
    }

    @Test
    fun theNewsArticleKeepsHeadingsListAndQuote() {
        val text = md(fixture("news-article.html").selectFirst("article")!!.outerHtml())
        assertTrue(text.startsWith("# City council approves new tram line\n\nBy A. Reporter"), text)
        assertTrue("voted **9 to 4** on Thursday" in text, text)
        assertTrue("\n\n## What happens next\n\n" in text, text)
        assertTrue("\n\n- Tracks and stations: 120 million\n- Depot: 18 million\n- Contingency: 12 million\n\n" in text, text)
        assertTrue("\n\n> This is the biggest investment" in text, text)
        assertFalse("href" in text || "http" in text || "!\\[" in text)
    }

    @Test
    fun theFourierFixtureHasEachFormulaOnce() {
        val text = md(fixture("fourier-transform.html").outerHtml())
        assertTrue("\\(f(x)\\)" in text, text.take(1500))
        assertFalse("displaystyle" in text)
        assertEquals(text.split("\\(f(x)\\)").size - 1, Regex("""\\\(f\(x\)\\\)""").findAll(text).count())
        // No stray TeX twice in a row, which is what both copies of a formula would give.
        assertFalse(Regex("""(\\\([^)]*\\\))\s*\1""").containsMatchIn(text))
    }

    @Test
    fun aJapanesePageKeepsItsText() {
        val text = md(fixture("ja-amygdala.html").outerHtml())
        assertTrue("扁桃体" in text)
        assertFalse("<" in text)
    }
}
