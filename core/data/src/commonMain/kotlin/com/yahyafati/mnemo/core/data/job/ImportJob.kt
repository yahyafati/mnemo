package com.yahyafati.mnemo.core.data.job

import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.common.platform.DocumentAccess
import com.yahyafati.mnemo.core.data.transfer.AnkiImporter
import com.yahyafati.mnemo.core.model.ImportSummary
import java.io.File

// The bodies of the long transfers (ARCHITECTURE §5.4), free of any scheduler: WorkManager runs
// them on Android through the workers in `work/`, and coroutines run them on desktop. A job
// reports progress through a callback and throws on failure; the caller maps the exception with
// `toTransferError`.

/** Imports an Anki package the user picked. */
internal class ImportJob(
    private val importer: AnkiImporter,
    private val documents: DocumentAccess,
    private val directories: AppDirectories,
) {
    /**
     * Imports the package at [uri]. [id] names this run's scratch directory, which is removed at
     * the end; [onProgress] gets 0..1.
     */
    suspend fun run(uri: String, id: String, onProgress: suspend (Float) -> Unit): ImportSummary {
        val work = File(directories.cache, "import-$id")
        try {
            work.mkdirs()
            // A local copy: the package is read with random access, and a picker's grant is short-lived.
            val copy = File(work, "package.apkg")
            documents.openInput(uri).use { source -> copy.outputStream().use { source.copyTo(it) } }
            return importer.import(copy, File(work, "unpacked"), onProgress)
        } finally {
            work.deleteRecursively()
        }
    }
}
