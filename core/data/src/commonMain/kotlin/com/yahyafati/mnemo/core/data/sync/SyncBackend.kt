package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.common.platform.DocumentAccess
import com.yahyafati.mnemo.core.sync.SyncStore
import com.yahyafati.mnemo.core.sync.store.FolderSyncStore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where a sync location is (ADR 0013): the description a device keeps of it. Secrets that a backend needs (a token,
 * a password) are never in here: they go in `SecretStore`. WebDAV (S7) adds its case.
 */
@Serializable
sealed interface SyncBackend {
    /** A folder the user picked: [location] is what the platform's folder picker returned. */
    @Serializable
    @SerialName("folder")
    data class Folder(val location: String) : SyncBackend

    /**
     * The hidden app-data folder of the user's Google Drive (S6). One account per device: which one is a matter of the
     * refresh token in `SecretStore`, so nothing about it is stored here.
     */
    @Serializable
    @SerialName("gdrive")
    data object GoogleDrive : SyncBackend
}

/**
 * Opens the [SyncStore] of a [SyncBackend], keeps or gives up the access it needs between runs, and signs in to the
 * backends that need an account.
 */
interface SyncStores {
    fun open(backend: SyncBackend): SyncStore

    /** Called when a device starts syncing with [backend]: keep the access across restarts (a picked folder). */
    fun retain(backend: SyncBackend) = Unit

    /** Called when it stops: give the access up (a folder permission, a Google sign-in). */
    suspend fun release(backend: SyncBackend) = Unit

    /** Whether this build can sync through Google Drive: it was built with a Google client (ADR 0013). */
    val googleDriveAvailable: Boolean get() = false

    /**
     * Signs in to Google in the browser and keeps the refresh token. `SyncSignInCancelledException` if the user gives
     * up; other `SyncException`s if Google refuses or can't be reached. Only if [googleDriveAvailable].
     */
    suspend fun signInToGoogleDrive(): Unit = throw UnsupportedOperationException("Google Drive isn't available in this build")
}

/** The stores of this version: folders, through the platform's [DocumentAccess], and Google Drive. */
internal class DocumentSyncStores(
    private val documents: DocumentAccess,
    private val google: GoogleDriveAccess,
) : SyncStores {
    override fun open(backend: SyncBackend): SyncStore = when (backend) {
        is SyncBackend.Folder -> FolderSyncStore(documents, backend.location)
        SyncBackend.GoogleDrive -> google.openStore()
    }

    override fun retain(backend: SyncBackend) {
        if (backend is SyncBackend.Folder) documents.keepAccess(backend.location)
    }

    override suspend fun release(backend: SyncBackend) {
        when (backend) {
            is SyncBackend.Folder -> documents.releaseAccess(backend.location)
            SyncBackend.GoogleDrive -> google.signOut()
        }
    }

    override val googleDriveAvailable: Boolean get() = google.isAvailable

    override suspend fun signInToGoogleDrive() = google.signIn()
}
