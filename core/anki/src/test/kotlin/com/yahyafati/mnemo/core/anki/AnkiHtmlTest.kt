package com.yahyafati.mnemo.core.anki

import org.junit.Test
import kotlin.test.assertEquals

class AnkiHtmlTest {
    private val hashA = "a".repeat(64)
    private val hashB = "b".repeat(64)
    private val refs = mapOf("cell.png" to "media:$hashA", "hi.mp3" to "media:$hashB")
    private val names = mapOf(hashA to "cell.png", hashB to "hi.mp3")

    private fun md(html: String) = AnkiHtml.toMarkdown(html, refs::get)

    @Test
    fun emphasisEntitiesAndBreaks() {
        assertEquals("What do **mitochondria** make?", md("What do <b>mitochondria</b> make?"))
        assertEquals(
            "*ATP*, via oxidative phosphorylation\n2 < 3 & done",
            md("<i>ATP</i>, via oxidative phosphorylation<br>2 &lt; 3 &amp;&nbsp;done"),
        )
        assertEquals("**B** and *I* and ~~S~~", md("<span style=\"font-weight: bold;\">B</span> and <em>I</em> and <s>S</s>"))
        // Markers must touch the text, so surrounding spaces move outside.
        assertEquals("**bold** word", md("<b> bold </b>word"))
    }

    @Test
    fun linesParagraphsAndLists() {
        assertEquals("A cell:\n![](media:$hashA)", md("<div>A cell:</div><div><img src=\"cell.png\"></div>"))
        assertEquals("A\n\nB", md("<p>A</p><p>B</p>"))
        assertEquals("one\n\ntwo", md("one<br><br>two"))
        assertEquals(
            "1. Prophase\n2. Metaphase\n\n- `x` stays",
            md("<ol><li>Prophase</li><li>Metaphase</li></ol><ul><li><code>x</code> stays</li></ul>"),
        )
        assertEquals("# Title\n\n> quoted\n\n---\n\n```\na < b\n```", md("<h1>Title</h1><blockquote>quoted</blockquote><hr><pre>a &lt; b</pre>"))
        assertEquals("a | b\nc | d", md("<table><tr><td>a</td><td>b</td></tr><tr><td>c</td><td>d</td></tr></table>"))
        assertEquals("[site](https://example.org)", md("<a href=\"https://example.org\">site</a>"))
    }

    @Test
    fun mediaSoundsAndMissingFiles() {
        assertEquals("konnichiwa [sound:media:$hashB]", md("konnichiwa [sound:hi.mp3]"))
        // A file the package doesn't have keeps its name, so nothing is silently lost.
        assertEquals("![](gone.png) [sound:gone.mp3]", md("<img src=\"gone.png\"> [sound:gone.mp3]"))
    }

    @Test
    fun math() {
        assertEquals("\\(E = mc^2\\)", md("\\(E = mc^2\\)"))
        assertEquals("\\(a b < c\\) \\[x\\]", md("\\(a<br>b &lt; c\\) \\[<b>x</b>\\]"))
        assertEquals("\\(x^2\\) and \\[\\sum\\] and \\(y\\) and \\[z\\]", md("[$]x^2[/$] and [$$]\\sum[/$$] and [latex]\$y\$[/latex] and [latex]z[/latex]"))
    }

    @Test
    fun textThatLooksLikeMarkdownIsEscaped() {
        assertEquals(
            "2 * 3, a\\*b, snake_case, \\_x\\_, \\`code\\` and \\~\\~no\\~\\~",
            md("2 * 3, a*b, snake_case, _x_, `code` and ~~no~~"),
        )
        assertEquals("\\# not a heading\n\\- not a list\n1\\. not ordered\n\\> not a quote", md("<div># not a heading</div><div>- not a list</div><div>1. not ordered</div><div>&gt; not a quote</div>"))
        assertEquals("\\[x](y) and \\!\\[a](b)", md("[x](y) and ![a](b)"))
    }

    @Test
    fun clozeMarkupSurvives() {
        assertEquals(
            "{{c1::**Mitochondria**}} make {{c2::ATP::energy molecule}}.",
            md("{{c1::<b>Mitochondria</b>}} make {{c2::ATP::energy molecule}}."),
        )
    }

    @Test
    fun markdownRoundTrips() {
        listOf(
            "**ATP** & *NADH*\n2 < 3",
            "# Title\n\nPara one\nline two\n\n- a\n- b\n\n1. x\n2. y\n\n> quote\n\n```\ncode *x*\n```",
            "{{c1::**x**::hint}} and {{c2::\\(E=mc^2\\)}}",
            "![a cell](media:$hashA) [sound:media:$hashB]",
            "a\\*b and 2 * 3 and snake_case",
            "~~gone~~ and `a*b` and [docs](https://example.com)",
        ).forEach { markdown ->
            val html = AnkiHtml.fromMarkdown(markdown, names::get)
            assertEquals(markdown, AnkiHtml.toMarkdown(html, refs::get), "via $html")
        }
    }

    @Test
    fun exportUsesMediaNames() {
        assertEquals(
            "<img src=\"cell.png\" alt=\"a cell\"> [sound:hi.mp3]",
            AnkiHtml.fromMarkdown("![a cell](media:$hashA) [sound:media:$hashB]", names::get),
        )
        assertEquals("gone", AnkiHtml.fromMarkdown("![gone](media:${"c".repeat(64)})", names::get))
    }

    @Test
    fun sortFieldKeepsImageNames() {
        assertEquals("A cell: cell.png", AnkiHtml.stripHtml("<div>A cell:</div><div><img src=\"cell.png\"></div>"))
        assertEquals("plain", AnkiHtml.stripHtml("plain"))
    }
}
