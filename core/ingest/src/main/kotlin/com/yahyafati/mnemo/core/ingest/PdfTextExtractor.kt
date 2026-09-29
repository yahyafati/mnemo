package com.yahyafati.mnemo.core.ingest

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.model.SourceText
import java.io.IOException
import java.io.InputStream

/**
 * The text layer of a PDF, with PdfBox-Android. Scanned PDFs have none and fail with
 * [SourceProblem.NoText]: there's no OCR. Blocking; call it off the main thread.
 */
class PdfTextExtractor(private val context: Context) {
    @Volatile
    private var initialized = false

    fun extract(input: InputStream, fileName: String? = null): SourceResult {
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
                    endPage = minOf(pages, MAX_PAGES)
                    lineSeparator = "\n"
                    paragraphEnd = "\n\n"
                }
                val raw = stripper.getText(document)
                val text = TextCleanup.normalize(TextCleanup.joinWrappedLines(TextCleanup.normalize(raw)))
                if (text.isBlank()) return SourceResult.Failure(SourceProblem.NoText)
                val title = document.documentInformation?.title?.trim()?.takeIf { it.isNotEmpty() } ?: fileName
                SourceResult.Success(
                    SourceText(
                        text = text.take(MAX_CHARS),
                        title = title,
                        truncated = pages > MAX_PAGES || text.length > MAX_CHARS,
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

    companion object {
        const val MAX_FILE_BYTES = 50L * 1024 * 1024
        const val MAX_PAGES = 300
        const val MAX_CHARS = 400_000
        private const val MAX_MAIN_MEMORY_BYTES = 16L * 1024 * 1024
    }
}
