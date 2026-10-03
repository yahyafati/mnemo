package com.yahyafati.mnemo.core.sync.webdav

import com.yahyafati.mnemo.core.model.AiEndpoint
import com.yahyafati.mnemo.core.sync.SyncAlreadyExistsException
import com.yahyafati.mnemo.core.sync.SyncAuthException
import com.yahyafati.mnemo.core.sync.SyncException
import com.yahyafati.mnemo.core.sync.SyncIoException
import com.yahyafati.mnemo.core.sync.SyncNotFoundException
import com.yahyafati.mnemo.core.sync.SyncOfflineException
import com.yahyafati.mnemo.core.sync.SyncPaths
import com.yahyafati.mnemo.core.sync.SyncQuotaException
import com.yahyafati.mnemo.core.sync.SyncStore
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.nio.charset.StandardCharsets

/** The address rules of a WebDAV location (ADR 0013): the same as an AI provider's, with every address "local" for HTTP's sake. */
object WebDavUrl {
    /** Whether [url] may be used: an absolute `https://` address, or `http://` to a host on this device or the local network. */
    fun check(url: String): AiEndpoint.Check {
        val result = AiEndpoint.check(url.trim(), isLocal = true)
        if (result != AiEndpoint.Check.Ok) return result
        val parsed = url.trim().toHttpUrlOrNull() ?: return AiEndpoint.Check.Invalid
        // Credentials belong in their own fields, not in a link that may be shown or logged; a query or fragment names nothing.
        return if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty() || parsed.query != null || parsed.fragment != null) {
            AiEndpoint.Check.Invalid
        } else {
            AiEndpoint.Check.Ok
        }
    }

    /** The folder's address with a trailing slash (a collection's address in WebDAV), or null if [check] refuses it. */
    fun normalize(url: String): HttpUrl? {
        if (check(url) != AiEndpoint.Check.Ok) return null
        val parsed = url.trim().toHttpUrlOrNull() ?: return null
        return if (parsed.encodedPath.endsWith("/")) parsed else parsed.newBuilder().addPathSegment("").build()
    }
}

/** What a folder on the server is, as [WebDavSyncStore.probe] found it. */
enum class WebDavFolder {
    /** The folder is there and the credentials work. */
    Exists,

    /** The server answered and the credentials work, but there is no folder at the address (yet). */
    Missing,
}

/**
 * A sync location in a folder on a WebDAV server such as Nextcloud (docs/sync/ROADMAP.md S7, ADR 0013), over OkHttp.
 *
 * The layout is flattened into the one folder the user named (`/` is `__` in a file name), like [com.yahyafati.mnemo.core.sync.store.FolderSyncStore],
 * so nothing but that folder is ever created. A new file is a `PUT` with `If-None-Match: *`, which a server answers with
 * `412` when the name is taken: that is the atomic claim the contract needs (two devices creating `sync.json`). A server
 * that ignores the header answers `204` instead of `201`; the write still succeeds, and the claim is then only as good as
 * the check that the folder is empty before it. `overwrite` is a plain `PUT`, `delete` forgives a missing file, and a
 * listing is `PROPFIND` with `Depth: 1`.
 *
 * Basic authentication, sent with every request: use an app password, not the account's. Failures are [SyncException]s:
 * no connection is offline; `401` and `403` are auth; `404` is not found (a deleted folder, a missing file); `507` is
 * quota; `429` and `5xx` are retried with a pause; a redirect is an error that says to use the final address, since a
 * request that followed one would send the password along. The address is [WebDavUrl]'s rule: HTTPS, or HTTP to a local host.
 */
