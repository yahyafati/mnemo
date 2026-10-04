package com.yahyafati.mnemo.core.ingest.desktop

import com.yahyafati.mnemo.core.ingest.PdfSelection
import com.yahyafati.mnemo.core.ingest.PdfTextExtractor
import com.yahyafati.mnemo.core.ingest.pdfSourceResult
import com.yahyafati.mnemo.core.model.PdfInfo
import com.yahyafati.mnemo.core.model.PdfInfoResult
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.text.PDFTextStripper
import java.io.IOException
import java.io.InputStream

/** [PdfTextExtractor] on Apache PDFBox 3, the library PdfBox-Android is a port of. */
class PdfBoxTextExtractor : PdfTextExtractor {
    override fun inspect(input: InputStream, fileName: String?): PdfInfoResult = guarded(
        failure = { problem, detail -> PdfInfoResult.Failure(problem, detail) },
    ) {
        open(input) { document ->
            val title = document.documentInformation?.title?.trim()?.takeIf { it.isNotEmpty() } ?: fileName
            PdfInfoResult.Success(PdfInfo(document.numberOfPages, title))
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

    private inline fun <T> open(input: InputStream, block: (PDDocument) -> T): T {
        // Parsing and text buffers stay in memory up to a limit, then go to temporary files.
        val cache = MemoryUsageSetting.setupMixed(MAX_MAIN_MEMORY_BYTES).streamCache
        return Loader.loadPDF(RandomAccessReadBuffer(input), "", null, null, cache).use { document ->
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
