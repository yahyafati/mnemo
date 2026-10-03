package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.common.platform.DocumentAccess
import com.yahyafati.mnemo.core.sync.SyncStore
import com.yahyafati.mnemo.core.sync.store.FolderSyncStore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where a sync location is (ADR 0013): the description a device keeps of it. Secrets that a backend needs (a token,
 * a password) are never in here: they go in `SecretStore`. Google Drive (S6) and WebDAV (S7) add their cases.
 */
@Serializable
sealed interface SyncBackend {
    /** A folder the user picked: [location] is what the platform's folder picker returned. */
    @Serializable
    @SerialName("folder")
    data class Folder(val location: String) : SyncBackend
}

/** Opens the [SyncStore] of a [SyncBackend], and keeps or gives up the access it needs between runs. */
interface SyncStores {
    fun open(backend: SyncBackend): SyncStore

    /** Called when a device starts syncing with [backend]: keep the access across restarts (a picked folder). */
    fun retain(backend: SyncBackend) = Unit

    /** Called when it stops. */
    fun release(backend: SyncBackend) = Unit
}

/** The stores of this version: folders, through the platform's [DocumentAccess]. */
internal class DocumentSyncStores(private val documents: DocumentAccess) : SyncStores {
    override fun open(backend: SyncBackend): SyncStore = when (backend) {
        is SyncBackend.Folder -> FolderSyncStore(documents, backend.location)
    }

    override fun retain(backend: SyncBackend) {
        if (backend is SyncBackend.Folder) documents.keepAccess(backend.location)
    }

    override fun release(backend: SyncBackend) {
        if (backend is SyncBackend.Folder) documents.releaseAccess(backend.location)
    }
}
