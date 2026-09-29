package com.yahyafati.mnemo.core.data.repository

import android.content.ContentResolver
import android.content.Context
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.yahyafati.mnemo.core.common.dispatchers.Dispatcher
import com.yahyafati.mnemo.core.common.dispatchers.MnemoDispatchers
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.database.dao.MediaDao
import com.yahyafati.mnemo.core.database.dao.NoteDao
import com.yahyafati.mnemo.core.database.entity.MediaEntity
import com.yahyafati.mnemo.core.model.Media
import com.yahyafati.mnemo.core.model.MediaRef
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

internal class FileMediaRepository(
    override val directory: File,
    private val mediaDao: MediaDao,
    private val noteDao: NoteDao,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
    /** Opens picked files for [importUri]; null in tests that only store streams. */
    private val contentResolver: ContentResolver? = null,
) : MediaRepository {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        mediaDao: MediaDao,
        noteDao: NoteDao,
        clock: Clock,
        @Dispatcher(MnemoDispatchers.IO) ioDispatcher: CoroutineDispatcher,
    ) : this(File(context.filesDir, MediaRef.DIRECTORY), mediaDao, noteDao, clock, ioDispatcher, context.contentResolver)

    override suspend fun importUri(uri: String): Media? = withContext(ioDispatcher) {
        val resolver = contentResolver ?: return@withContext null
        val parsed = uri.toUri()
        val name = runCatching {
            resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: parsed.lastPathSegment?.substringAfterLast('/') ?: "attachment"
        val named = if ('.' in name) {
            name
        } else {
            val extension = resolver.getType(parsed)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            if (extension != null) "$name.$extension" else name
        }
        try {
            resolver.openInputStream(parsed)?.use { store(it, named) }
        } catch (e: java.io.IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    override suspend fun store(input: InputStream, name: String): Media = withContext(ioDispatcher) {
        directory.mkdirs()
        // Hash while copying to a temporary file, then move it into place under its hash.
        val temp = File(directory, ".tmp-${UUID.randomUUID()}")
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        try {
            temp.outputStream().use { out ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                    out.write(buffer, 0, n)
                    size += n
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val target = File(directory, hash)
            if (target.exists()) temp.delete() else check(temp.renameTo(target)) { "Can't store media $name" }

            val existing = mediaDao.get(listOf(hash)).firstOrNull()
            val now = clock.now().toEpochMilli()
            val entity = existing ?: MediaEntity(hash, name.substringAfterLast('/'), MediaRef.mimeTypeFor(name), size, now, now)
            if (existing == null) mediaDao.upsert(listOf(entity))
            entity.toModel()
        } finally {
            temp.delete()
        }
    }

    override suspend fun getAll(): List<Media> = mediaDao.getAll().map { it.toModel() }

    override fun file(hash: String): File = File(directory, hash)

    override suspend fun collectGarbage(): Int = withContext(ioDispatcher) {
        val referenced = HashSet<String>()
        var after = 0L
        while (true) {
            val page = noteDao.getPage(after, PAGE)
            if (page.isEmpty()) break
            page.forEach { row ->
                row.note.fields.forEach { referenced += MediaRef.referencedHashes(it) }
                row.note.hint?.let { referenced += MediaRef.referencedHashes(it) }
            }
            after = page.last().rowId
        }
        val cutoff = clock.now().minus(GRACE)
        val unused = mediaDao.getIdsCreatedBefore(cutoff.toEpochMilli()).filterNot { it in referenced }
        unused.chunked(PAGE).forEach { mediaDao.softDelete(it, clock.now().toEpochMilli()) }
        unused.forEach { file(it).delete() }

        // Files with no live record (e.g. left by a crash mid-import) go too, once they're old.
        val live = mediaDao.getAll().map { it.id }.toSet()
        val strays = directory.listFiles().orEmpty().filter { file ->
            file.isFile && file.name !in live && file.name !in referenced && Instant.ofEpochMilli(file.lastModified()).isBefore(cutoff)
        }
        strays.forEach { it.delete() }
        unused.size + strays.size
    }

    private fun MediaEntity.toModel() = Media(id, name, mimeType, size, Instant.ofEpochMilli(createdAt))

    private companion object {
        const val BUFFER = 64 * 1024
        const val PAGE = 500
        val GRACE: Duration = Duration.ofDays(1)
    }
}
