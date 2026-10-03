package com.yahyafati.mnemo.core.sync.google

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okio.Buffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A small Google Drive v3 on a MockWebServer: just the app-data folder calls the store makes (list with `q` and paging,
 * get, delete, multipart / media / resumable uploads), with the behaviours that matter to it: a bearer token that must
 * match, ids and creation times, and hooks to make things fail.
 */
internal class FakeDriveServer : Dispatcher() {
    class DriveFile(val id: String, val name: String, var bytes: ByteArray, val group: String, val created: String)

    private class Session(val id: String?, val name: String?, val group: String?, val total: Int, val received: java.io.ByteArrayOutputStream = java.io.ByteArrayOutputStream())

    val files = LinkedHashMap<String, DriveFile>()

    /** Every request as "METHOD /path?query", in order. */
    val requests = CopyOnWriteArrayList<String>()

    /** The headers of every request, parallel to [requests]. */
    val headers = CopyOnWriteArrayList<okhttp3.Headers>()

    /** Answered, in order, instead of handling the next requests (for failures). */
    val injected = ConcurrentLinkedQueue<MockResponse>()

    var validToken = "token-1"
    var pageSize = 3
    var quotaFull = false

    /** The next create also finds an older file of the same name, as if another device created it a moment before. */
    var raceOnNextCreate = false

    /** The Nth chunk (1-based, counted over all sessions) of a resumable upload is answered with a 503, once. */
    var failChunk: Int? = null
    private var chunks = 0

    /** Where a resumable session's address points (null: this server). */
    var sessionBase: String? = null

    lateinit var baseUrl: String

    private var counter = 0
    private val sessions = HashMap<String, Session>()

    fun seed(name: String, bytes: ByteArray = byteArrayOf(1), group: String = groupOfName(name), created: String = stamp()): DriveFile =
        DriveFile("id-${++counter}", name, bytes, group, created).also { files[it.id] = it }

    private fun stamp() = "2026-01-01T00:%02d:%02d.000Z".format(counter / 60, counter % 60)

    private fun groupOfName(name: String) = name.substringBefore("__").takeIf { it in listOf("devices", "snapshots", "media") } ?: "root"

    @Synchronized
    override fun dispatch(request: RecordedRequest): MockResponse {
        requests += "${request.method} ${request.target}"
        headers += request.headers
        injected.poll()?.let { return it }
        val url = request.url
        val path = url.encodedPath
        // A resumable session address carries its own authorization.
        if (!path.startsWith("/upload/session/") && request.headers["Authorization"] != "Bearer $validToken") {
            return error(401, "authError", "Invalid Credentials")
        }
        val segments = path.trim('/').split('/')
        return when {
            path == "/drive/v3/files" && request.method == "GET" -> list(url)
            segments.size == 4 && segments[0] == "drive" && segments[2] == "files" -> {
                val file = files[segments[3]] ?: return error(404, "notFound", "File not found")
                when (request.method) {
                    "GET" -> if (url.queryParameter("alt") == "media") ok(file.bytes) else json("""{"id":"${file.id}"}""")
                    "DELETE" -> {
                        files.remove(file.id)
                        MockResponse.Builder().code(204).build()
                    }
                    else -> error(405, "badRequest", "no")
                }
            }
            path == "/upload/drive/v3/files" && request.method == "POST" -> upload(request, null)
            segments.size == 5 && segments[0] == "upload" && segments[3] == "files" && request.method == "PATCH" -> {
                if (files[segments[4]] == null) error(404, "notFound", "File not found") else upload(request, segments[4])
            }
            path.startsWith("/upload/session/") && request.method == "PUT" -> chunk(request, segments.last())
            else -> error(404, "notFound", "No such call: ${request.requestLine}")
        }
    }

    // --- list ---------------------------------------------------------------------------------------------------

    private fun list(url: okhttp3.HttpUrl): MockResponse {
        if (url.queryParameter("spaces") != "appDataFolder") return error(400, "badRequest", "spaces")
        val q = url.queryParameter("q")
        var matching = files.values.toList()
        if (q != null) {
            Regex("name = '([^']+)'").matchEntire(q)?.let { m -> matching = matching.filter { it.name == m.groupValues[1] } }
                ?: Regex("appProperties has \\{ key='g' and value='([^']+)' \\}").matchEntire(q)?.let { m -> matching = matching.filter { it.group == m.groupValues[1] } }
                ?: return error(400, "invalidQuery", "Invalid Value")
        }
        val start = url.queryParameter("pageToken")?.toInt() ?: 0
        val size = minOf(pageSize, url.queryParameter("pageSize")?.toInt() ?: pageSize)
        val page = matching.drop(start).take(size)
        val next = if (start + size < matching.size) ",\"nextPageToken\":\"${start + size}\"" else ""
        val items = page.joinToString(",") { """{"id":"${it.id}","name":"${it.name}","createdTime":"${it.created}"}""" }
        return json("""{"files":[$items]$next}""")
    }

