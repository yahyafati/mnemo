package com.yahyafati.mnemo.core.sync.webdav

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okhttp3.Credentials
import okio.Buffer
import java.net.URLEncoder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A small WebDAV server on a MockWebServer, shaped like Nextcloud's (SabreDAV): a folder at [folderPath] whose files are
 * listed by `PROPFIND` as a `207` with percent-encoded absolute hrefs, `PUT` with `If-None-Match: *` answered `412` when the
 * name is taken, `MKCOL`, `DELETE`, and Basic authentication. [injected] answers the next requests instead (for failures).
 */
internal class FakeWebDavServer(
    val folderPath: String = "/remote.php/dav/files/alice/Mnemo Sync/",
    val user: String = "alice",
    val password: String = "app-pass-word",
) : Dispatcher() {
    val files = LinkedHashMap<String, ByteArray>()
    var folderExists = true

    /** Every request as "METHOD /decoded/path". */
    val requests = CopyOnWriteArrayList<String>()
    val headers = CopyOnWriteArrayList<okhttp3.Headers>()
    val injected = ConcurrentLinkedQueue<MockResponse>()

    /** Folders other than the sync folder that exist (`MKCOL` of the sync folder needs its parent). */
    var parentExists = true

    /** Answers `204` to every `PUT` of a new file and replaces a file silently, like a server that ignores `If-None-Match`. */
    var ignoresIfNoneMatch = false

    /** Sends hrefs as full addresses on another host, as a server behind a proxy does. */
    var absoluteHrefs: String? = null

    /** A namespace prefix other than `d`, to prove the reader doesn't depend on it. */
    var prefix = "d"

    @Synchronized
    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        requests += "${request.method} ${java.net.URLDecoder.decode(path, "UTF-8")}"
        headers += request.headers
        injected.poll()?.let { return it }
        if (request.headers["Authorization"] != Credentials.basic(user, password, Charsets.UTF_8)) {
            return MockResponse.Builder().code(401).addHeader("WWW-Authenticate", "Basic realm=\"test\"").build()
        }
        val decoded = java.net.URLDecoder.decode(path.replace("+", "%2B"), "UTF-8")
        val name = decoded.removePrefix(folderPath).takeIf { decoded.startsWith(folderPath) && it.isNotEmpty() && '/' !in it }
        return when {
            decoded.trimEnd('/') + "/" == folderPath -> folder(request)
            name == null -> MockResponse.Builder().code(404).build()
            !folderExists -> MockResponse.Builder().code(if (request.method == "PUT") 409 else 404).build()
            else -> file(request, name)
        }
    }

    private fun folder(request: RecordedRequest): MockResponse = when (request.method) {
        "PROPFIND" -> if (!folderExists) {
            MockResponse.Builder().code(404).build()
        } else {
            val depth = request.headers["Depth"]
            multistatus(if (depth == "0") emptyList() else files.keys.toList())
        }
        "MKCOL" -> when {
            folderExists -> MockResponse.Builder().code(405).build()
            !parentExists -> MockResponse.Builder().code(409).build()
            else -> {
                folderExists = true
                MockResponse.Builder().code(201).build()
            }
        }
        else -> MockResponse.Builder().code(405).build()
    }

    private fun file(request: RecordedRequest, name: String): MockResponse = when (request.method) {
        "GET" -> files[name]?.let { MockResponse.Builder().code(200).body(Buffer().write(it)).build() } ?: MockResponse.Builder().code(404).build()
        "PUT" -> {
            val bytes = request.body?.toByteArray() ?: ByteArray(0)
            val existed = name in files
            if (existed && request.headers["If-None-Match"] == "*" && !ignoresIfNoneMatch) {
                MockResponse.Builder().code(412).build()
            } else {
                files[name] = bytes
                MockResponse.Builder().code(if (existed) 204 else 201).build()
            }
        }
        "DELETE" -> if (files.remove(name) != null) MockResponse.Builder().code(204).build() else MockResponse.Builder().code(404).build()
        else -> MockResponse.Builder().code(405).build()
    }

    /** The folder itself first (as a real server does), then [names]. */
    private fun multistatus(names: List<String>): MockResponse {
        val p = prefix
        val origin = absoluteHrefs.orEmpty()
        fun href(path: String) = origin + path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val body = buildString {
            append("""<?xml version="1.0"?>""")
            append("""<$p:multistatus xmlns:$p="DAV:" xmlns:s="http://sabredav.org/ns">""")
            append("""<$p:response><$p:href>${href(folderPath)}</$p:href><$p:propstat><$p:prop><$p:resourcetype><$p:collection/></$p:resourcetype></$p:prop><$p:status>HTTP/1.1 200 OK</$p:status></$p:propstat></$p:response>""")
            names.forEach {
                append("""<$p:response><$p:href>${href(folderPath + it)}</$p:href><$p:propstat><$p:prop><$p:resourcetype/></$p:prop><$p:status>HTTP/1.1 200 OK</$p:status></$p:propstat></$p:response>""")
            }
            append("</$p:multistatus>")
        }
        return MockResponse.Builder().code(207).addHeader("Content-Type", "application/xml; charset=utf-8").body(body).build()
    }
}