class WebDavSyncStore(
    http: OkHttpClient,
    url: String,
    private val username: String,
    private val password: () -> String?,
    private val pause: (millis: Long) -> Unit = { Thread.sleep(it) },
) : SyncStore {
    private val http = http.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val base: HttpUrl = requireNotNull(WebDavUrl.normalize(url)) { "Not a usable WebDAV address" }

    override fun list(prefix: String): List<String> {
        val reply = execute("list the files", accept = { it == MULTI_STATUS }) { propfind(base, depth = 1) }
        val entries = WebDavMultistatus.parse(reply.body.decodeToString())
            ?: throw SyncIoException("The server's answer to the file listing couldn't be read: is the address a WebDAV folder?")
        return entries.asSequence()
            .filterNot { it.isCollection }
            .mapNotNull { entry -> nameIn(entry.href) }
            .mapNotNull { name -> name.replace(SEPARATOR, "/").takeIf(SyncPaths::isValid) }
            .filter { it.startsWith(prefix) }
            .distinct()
            .sorted()
            .toList()
    }

    override fun read(path: String): ByteArray {
        val url = urlOf(path)
        return execute("read a file") { Request.Builder().url(url).get() }.body
    }

    override fun write(path: String, bytes: ByteArray) {
        val url = urlOf(path)
        val reply = execute("write a file", accept = { it in 200..299 || it == PRECONDITION_FAILED }) {
            Request.Builder().url(url).header("If-None-Match", "*").put(bytes.toRequestBody(BINARY))
        }
        if (reply.code == PRECONDITION_FAILED) throw SyncAlreadyExistsException("$path exists")
    }

    override fun overwrite(path: String, bytes: ByteArray) {
        val url = urlOf(path)
        execute("replace a file") { Request.Builder().url(url).put(bytes.toRequestBody(BINARY)) }
    }

    override fun delete(path: String) {
        val url = urlOf(path)
        try {
            execute("delete a file") { Request.Builder().url(url).delete() }
        } catch (_: SyncNotFoundException) {
            // Gone already.
        }
    }

    // --- the folder -----------------------------------------------------------------------------------------------

    /**
     * Looks at the folder without changing anything: [WebDavFolder.Exists], or [WebDavFolder.Missing] when the server
     * answered and the credentials work but the address has no folder. This is "Test connection"; [SyncAuthException],
     * [SyncOfflineException] and the others say what is wrong otherwise.
     */
    fun probe(): WebDavFolder = try {
        val reply = execute("look at the folder", accept = { it == MULTI_STATUS }) { propfind(base, depth = 0) }
        if (WebDavMultistatus.parse(reply.body.decodeToString()) == null) {
            throw SyncIoException("The server's answer couldn't be read: is the address a WebDAV folder?")
        }
        WebDavFolder.Exists
    } catch (_: SyncNotFoundException) {
        WebDavFolder.Missing
    }

    /** Makes the folder if it isn't there (one level: its parent has to exist, or [SyncNotFoundException]). */
    fun ensureFolder() {
        if (probe() == WebDavFolder.Exists) return
        // 405 is what servers say to a MKCOL on something that exists: another device made it a moment ago.
        val reply = execute("create the folder", accept = { it in 200..299 || it == METHOD_NOT_ALLOWED || it == CONFLICT }) {
            Request.Builder().url(base).method("MKCOL", ByteArray(0).toRequestBody(null))
        }
        if (reply.code == CONFLICT) throw SyncNotFoundException("The folder above the sync folder doesn't exist")
    }

    // --- requests -------------------------------------------------------------------------------------------------

    private class Reply(val code: Int, val body: ByteArray)

    /**
     * Runs a request with the credentials. Answers [accept] says yes to are returned; rate limits and server errors are
     * retried with a growing pause; anything else is mapped to a [SyncException].
     */
    private fun execute(what: String, accept: (Int) -> Boolean = { it in 200..299 }, build: () -> Request.Builder): Reply {
        val secret = password() ?: throw SyncAuthException("There is no password for the WebDAV server")
        val authorization = Credentials.basic(username, secret, StandardCharsets.UTF_8)
        var attempt = 0
        while (true) {
            val reply = try {
                http.newCall(build().header("Authorization", authorization).build()).execute().use { Reply(it.code, it.body.bytes()) }
            } catch (e: IOException) {
                throw SyncOfflineException("Couldn't reach the WebDAV server ($what)", e)
            }
            when {
                accept(reply.code) -> return reply
                (reply.code == TOO_MANY_REQUESTS || reply.code == 502 || reply.code == 503 || reply.code == 504) && attempt < MAX_RETRIES ->
                    pause(RETRY_PAUSE_MS shl attempt++)
                else -> throw failure(what, reply.code)
            }
        }
    }

    private fun failure(what: String, code: Int): SyncException = when (code) {
        401 -> SyncAuthException("The WebDAV server refused the user name or password ($what)")
        403 -> SyncAuthException("The WebDAV server refused access ($what)")
        404 -> SyncNotFoundException("Not found on the WebDAV server ($what)")
        507 -> SyncQuotaException("The WebDAV server is full ($what)")
        413 -> SyncIoException("The WebDAV server refuses a file this large ($what)")
        423 -> SyncIoException("The file is locked on the WebDAV server ($what)")
        405, 501 -> SyncIoException("The server doesn't do WebDAV at this address ($what)")
        301, 302, 303, 307, 308 -> SyncIoException("The server moved the sync folder: use its final address ($what)")
        TOO_MANY_REQUESTS, 502, 503, 504 -> SyncIoException("The WebDAV server is unavailable ($code, $what)")
        else -> SyncIoException("The WebDAV server refused the request ($code, $what)")
    }

    private fun propfind(url: HttpUrl, depth: Int): Request.Builder = Request.Builder().url(url)
        .header("Depth", depth.toString())
        .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML))

    // --- names ----------------------------------------------------------------------------------------------------

    private fun urlOf(path: String): HttpUrl {
        SyncPaths.requireValid(path)
        return base.newBuilder().addPathSegment(path.replace("/", SEPARATOR)).build()
    }

    /**
     * The file name of a listing's [href] if it is a file directly in the folder, else null. An href is absolute, a full
     * address or relative, and may be encoded differently from how the folder was typed: comparing decoded path segments
     * gets past all of that, and only the path counts (the host is whatever the server is behind).
     */
    private fun nameIn(href: String): String? {
        val segments = base.resolve(href)?.pathSegments ?: return null
        val folder = base.pathSegments.dropLastWhile { it.isEmpty() }
        if (segments.size != folder.size + 1 || segments.subList(0, folder.size) != folder) return null
        return segments.last().takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val SEPARATOR = "__"
        const val MULTI_STATUS = 207
        const val PRECONDITION_FAILED = 412
        const val METHOD_NOT_ALLOWED = 405
        const val CONFLICT = 409
        const val TOO_MANY_REQUESTS = 429
        const val MAX_RETRIES = 4
        const val RETRY_PAUSE_MS = 500L
        const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
        val XML = "application/xml; charset=utf-8".toMediaType()
        val BINARY = "application/octet-stream".toMediaType()
    }
}
