package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.common.platform.DocumentAccess
import com.yahyafati.mnemo.core.ingest.PdfTextExtractor
import com.yahyafati.mnemo.core.ingest.SpeechTranscriber
import com.yahyafati.mnemo.core.ingest.WebPageExtractor
import com.yahyafati.mnemo.core.model.DictationEvent
import com.yahyafati.mnemo.core.model.SourceInput
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.model.SourceText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Smart Extract's sources (`:core:ingest`): PDFs, links and dictation become plain text on the device. */
interface SourceRepository {
    suspend fun read(source: SourceInput): SourceResult

    fun isDictationAvailable(): Boolean

    /** On-device dictation until the collector cancels. Needs the microphone permission. */
    fun dictate(languageTag: String? = null): Flow<DictationEvent>
}

internal class DefaultSourceRepository(
    private val documents: DocumentAccess,
    private val pdf: PdfTextExtractor,
    private val web: WebPageExtractor,
    private val speech: SpeechTranscriber,
    private val ioDispatcher: CoroutineDispatcher,
) : SourceRepository {
    override suspend fun read(source: SourceInput): SourceResult = withContext(ioDispatcher) {
        when (source) {
            is SourceInput.Link -> web.extract(source.url)
            is SourceInput.Pdf -> readPdf(source.uri)
            is SourceInput.TextFile -> readTextFile(source.uri)
        }
    }

    private fun readPdf(uri: String): SourceResult {
        val info = documents.info(uri)
        if ((info.size ?: 0) > PdfTextExtractor.MAX_FILE_BYTES) return SourceResult.Failure(SourceProblem.TooLarge)
        val input = try {
            documents.openInput(uri)
        } catch (e: IOException) {
            return SourceResult.Failure(SourceProblem.FileUnavailable)
        }
        return input.use { pdf.extract(it, info.name?.removeSuffix(".pdf")?.removeSuffix(".PDF")) }
    }

    private fun readTextFile(uri: String): SourceResult {
        val info = documents.info(uri)
        if ((info.size ?: 0) > MAX_TEXT_FILE_BYTES) return SourceResult.Failure(SourceProblem.TooLarge)
        val bytes = try {
            // One byte more than the limit tells a file that is too large from one that just fits, whatever it says its size is.
            documents.openInput(uri).use { it.readUpTo(MAX_TEXT_FILE_BYTES.toInt() + 1) }
        } catch (e: IOException) {
            return SourceResult.Failure(SourceProblem.FileUnavailable)
        }
        if (bytes.size > MAX_TEXT_FILE_BYTES) return SourceResult.Failure(SourceProblem.TooLarge)
        val text = bytes.decodeToString().removePrefix("\uFEFF").trim()
        // A NUL is never in text: this is a binary file with a text file's name.
        if ('\u0000' in text) return SourceResult.Failure(SourceProblem.Unsupported)
        if (text.isEmpty()) return SourceResult.Failure(SourceProblem.NoText)
        return SourceResult.Success(
            SourceText(
                text = text.take(PdfTextExtractor.MAX_CHARS),
                title = info.name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() },
                truncated = text.length > PdfTextExtractor.MAX_CHARS,
            ),
        )
    }

    override fun isDictationAvailable(): Boolean = speech.isAvailable()

    override fun dictate(languageTag: String?): Flow<DictationEvent> = speech.transcribe(languageTag)

    private companion object {
        /** A text file larger than this isn't a document Smart Extract can use (400,000 characters are read at most). */
        const val MAX_TEXT_FILE_BYTES = 8L * 1024 * 1024
    }
}

/** At most [limit] bytes of this stream; the rest stays unread. */
private fun InputStream.readUpTo(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var remaining = limit
    while (remaining > 0) {
        val read = read(buffer, 0, minOf(buffer.size, remaining))
        if (read < 0) break
        out.write(buffer, 0, read)
        remaining -= read
    }
    return out.toByteArray()
}
