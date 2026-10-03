package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.security.SecretIds
import com.yahyafati.mnemo.core.security.SecretStore
import com.yahyafati.mnemo.core.security.StoredSecret
import com.yahyafati.mnemo.core.sync.SyncIoException
import com.yahyafati.mnemo.core.sync.SyncStore
import com.yahyafati.mnemo.core.sync.webdav.WebDavFolder
import com.yahyafati.mnemo.core.sync.webdav.WebDavSyncStore
import com.yahyafati.mnemo.core.sync.webdav.WebDavUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Everything about the account of a WebDAV sync location (docs/sync/ROADMAP.md S7): testing and keeping a password, which
 * lives only in [SecretStore] (never in Room, the sync config, a backup or a log), handing out stores that read it, and
 * forgetting it. There is one WebDAV password per device, like one Google sign-in.
 */
internal class WebDavAccess(
    private val secrets: SecretStore,
    private val http: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher,
) {
    /** A store on a location whose password is already kept. A stored address that isn't usable any more is an I/O problem, not a crash. */
    fun openStore(backend: SyncBackend.WebDav): SyncStore {
        if (WebDavUrl.normalize(backend.url) == null) throw SyncIoException("The WebDAV address isn't usable")
        return WebDavSyncStore(http, backend.url, backend.username, ::storedPassword)
    }

    /** Looks at the folder with [password] and changes nothing, keeps nothing. */
    suspend fun test(url: String, username: String, password: String): WebDavTestResult = withContext(ioDispatcher) {
        when (storeFor(url, username, password).probe()) {
            WebDavFolder.Exists -> WebDavTestResult.FolderFound
            WebDavFolder.Missing -> WebDavTestResult.FolderWillBeCreated
        }
    }

    /** Makes the folder if needed, then keeps [password]: a wrong one is refused before anything is kept. */
    suspend fun connect(url: String, username: String, password: String): SyncBackend.WebDav {
        val normal = requireNotNull(WebDavUrl.normalize(url)) { "Not a usable WebDAV address" }
        val user = username.trim()
        withContext(ioDispatcher) { storeFor(url, user, password).ensureFolder() }
        secrets.put(SecretIds.WEBDAV_PASSWORD, password)
        return SyncBackend.WebDav(normal.toString(), user)
    }

    suspend fun forget() = secrets.remove(SecretIds.WEBDAV_PASSWORD)

    private fun storeFor(url: String, username: String, password: String) = WebDavSyncStore(http, url, username.trim(), { password })

    /** Called by a store, on an IO thread, with every request. */
    private fun storedPassword(): String? = runBlocking { (secrets.get(SecretIds.WEBDAV_PASSWORD) as? StoredSecret.Present)?.value }
}
