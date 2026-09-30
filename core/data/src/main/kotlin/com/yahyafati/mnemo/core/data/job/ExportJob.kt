package com.yahyafati.mnemo.core.data.job

import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.common.platform.DocumentAccess
import com.yahyafati.mnemo.core.data.transfer.AnkiExporter
import com.yahyafati.mnemo.core.data.transfer.JsonExporter
import com.yahyafati.mnemo.core.model.ExportFormat
import kotlinx.coroutines.CancellationException
import java.io.File

/** Exports the collection, or one deck, to a file the user picked. */
internal class ExportJob(
    private val ankiExporter: AnkiExporter,
    private val jsonExporter: JsonExporter,
    private val documents: DocumentAccess,
    private val directories: AppDirectories,
) {
    /** Writes [format] to [uri]; [deckId] limits an Anki package to that deck and its subdecks. */
    suspend fun run(uri: String, format: ExportFormat, deckId: String?, id: String, onProgress: suspend (Float) -> Unit) {
        val work = File(directories.cache, "export-$id")
        try {
            documents.openOutput(uri).use { out ->
                when (format) {
                    ExportFormat.Apkg -> ankiExporter.export(deckId, out, work, onProgress)
                    ExportFormat.Json -> jsonExporter.export(out, onProgress)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Don't leave a half-written file behind.
            documents.delete(uri)
            throw e
        } finally {
            work.deleteRecursively()
        }
    }
}
