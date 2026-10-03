package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.common.platform.AppDirectories
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * What this device knows about the location it syncs with. [collectionId] is the location's own (`sync.json`): a
 * different one later means somebody started the sync data again. [lastSyncAt] and [lastMaintenanceAt] are epoch
 * milliseconds of the last round that finished, and of the last time snapshots and clean-up were considered.
 */
@Serializable
internal data class SyncConfig(
    val backend: SyncBackend,
    val collectionId: String,
    val encrypted: Boolean,
    val lastSyncAt: Long? = null,
    val lastMaintenanceAt: Long? = null,
)

/**
 * [SyncConfig] in `<files>/sync/config.json`. It is not in the database or the preferences on purpose: those are what a
 * backup restores, and a restore must not bring another device's idea of the location. After a restore the config is
 * what is still here while the database says sync is off, which is how the app knows to ask what to do ([SyncStatus.Restored]).
 * A file that can't be read is the same as none.
 */
internal class SyncConfigStore(
    private val directories: AppDirectories,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun file() = File(File(directories.files, DIRECTORY), FILE)

    suspend fun read(): SyncConfig? = withContext(ioDispatcher) {
        val file = file()
        if (!file.isFile) return@withContext null
        try {
            json.decodeFromString(SyncConfig.serializer(), file.readText())
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    suspend fun write(config: SyncConfig) = withContext(ioDispatcher) {
        val file = file()
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "$FILE.tmp")
        temp.writeText(json.encodeToString(SyncConfig.serializer(), config))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        Unit
    }

    suspend fun clear() {
        withContext(ioDispatcher) { file().delete() }
    }

    private companion object {
        const val DIRECTORY = "sync"
        const val FILE = "config.json"
    }
}
