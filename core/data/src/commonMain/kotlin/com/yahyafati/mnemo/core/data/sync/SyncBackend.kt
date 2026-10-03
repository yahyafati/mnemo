package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.common.platform.DocumentAccess
import com.yahyafati.mnemo.core.model.AiEndpoint
import com.yahyafati.mnemo.core.sync.SyncStore
import com.yahyafati.mnemo.core.sync.store.FolderSyncStore
import com.yahyafati.mnemo.core.sync.webdav.WebDavUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where a sync location is (ADR 0013): the description a device keeps of it. Secrets that a backend needs (a token,
 * a password) are never in here: they go in `SecretStore`.
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

    /**
     * A folder on a WebDAV server such as Nextcloud (S7): [url] is the folder's address (with a trailing slash), [username]
     * the account. The password (an app password) is in `SecretStore`, never here.
     */
    @Serializable
    @SerialName("webdav")
    data class WebDav(val url: String, val username: String) : SyncBackend
}

/**
 * Whether two backends are used with one and the same stored credential, so that giving up the older one when the newer is
 * set up would take the newer one's access with it: there is one Google sign-in and one WebDAV password per device.
 */
internal fun SyncBackend.sharesAccessWith(other: SyncBackend): Boolean = when (this) {
    is SyncBackend.Folder -> false
    SyncBackend.GoogleDrive -> other == SyncBackend.GoogleDrive
    is SyncBackend.WebDav -> other is SyncBackend.WebDav
}

/** The address rule of a WebDAV location, for the form that asks for it: HTTPS, or HTTP to a host on this device or the local network. */
object WebDavAddress {
    fun check(url: String): AiEndpoint.Check = WebDavUrl.check(url)
}

/** What "Test connection" found on a WebDAV server (S7). Wrong passwords, unreachable servers and the like are `SyncException`s instead. */
enum class WebDavTestResult {
    /** The server answered, the user name and password work, and the folder is there. */
    FolderFound,

    /** The same, but the folder isn't there yet: setting up creates it. */
    FolderWillBeCreated,
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

    /** Looks at the WebDAV folder at [url] with these credentials and changes nothing (S7). Failures are `SyncException`s. */
    suspend fun testWebDav(url: String, username: String, password: String): WebDavTestResult =
        throw UnsupportedOperationException("WebDAV isn't available here")

    /**
     * Checks the credentials, makes the folder if it isn't there and keeps the password; returns the backend to set up
     * with it. Failures are `SyncException`s and keep nothing.
     */
    suspend fun connectWebDav(url: String, username: String, password: String): SyncBackend.WebDav =
        throw UnsupportedOperationException("WebDAV isn't available here")
}

/** The stores of this version: folders, through the platform's [DocumentAccess], Google Drive and WebDAV. */
internal class DocumentSyncStores(
    private val documents: DocumentAccess,
    private val google: GoogleDriveAccess,
    private val webDav: WebDavAccess,
) : SyncStores {
    override fun open(backend: SyncBackend): SyncStore = when (backend) {
        is SyncBackend.Folder -> FolderSyncStore(documents, backend.location)
        SyncBackend.GoogleDrive -> google.openStore()
        is SyncBackend.WebDav -> webDav.openStore(backend)
    }

    override fun retain(backend: SyncBackend) {
        if (backend is SyncBackend.Folder) documents.keepAccess(backend.location)
    }

    override suspend fun release(backend: SyncBackend) {
        when (backend) {
            is SyncBackend.Folder -> documents.releaseAccess(backend.location)
            SyncBackend.GoogleDrive -> google.signOut()
            is SyncBackend.WebDav -> webDav.forget()
        }
    }

    override val googleDriveAvailable: Boolean get() = google.isAvailable

    override suspend fun signInToGoogleDrive() = google.signIn()

    override suspend fun testWebDav(url: String, username: String, password: String) = webDav.test(url, username, password)

    override suspend fun connectWebDav(url: String, username: String, password: String) = webDav.connect(url, username, password)
}
