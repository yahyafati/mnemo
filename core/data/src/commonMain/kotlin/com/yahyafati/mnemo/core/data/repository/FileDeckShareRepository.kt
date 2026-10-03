package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.data.transfer.AnkiExporter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Duration
import java.util.UUID

internal class FileDeckShareRepository(
    private val exporter: AnkiExporter,
    private val directories: AppDirectories,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
) : DeckShareRepository {
    override suspend fun prepare(deckId: String, name: String, onProgress: suspend (Float) -> Unit): SharedDeck =
        withContext(ioDispatcher) {
            val folder = File(directories.cache, SHARE_FOLDER)
            removeOld(folder)
            if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Can't create $folder")
            val fileName = fileName(name)
            val file = File(folder, fileName)
            val work = File(directories.cache, "share-${UUID.randomUUID()}")
            try {
                file.outputStream().buffered().use { exporter.export(deckId, it, work, onProgress) }
            } catch (e: Exception) {
                // Cancelled or failed: don't leave half a package for the share sheet to find.
                file.delete()
                throw e
            } finally {
                work.deleteRecursively()
            }
            SharedDeck(file.absolutePath, fileName)
        }

    /** A shared file may still be read by the app it went to, so earlier ones stay for a day. */
    private fun removeOld(folder: File) {
        val cutoff = clock.now().minus(KEEP).toEpochMilli()
        folder.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    companion object {
        /** The folder in the cache that the Android file provider shares from (`app/src/main/res/xml/share_paths.xml`). */
        const val SHARE_FOLDER = "share"

        private val KEEP = Duration.ofDays(1)
        private const val MAX_NAME = 80
        private val UNSAFE = Regex("""[\\/:*?"<>|\u0000-\u001f]+""")

        /** `Spanish::Verbs` → `Spanish_Verbs.apkg`: a deck's name may hold characters a file name can't. */
        fun fileName(deckName: String): String {
            val base = deckName.replace(UNSAFE, "_").trim().trim('.', '_').take(MAX_NAME).trim()
            return "${base.ifEmpty { "deck" }}.apkg"
        }
    }
}
