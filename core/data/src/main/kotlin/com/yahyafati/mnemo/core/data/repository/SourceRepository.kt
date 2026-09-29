package com.yahyafati.mnemo.core.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.yahyafati.mnemo.core.common.dispatchers.Dispatcher
import com.yahyafati.mnemo.core.common.dispatchers.MnemoDispatchers
import com.yahyafati.mnemo.core.ingest.PdfTextExtractor
import com.yahyafati.mnemo.core.ingest.SpeechTranscriber
import com.yahyafati.mnemo.core.ingest.WebPageExtractor
import com.yahyafati.mnemo.core.model.DictationEvent
import com.yahyafati.mnemo.core.model.SourceInput
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import javax.inject.Inject

/** Smart Extract's sources (`:core:ingest`): PDFs, links and dictation become plain text on the device. */
interface SourceRepository {
    suspend fun read(source: SourceInput): SourceResult

    fun isDictationAvailable(): Boolean

    /** On-device dictation until the collector cancels. Needs the microphone permission. */
    fun dictate(languageTag: String? = null): Flow<DictationEvent>
}

internal class DefaultSourceRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pdf: PdfTextExtractor,
    private val web: WebPageExtractor,
    private val speech: SpeechTranscriber,
    @param:Dispatcher(MnemoDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : SourceRepository {
    override suspend fun read(source: SourceInput): SourceResult = withContext(ioDispatcher) {
        when (source) {
            is SourceInput.Link -> web.extract(source.url)
            is SourceInput.Pdf -> readPdf(source.uri.toUri())
        }
    }

    private fun readPdf(uri: Uri): SourceResult {
        val resolver = context.contentResolver
        return try {
            var name: String? = null
            var size: Long? = null
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0)
                    size = if (cursor.isNull(1)) null else cursor.getLong(1)
                }
            }
            if ((size ?: 0) > PdfTextExtractor.MAX_FILE_BYTES) return SourceResult.Failure(SourceProblem.TooLarge)
            val input = resolver.openInputStream(uri) ?: return SourceResult.Failure(SourceProblem.FileUnavailable)
            input.use { pdf.extract(it, name?.removeSuffix(".pdf")?.removeSuffix(".PDF")) }
        } catch (e: FileNotFoundException) {
            SourceResult.Failure(SourceProblem.FileUnavailable)
        } catch (e: SecurityException) {
            SourceResult.Failure(SourceProblem.FileUnavailable)
        }
    }

    override fun isDictationAvailable(): Boolean = speech.isAvailable()

    override fun dictate(languageTag: String?): Flow<DictationEvent> = speech.transcribe(languageTag)
}
