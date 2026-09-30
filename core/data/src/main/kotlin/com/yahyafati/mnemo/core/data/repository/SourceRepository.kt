package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.common.platform.DocumentAccess
import com.yahyafati.mnemo.core.ingest.PdfTextExtractor
import com.yahyafati.mnemo.core.ingest.SpeechTranscriber
import com.yahyafati.mnemo.core.ingest.WebPageExtractor
import com.yahyafati.mnemo.core.model.DictationEvent
import com.yahyafati.mnemo.core.model.SourceInput
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException

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

    override fun isDictationAvailable(): Boolean = speech.isAvailable()

    override fun dictate(languageTag: String?): Flow<DictationEvent> = speech.transcribe(languageTag)
}
