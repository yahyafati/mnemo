package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.model.SourceText
import java.io.InputStream

/**
 * The text layer of a PDF. Scanned PDFs have none and fail with [SourceProblem.NoText]: there's no
 * OCR. Blocking; call it off the main thread.
 */
interface PdfTextExtractor {
    fun extract(input: InputStream, fileName: String? = null): SourceResult

    companion object {
        const val MAX_FILE_BYTES = 50L * 1024 * 1024
        const val MAX_PAGES = 300
        const val MAX_CHARS = 400_000
    }
}

/** What both PDF libraries report the same way: their [rawText] of the first pages, cleaned up. */
internal fun pdfSourceResult(rawText: String, pages: Int, title: String?, fileName: String?): SourceResult {
    val text = TextCleanup.normalize(TextCleanup.joinWrappedLines(TextCleanup.normalize(rawText)))
    if (text.isBlank()) return SourceResult.Failure(SourceProblem.NoText)
    return SourceResult.Success(
        SourceText(
            text = text.take(PdfTextExtractor.MAX_CHARS),
            title = title?.trim()?.takeIf { it.isNotEmpty() } ?: fileName,
            truncated = pages > PdfTextExtractor.MAX_PAGES || text.length > PdfTextExtractor.MAX_CHARS,
        ),
    )
}
