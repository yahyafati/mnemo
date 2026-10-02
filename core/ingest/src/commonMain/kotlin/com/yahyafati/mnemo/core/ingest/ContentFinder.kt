package com.yahyafati.mnemo.core.ingest

import org.jsoup.nodes.Element

/**
 * Finds the element of a page that holds its main text, without site rules: a port of the idea of Mozilla's
 * Readability (not of its code). Every block with a line of prose adds a score to its parent and, halved,
 * its grandparent; the parents start from a weight for their tag and for hints in their `class` and `id`
 * (`article`, `content` against `comment`, `sidebar`); a container full of links counts for less. The best one
 * is taken, widened to the page's `article` / `main` when that adds the title and not much else.
 *
 * Works on `div` soup, where [GenericExtractor]'s old rule (the longest `article`, `main` … element) had
 * nothing to look for. Those elements still count: they start ahead, which settles a close call.
 */
internal object ContentFinder {
    /** The element that holds the text of [body], or null when no block of it reads like prose. */
    fun find(body: Element): Element? {
        val scores = HashMap<Element, Double>()

        fun add(element: Element?, points: Double) {
            if (element == null || element.normalName() == "body" || element.normalName() == "html") return
            scores[element] = (scores[element] ?: initial(element)) + points
        }
        for (block in body.select(BLOCKS)) {
            val text = if (block.normalName() == "p" || block.normalName() == "pre") block.text() else block.ownText()
            if (text.length < MIN_PARAGRAPH) continue
            val points = 1.0 + text.count { it == ',' || it == '，' || it == '、' } + minOf(text.length / 100, 3)
            add(block.parent(), points)
            add(block.parent()?.parent(), points / 2)
        }
        if (scores.isEmpty()) return null

        val scaled = scores.mapValues { (element, score) -> score * (1 - linkDensity(element)) }
        val top = scaled.maxByOrNull { it.value } ?: return null
        var best = top.key
        // A parent that scores nearly as well holds more of the article (Readability's own climb).
        val threshold = top.value / 3
        var last = top.value
        var parent = best.parent()
        while (parent != null && parent.normalName() != "body") {
            val score = scaled[parent]
            if (score != null) {
                if (score < threshold) break
                if (score > last) {
                    best = parent
                    break
                }
                last = score
            }
            parent = parent.parent()
        }
        while (true) {
            val only = best.parent()?.takeIf { it.normalName() != "body" && it.normalName() != "html" && it.childrenSize() == 1 } ?: break
            best = only
        }

        // `article` and `main` bring the headline and byline: take them unless they bring a lot more besides.
        val semantic = best.closest(SEMANTIC)
        if (semantic != null && semantic !== best && semantic.text().length <= best.text().length * WIDEN_LIMIT && linkDensity(semantic) < MAX_WIDEN_LINKS) {
            best = semantic
        }
        return best
    }

    private fun initial(element: Element): Double {
        var weight = when (element.normalName()) {
            "div" -> 5.0
            "pre", "td", "blockquote" -> 3.0
            "address", "ol", "ul", "dl", "dd", "dt", "li", "form" -> -3.0
            "h1", "h2", "h3", "h4", "h5", "h6", "th" -> -5.0
            else -> 0.0
        }
        for (hint in listOf(element.className(), element.id())) {
            val tokens = tokens(hint)
            if (tokens.any { it in NEGATIVE }) weight -= HINT
            if (tokens.any { it in POSITIVE }) weight += HINT
        }
        if (element.`is`(CONTENT_ROOTS)) weight += HEAD_START
        return weight
    }

    /** Words of a class or id: `entryContent`, `entry-content` and `entry_content` are `entry`, `content`. */
    private fun tokens(hint: String): List<String> =
        hint.replace(CAMEL, "$1 $2").lowercase().split(NON_WORD).filter { it.isNotEmpty() }

    private fun linkDensity(element: Element): Double {
        val length = element.text().length
        if (length == 0) return 0.0
        return element.select("a").sumOf { it.text().length }.toDouble() / length
    }

    private const val MIN_PARAGRAPH = 25
    private const val HINT = 25.0
    private const val HEAD_START = 10.0
    private const val WIDEN_LIMIT = 1.5
    private const val MAX_WIDEN_LINKS = 0.25
    private const val SEMANTIC = "article, main, [role=main]"

    /** Elements that usually hold a page's text; they start ahead, and are what the page falls back to. */
    const val CONTENT_ROOTS = "article, main, [role=main], #content, #main-content, .post-content, .entry-content, .article-body, #mw-content-text"
    private const val BLOCKS = "p, pre, td, li, blockquote, dd, div, section, figcaption"

    private val CAMEL = Regex("([a-z0-9])([A-Z])")
    private val NON_WORD = Regex("[^a-z0-9]+")
    private val POSITIVE = setOf("article", "body", "content", "entry", "hentry", "main", "page", "post", "text", "blog", "story")
    private val NEGATIVE = setOf(
        "hidden", "banner", "combx", "comment", "comments", "contact", "foot", "footer", "footnote", "gdpr", "masthead", "media",
        "meta", "outbrain", "promo", "related", "scroll", "share", "shoutbox", "sidebar", "skyscraper", "sponsor", "shopping",
        "tags", "tool", "widget", "nav", "menu", "advert", "ads", "ad",
    )
}
