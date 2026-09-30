package com.yahyafati.mnemo.core.ingest.android

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.yahyafati.mnemo.core.ingest.PdfTextExtractor
import com.yahyafati.mnemo.core.ingest.TextCleanup
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.model.SourceText
import java.io.IOException
import java.io.InputStream

/** [PdfTextExtractor] on PdfBox-Android. */
class PdfBoxAndroidTextExtractor(private val context: Context) : PdfTextExtractor {
    @Volatile
    private var initialized = false

    override fun extract(input: InputStream, fileName: String?): SourceResult {
        if (!initialized) {
            // Loads PdfBox's font and glyph resources; cheap after the first time.
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
        return try {
            PDDocument.load(input, "", MemoryUsageSetting.setupMixed(MAX_MAIN_MEMORY_BYTES)).use { document ->
                // Owner-password PDFs open without a password but forbid extraction; the user has the file.
                if (document.isEncrypted) document.isAllSecurityToBeRemoved = true
                val pages = document.numberOfPages
                val stripper = PDFTextStripper().apply {
                    startPage = 1
                    endPage = minOf(pages, PdfTextExtractor.MAX_PAGES)
                    lineSeparator = "\n"
                    paragraphEnd = "\n\n"
                }
                val raw = stripper.getText(document)
                val text = TextCleanup.normalize(TextCleanup.joinWrappedLines(TextCleanup.normalize(raw)))
                if (text.isBlank()) return SourceResult.Failure(SourceProblem.NoText)
                val title = document.documentInformation?.title?.trim()?.takeIf { it.isNotEmpty() } ?: fileName
                SourceResult.Success(
                    SourceText(
                        text = text.take(PdfTextExtractor.MAX_CHARS),
                        title = title,
                        truncated = pages > PdfTextExtractor.MAX_PAGES || text.length > PdfTextExtractor.MAX_CHARS,
                    ),
                )
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
