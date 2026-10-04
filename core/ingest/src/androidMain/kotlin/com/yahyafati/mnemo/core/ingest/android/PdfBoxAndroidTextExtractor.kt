package com.yahyafati.mnemo.core.ingest.android

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.yahyafati.mnemo.core.ingest.PdfSelection
import com.yahyafati.mnemo.core.ingest.PdfTextExtractor
import com.yahyafati.mnemo.core.ingest.RawBookmark
import com.yahyafati.mnemo.core.ingest.pdfLabels
import com.yahyafati.mnemo.core.ingest.pdfOutline
import com.yahyafati.mnemo.core.ingest.pdfPageText
import com.yahyafati.mnemo.core.ingest.pdfSourceResult
import com.yahyafati.mnemo.core.model.PdfInfo
import com.yahyafati.mnemo.core.model.PdfInfoResult
import com.yahyafati.mnemo.core.model.PdfOutlineItem
import com.yahyafati.mnemo.core.model.PdfPageTextsResult
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import java.io.IOException
import java.io.InputStream

/** [PdfTextExtractor] on PdfBox-Android. */
class PdfBoxAndroidTextExtractor(private val context: Context) : PdfTextExtractor {
    @Volatile
    private var initialized = false

    override fun inspect(input: InputStream, fileName: String?): PdfInfoResult = guarded(
        failure = { problem, detail -> PdfInfoResult.Failure(problem, detail) },
    ) {
        open(input) { document ->
            val title = document.documentInformation?.title?.trim()?.takeIf { it.isNotEmpty() } ?: fileName
            val pageCount = document.numberOfPages
            PdfInfoResult.Success(PdfInfo(pageCount, title, pdfOutline(bookmarks(document), pageCount), pdfLabels(pageLabels(document), pageCount)))
        }
    }

    override fun extract(input: InputStream, pages: List<Int>?, fileName: String?): SourceResult = guarded(
        failure = { problem, detail -> SourceResult.Failure(problem, detail) },
    ) {
        open(input) { document ->
            val selection = PdfSelection.of(pages, document.numberOfPages)
            val stripper = PDFTextStripper().apply {
                lineSeparator = "\n"
                paragraphEnd = "\n\n"
            }
            val text = selection.runs.joinToString("\n\n") { run ->
                stripper.startPage = run.first
                stripper.endPage = run.last
                stripper.getText(document)
            }
            pdfSourceResult(text, selection.cut, document.documentInformation?.title, fileName)
        }
    }

    override fun pageTexts(input: InputStream, pages: List<Int>): PdfPageTextsResult = guarded(
        failure = { problem, detail -> PdfPageTextsResult.Failure(problem, detail) },
    ) {
        open(input) { document ->
            val stripper = PDFTextStripper().apply {
                lineSeparator = "\n"
                paragraphEnd = "\n\n"
            }
            val texts = LinkedHashMap<Int, String>()
            for (page in pages.distinct().sorted()) {
                if (page !in 1..document.numberOfPages) continue
                stripper.startPage = page
                stripper.endPage = page
                texts[page] = pdfPageText(stripper.getText(document))
            }
            PdfPageTextsResult.Success(texts)
        }
    }

    /** The bookmarks in document order, with the page each opens; a PDF whose outline can't be read has none. */
    private fun bookmarks(document: PDDocument): List<RawBookmark> = try {
        val found = mutableListOf<RawBookmark>()
        fun walk(node: PDOutlineNode, level: Int) {
            for (item in node.children()) {
                if (found.size >= PdfOutlineItem.MAX_ITEMS) return
                val page = try {
                    item.findDestinationPage(document)?.let { document.pages.indexOf(it) + 1 }?.takeIf { it > 0 }
                } catch (e: IOException) {
                    null
                }
                found += RawBookmark(item.title, level, page)
                if (level < PdfOutlineItem.MAX_LEVEL) walk(item, level + 1)
            }
        }
        document.documentCatalog.documentOutline?.let { walk(it, 1) }
        found
    } catch (e: IOException) {
        emptyList()
    } catch (e: RuntimeException) {
        emptyList()
    }

    private fun pageLabels(document: PDDocument): List<String?>? = try {
        document.documentCatalog.pageLabels?.labelsByPageIndices?.toList()
    } catch (e: IOException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    private inline fun <T> open(input: InputStream, block: (PDDocument) -> T): T {
        if (!initialized) {
            // Loads PdfBox's font and glyph resources; cheap after the first time.
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
        return PDDocument.load(input, "", MemoryUsageSetting.setupMixed(MAX_MAIN_MEMORY_BYTES)).use { document ->
            // Owner-password PDFs open without a password but forbid extraction; the user has the file.
            if (document.isEncrypted) document.isAllSecurityToBeRemoved = true
            block(document)
        }
    }

    private inline fun <T> guarded(failure: (SourceProblem, String?) -> T, block: () -> T): T = try {
        block()
    } catch (e: InvalidPasswordException) {
        failure(SourceProblem.Encrypted, null)
    } catch (e: NoClassDefFoundError) {
        // Certificate-encrypted PDFs need BouncyCastle, which Mnemo doesn't ship.
        failure(SourceProblem.Encrypted, null)
    } catch (e: IOException) {
        failure(SourceProblem.Unsupported, e.message)
    } catch (e: IllegalStateException) {
        failure(SourceProblem.Unsupported, e.message)
    } catch (e: OutOfMemoryError) {
        failure(SourceProblem.TooLarge, null)
    }

    private companion object {
        const val MAX_MAIN_MEMORY_BYTES = 16L * 1024 * 1024
    }
}
