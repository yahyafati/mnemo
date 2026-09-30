package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
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