    // --- upload -------------------------------------------------------------------------------------------------

    private fun upload(request: RecordedRequest, id: String?): MockResponse {
        if (quotaFull) return error(403, "storageQuotaExceeded", "The user's Drive storage quota has been exceeded.")
        return when (request.url.queryParameter("uploadType")) {
            "multipart" -> {
                val (metadata, bytes) = parseMultipart(request)
                val name = Regex("\"name\":\"([^\"]+)\"").find(metadata)!!.groupValues[1]
                val group = Regex("\"g\":\"([^\"]+)\"").find(metadata)!!.groupValues[1]
                check("appDataFolder" in metadata) { "A file must be created in the app data folder" }
                create(name, group, bytes)
            }
            "media" -> {
                files.getValue(id!!).bytes = request.body!!.toByteArray()
                json("""{"id":"$id"}""")
            }
            "resumable" -> {
                val metadata = request.body!!.utf8()
                val session = "s${++counter}"
                sessions[session] = Session(
                    id = id,
                    name = Regex("\"name\":\"([^\"]+)\"").find(metadata)?.groupValues?.get(1),
                    group = Regex("\"g\":\"([^\"]+)\"").find(metadata)?.groupValues?.get(1),
                    total = request.headers["X-Upload-Content-Length"]!!.toInt(),
                )
                MockResponse.Builder().code(200).addHeader("Location", "${sessionBase ?: baseUrl}upload/session/$session").build()
            }
            else -> error(400, "badRequest", "uploadType")
        }
    }

    private fun chunk(request: RecordedRequest, sessionId: String): MockResponse {
        val session = sessions[sessionId] ?: return error(404, "notFound", "No such session")
        val range0 = request.headers["Content-Range"]
        if (range0 != null && !range0.startsWith("bytes */") && ++chunks == failChunk) {
            failChunk = null
            return MockResponse.Builder().code(503).build()
        }
        val range = request.headers["Content-Range"] ?: return error(400, "badRequest", "Content-Range")
        if (!range.startsWith("bytes */")) {
            val from = range.removePrefix("bytes ").substringBefore('-').toInt()
            if (from != session.received.size()) return error(400, "badRequest", "Wrong offset $from, expected ${session.received.size()}")
            session.received.write(request.body!!.toByteArray())
        }
        if (session.received.size() < session.total) {
            val builder = MockResponse.Builder().code(308)
            if (session.received.size() > 0) builder.addHeader("Range", "bytes=0-${session.received.size() - 1}")
            return builder.build()
        }
        sessions.remove(sessionId)
        val bytes = session.received.toByteArray()
        if (session.id != null) {
            files.getValue(session.id).bytes = bytes
            return json("""{"id":"${session.id}"}""")
        }
        return create(session.name!!, session.group!!, bytes)
    }

    private fun create(name: String, group: String, bytes: ByteArray): MockResponse {
        if (raceOnNextCreate) {
            raceOnNextCreate = false
            seed(name, byteArrayOf(9), group, created = "2025-12-31T23:59:59.000Z")
        }
        return json("""{"id":"${seed(name, bytes, group).id}"}""")
    }

    private fun parseMultipart(request: RecordedRequest): Pair<String, ByteArray> {
        val boundary = request.headers["Content-Type"]!!.substringAfter("boundary=").trim('"')
        val body = request.body!!.toByteArray()
        val delimiter = "--$boundary".toByteArray()
        val parts = ArrayList<ByteArray>()
        var from = indexOf(body, delimiter, 0)
        while (from >= 0) {
            val next = indexOf(body, delimiter, from + delimiter.size)
            if (next < 0) break
            // A part is "\r\n<headers>\r\n\r\n<content>\r\n" between two delimiters.
            val part = body.copyOfRange(from + delimiter.size + 2, next - 2)
            val split = indexOf(part, "\r\n\r\n".toByteArray(), 0)
            parts += part.copyOfRange(split + 4, part.size)
            from = next
        }
        check(parts.size == 2) { "A multipart upload has two parts, not ${parts.size}" }
        return parts[0].decodeToString() to parts[1]
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, start: Int): Int {
        outer@ for (i in start..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    // --- responses ----------------------------------------------------------------------------------------------

    private fun ok(bytes: ByteArray) = MockResponse.Builder().code(200).body(Buffer().write(bytes)).build()

    private fun json(body: String) = MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build()

    companion object {
        fun error(code: Int, reason: String, message: String): MockResponse = MockResponse.Builder()
            .code(code)
            .addHeader("Content-Type", "application/json")
            .body("""{"error":{"code":$code,"message":"$message","errors":[{"reason":"$reason","message":"$message"}]}}""")
            .build()
    }
}
