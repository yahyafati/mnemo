package com.yahyafati.mnemo.core.ingest.desktop

import com.yahyafati.mnemo.core.ingest.PdfTextExtractor
import com.yahyafati.mnemo.core.ingest.pdfSourceResult
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.text.PDFTextStripper
import java.io.IOException
import java.io.InputStream

/** [PdfTextExtractor] on Apache PDFBox 3, the library PdfBox-Android is a port of. */
class PdfBoxTextExtractor : PdfTextExtractor {
    override fun extract(input: InputStream, fileName: String?): SourceResult {
        return try {
            // Parsing and text buffers stay in memory up to a limit, then go to temporary files.
            val cache = MemoryUsageSetting.setupMixed(MAX_MAIN_MEMORY_BYTES).streamCache
            Loader.loadPDF(RandomAccessReadBuffer(input), "", null, null, cache).use { document ->
                // Owner-password PDFs open without a password but forbid extraction; the user has the file.
                if (document.isEncrypted) document.isAllSecurityToBeRemoved = true
                val pages = document.numberOfPages
                val stripper = PDFTextStripper().apply {
                    startPage = 1
                    endPage = minOf(pages, PdfTextExtractor.MAX_PAGES)
                    lineSeparator = "\n"
                    paragraphEnd = "\n\n"
                }
                pdfSourceResult(stripper.getText(document), pages, document.documentInformation?.title, fileName)
            }
        } catch (e: InvalidPasswordException) {
            SourceResult.Failure(SourceProblem.Encrypted)
        } catch (e: NoClassDefFoundError) {
            // Certificate-encrypted PDFs need BouncyCastle, which Mnemo doesn't ship.
            SourceResult.Failure(SourceProblem.Encrypted)
        } catch (e: IOException) {
            SourceResult.Failure(SourceProblem.Unsupported, e.message)
        } catch (e: IllegalStateException) {
            SourceResult.Failure(SourceProblem.Unsupported, e.message)
        } catch (e: OutOfMemoryError) {
            SourceResult.Failure(SourceProblem.TooLarge)
        }
    }

    private companion object {
        const val MAX_MAIN_MEMORY_BYTES = 16L * 1024 * 1024
    }
}
