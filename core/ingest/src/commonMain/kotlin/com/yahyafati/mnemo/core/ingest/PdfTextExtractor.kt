package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.PageRanges
import com.yahyafati.mnemo.core.model.PdfInfo
import com.yahyafati.mnemo.core.model.PdfInfoResult
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

    /** The first [MAX_PAGES] pages: a PDF behind a link, or a file read without choosing pages. */
    fun extract(input: InputStream, fileName: String? = null): SourceResult = extract(input, null as List<Int>?, fileName)

    companion object {
        const val MAX_FILE_BYTES = 50L * 1024 * 1024
        const val MAX_PAGES = PdfInfo.MAX_PAGES
        const val MAX_CHARS = 400_000
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

/** What both PDF libraries report the same way: their [rawText] of the chosen pages, cleaned up. */
internal fun pdfSourceResult(rawText: String, cut: Boolean, title: String?, fileName: String?): SourceResult {
    val text = TextCleanup.normalize(TextCleanup.joinWrappedLines(TextCleanup.normalize(rawText)))
    if (text.isBlank()) return SourceResult.Failure(SourceProblem.NoText)
    return SourceResult.Success(
        SourceText(
            text = text.take(PdfTextExtractor.MAX_CHARS),
            title = title?.trim()?.takeIf { it.isNotEmpty() } ?: fileName,
            truncated = cut || text.length > PdfTextExtractor.MAX_CHARS,
        ),
    )
}
