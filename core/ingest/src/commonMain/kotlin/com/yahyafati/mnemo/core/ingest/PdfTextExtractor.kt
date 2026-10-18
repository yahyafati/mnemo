package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.PageRanges
import com.yahyafati.mnemo.core.model.PdfPageTextsResult
import com.yahyafati.mnemo.core.model.PdfPageText
import com.yahyafati.mnemo.core.model.PdfInfo
import com.yahyafati.mnemo.core.model.PdfInfoResult
import com.yahyafati.mnemo.core.model.PdfOutlineItem
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.model.SourceText
import java.io.InputStream

/**
 * The text layer of a PDF. Scanned PDFs have none and fail with [SourceProblem.NoText]: there's no
 * OCR. Blocking; call it off the main thread.
 */
interface PdfTextExtractor {
    /** How many pages the PDF has and what it calls itself ([fileName] when it has no title), without reading its text. */
    fun inspect(input: InputStream, fileName: String? = null): PdfInfoResult

    /**
     * The text of [pages] (1-based, any order; ones the PDF doesn't have are ignored), or of its first
     * [MAX_PAGES] pages when [pages] is null. Runs of consecutive pages are read together and runs are joined with
     * a blank line. At most [MAX_PAGES] pages are read; `truncated` says when that cut some.
     */
    fun extract(input: InputStream, pages: List<Int>?, fileName: String? = null): SourceResult

    /**
     * The text layer of each of [pages] on its own (1-based; ones the PDF doesn't have are left out), cleaned like
     * [extract]'s text and empty for a page with none. For deciding, page by page, which pages need their image read
     * (ADR 0014, P5); [PdfPageText.hasText] says whether a page's text is enough. Not limited to [MAX_PAGES].
     */
    fun pageTexts(input: InputStream, pages: List<Int>): PdfPageTextsResult

    /** The first [MAX_PAGES] pages: a PDF behind a link, or a file read without choosing pages. */
    fun extract(input: InputStream, fileName: String? = null): SourceResult = extract(input, null as List<Int>?, fileName)

    companion object {
        const val MAX_FILE_BYTES = 50L * 1024 * 1024
        const val MAX_PAGES = PdfInfo.MAX_PAGES
        const val MAX_CHARS = SourceText.MAX_CHARS
    }
}

/** The runs of pages to strip, and whether [PdfTextExtractor.MAX_PAGES] left some of the wanted ones out. */
internal class PdfSelection(val runs: List<IntRange>, val cut: Boolean) {
    companion object {
        /** [pages] clipped to a document of [pageCount] pages, or its first pages when null. */
        fun of(pages: List<Int>?, pageCount: Int): PdfSelection {
            val wanted = if (pages == null) PageRanges.all(pageCount) else PageRanges.of(pages.filter { it <= pageCount })
            val kept = wanted.first(PdfTextExtractor.MAX_PAGES)
            return PdfSelection(kept.runs, cut = kept.count < wanted.count)
        }
    }
}

/** One page's text as both PDF libraries report it: tidied, and with lines the page wrapped joined up. */
internal fun pdfPageText(rawText: String): String = TextCleanup.normalize(TextCleanup.joinWrappedLines(TextCleanup.normalize(rawText)))

/** What both PDF libraries report the same way: their [rawText] of the chosen pages, cleaned up. */
internal fun pdfSourceResult(rawText: String, cut: Boolean, title: String?, fileName: String?): SourceResult {
    val text = pdfPageText(rawText)
    if (text.isBlank()) return SourceResult.Failure(SourceProblem.NoText)
    return SourceResult.Success(
        SourceText(
            text = text.take(PdfTextExtractor.MAX_CHARS),
            title = title?.trim()?.takeIf { it.isNotEmpty() } ?: fileName,
            truncated = cut || text.length > PdfTextExtractor.MAX_CHARS,
        ),
    )
}

/**
 * The text layer of [selection], read by [read] (the raw text of a run of consecutive pages, 1-based and inclusive), with
 * the bookmarks of [outline] written in as Markdown headings (ADR 0015, C6): `## <title>` for a top-level bookmark, `###`
 * for the next level and so on, at the start of its page, a page with several in outline order. A bookmark whose page was
 * not selected is left out, and a PDF without bookmarks reads exactly as before. A run is cut where a bookmarked page
 * starts, so a sentence never joins across a heading, and a PDF with no text at all is still [SourceProblem.NoText]
 * (headings alone are not text).
 */
internal fun pdfTextResult(
    selection: PdfSelection,
    outline: List<PdfOutlineItem>,
    title: String?,
    fileName: String?,
    read: (IntRange) -> String,
): SourceResult {
    val bookmarked = outline.groupBy { it.page }
    if (selection.runs.none { run -> bookmarked.keys.any { it in run } }) {
        return pdfSourceResult(selection.runs.joinToString("\n\n") { read(it) }, selection.cut, title, fileName)
    }
    val parts = mutableListOf<Pair<List<PdfOutlineItem>, String>>()
    for (run in selection.runs) {
        val starts = (run.first..run.last).filter { it == run.first || it in bookmarked }
        for ((index, start) in starts.withIndex()) {
            val end = starts.getOrNull(index + 1)?.minus(1) ?: run.last
            parts += bookmarked[start].orEmpty() to pdfPageText(read(start..end))
        }
    }
    if (parts.all { it.second.isBlank() }) return SourceResult.Failure(SourceProblem.NoText)
    val text = parts.joinToString("\n\n") { (headings, body) ->
        (headings.map { "#".repeat(it.level + 1) + " " + it.title } + listOfNotNull(body.takeIf { it.isNotBlank() })).joinToString("\n\n")
    }
    return SourceResult.Success(
        SourceText(
            text = text.take(PdfTextExtractor.MAX_CHARS),
            title = title?.trim()?.takeIf { it.isNotEmpty() } ?: fileName,
            truncated = selection.cut || text.length > PdfTextExtractor.MAX_CHARS,
        ),
    )
}

/** A bookmark as a PDF library reports it: [page] is 1-based, null when it leads nowhere. */
internal class RawBookmark(val title: String?, val level: Int, val page: Int?)

/**
 * The bookmarks a picker can use (ADR 0014, P2): in order, with a title and a page the PDF has, no deeper
 * than [PdfOutlineItem.MAX_LEVEL] and no more than [PdfOutlineItem.MAX_ITEMS]. A bookmark that leads nowhere is
 * dropped; the ones under it keep their own level.
 */
internal fun pdfOutline(raw: List<RawBookmark>, pageCount: Int): List<PdfOutlineItem> = raw.mapNotNull { bookmark ->
    val title = bookmark.title?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
    val page = bookmark.page?.takeIf { it in 1..pageCount } ?: return@mapNotNull null
    PdfOutlineItem(title, bookmark.level, page).takeIf { it.level in 1..PdfOutlineItem.MAX_LEVEL }
}.take(PdfOutlineItem.MAX_ITEMS)

/**
 * The page labels worth showing: null when the PDF has none, they don't cover every page, or each is just its
 * position (`1`, `2`, `3`…), which the page number already says.
 */
internal fun pdfLabels(raw: List<String?>?, pageCount: Int): List<String>? {
    if (raw == null || raw.size != pageCount) return null
    val labels = raw.map { it.orEmpty() }
    if (labels.all { it.isBlank() } || labels.withIndex().all { (index, label) -> label == (index + 1).toString() }) return null
    return labels
}
