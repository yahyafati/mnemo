package com.yahyafati.mnemo.core.data.backup

import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.common.platform.DocumentAccess
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.data.repository.MediaRepository
import com.yahyafati.mnemo.core.database.DatabaseSnapshot
import com.yahyafati.mnemo.core.datastore.di.DataStoreFiles
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A backup that was read and is ready to restore. */
data class BackupInfo(val createdAt: Instant, val schemaVersion: Int)

/** A file is not a Mnemo backup, or one this version can't restore. */
class BackupFormatException(message: String) : Exception(message)

/**
 * Backups (ARCHITECTURE §5.4): a zip with a manifest, a consistent copy of the database, the
 * preferences file and every media file. Restoring stages the files and applies them at the next
 * start ([PendingRestore]), before Room or DataStore have opened anything.
 */
class BackupManager internal constructor(
    private val directories: AppDirectories,
    private val documents: DocumentAccess,
    private val snapshot: DatabaseSnapshot,
    private val mediaRepository: MediaRepository,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Writes a backup to [output]. [workDir] is scratch space, deleted afterwards. */
    suspend fun write(output: OutputStream, workDir: File, onProgress: suspend (Float) -> Unit = {}) {
        withContext(ioDispatcher) { workDir.mkdirs() }
        try {
            val databaseFiles = snapshot.copyTo(workDir)
            withContext(ioDispatcher) {
                val zip = ZipOutputStream(output)
                zip.entry(MANIFEST) {
                    it.write(json.encodeToString(Manifest.serializer(), Manifest(FORMAT, VERSION, snapshot.version(), clock.now().toString())).toByteArray())
                }
                databaseFiles.forEach { file -> zip.entry("$DATABASE/${file.name}") { out -> file.inputStream().use { it.copyTo(out) } } }
                preferencesFile().takeIf { it.exists() }?.let { file ->
                    zip.entry("$PREFERENCES/${file.name}") { out -> file.inputStream().use { it.copyTo(out) } }
                }
                val media = mediaRepository.getAll()
                media.forEachIndexed { index, m ->
                    val file = mediaRepository.file(m.id)
                    if (file.exists()) zip.entry("$MEDIA/${m.id}") { out -> file.inputStream().use { it.copyTo(out) } }
                    if (index % 50 == 0) onProgress(index.toFloat() / media.size)
                }
                zip.finish()
            }
        } finally {
            withContext(ioDispatcher) { workDir.deleteRecursively() }
        }
    }

    /**
     * Writes an automatic backup into the folder [folderUri] and deletes Mnemo's older backups
     * there beyond [keep]. Files the user put in the folder are never touched.
     */
    suspend fun writeToFolder(folderUri: String, keep: Int, workDir: File) {
        val name = AUTO_PREFIX + NAME_FORMAT.format(clock.now().atZone(ZoneId.systemDefault())) + ".zip"
        val file = withContext(ioDispatcher) { documents.createInFolder(folderUri, name, ZIP_MIME) }
        try {
            withContext(ioDispatcher) { documents.openOutput(file) }.use { write(it, workDir) }
        } catch (e: Exception) {
            withContext(NonCancellable + ioDispatcher) { documents.delete(file) }
            throw e
        }
        withContext(ioDispatcher) {
            documents.listFolder(folderUri)
                .filter { it.name.startsWith(AUTO_PREFIX) && it.name.endsWith(".zip") }
                .sortedByDescending { it.name }
                .drop(keep.coerceAtLeast(1))
                .forEach { documents.delete(it.uri) }
        }
    }

    /**
     * Reads the backup in [input] and stages it for [PendingRestore]. Nothing in use changes until
     * the app restarts.
     *
     * @throws BackupFormatException if it isn't a backup this version can restore.
     */
    suspend fun stage(input: InputStream): BackupInfo = withContext(ioDispatcher) {
        val dir = directories.restoreStaging
        dir.deleteRecursively()
        dir.mkdirs()
        try {
            var manifest: Manifest? = null
            var hasDatabase = false
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    val parts = name.split('/')
                    // Only known, flat entries: nothing may escape the staging directory.
                    val safe = parts.size <= 2 && parts.none { it.isEmpty() || it == "." || it == ".." }
                    if (entry.isDirectory || !safe) continue
                    when {
                        name == MANIFEST -> manifest = runCatching {
                            json.decodeFromString(Manifest.serializer(), zip.readBytes().decodeToString())
                        }.getOrNull()
                        parts.size == 2 && parts[0] in listOf(DATABASE, PREFERENCES, MEDIA) -> {
                            val target = File(File(dir, parts[0]).apply { mkdirs() }, parts[1])
                            target.outputStream().use { zip.copyTo(it) }
                            if (parts[0] == DATABASE) hasDatabase = true
                        }
                    }
                }
            }
            val m = manifest ?: throw BackupFormatException("No manifest")
            if (m.format != FORMAT || m.version > VERSION) throw BackupFormatException("Not a Mnemo backup this version can read")
            if (m.schemaVersion > snapshot.version()) throw BackupFormatException("The backup is from a newer version of Mnemo")
            if (!hasDatabase) throw BackupFormatException("The backup has no database")
            File(dir, PendingRestore.READY).createNewFile()
            BackupInfo(Instant.parse(m.createdAt), m.schemaVersion)
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    private fun preferencesFile(): File = directories.dataStoreFile(DataStoreFiles.USER_PREFERENCES)

    private inline fun ZipOutputStream.entry(name: String, write: (OutputStream) -> Unit) {
        putNextEntry(ZipEntry(name))
        write(this)
        closeEntry()
    }

    @Serializable
    internal data class Manifest(val format: String, val version: Int, val schemaVersion: Int, val createdAt: String)

    internal companion object {
        const val FORMAT = "mnemo-backup"
        const val VERSION = 1
        const val MANIFEST = "manifest.json"
        const val DATABASE = "database"
        const val PREFERENCES = "preferences"
        const val MEDIA = "media"
        const val ZIP_MIME = "application/zip"
        /** Automatic backups have their own prefix, so pruning never deletes a manual one. */
        const val AUTO_PREFIX = "mnemo-auto-backup-"
        private const val MANUAL_PREFIX = "mnemo-backup-"
        private val NAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

        /** A file name for a manual backup made at [now]. */
        fun suggestedName(now: Instant): String = MANUAL_PREFIX + NAME_FORMAT.format(now.atZone(ZoneId.systemDefault())) + ".zip"
    }
}
