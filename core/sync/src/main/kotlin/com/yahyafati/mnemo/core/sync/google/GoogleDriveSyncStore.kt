package com.yahyafati.mnemo.core.sync.google

import com.yahyafati.mnemo.core.sync.SyncAlreadyExistsException
import com.yahyafati.mnemo.core.sync.SyncAuthException
import com.yahyafati.mnemo.core.sync.SyncException
import com.yahyafati.mnemo.core.sync.SyncIoException
import com.yahyafati.mnemo.core.sync.SyncNotFoundException
import com.yahyafati.mnemo.core.sync.SyncOfflineException
import com.yahyafati.mnemo.core.sync.SyncPaths
import com.yahyafati.mnemo.core.sync.SyncQuotaException
import com.yahyafati.mnemo.core.sync.SyncStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * A sync location in the hidden app-data folder of the user's Google Drive (docs/sync/ROADMAP.md S6, ADR 0013), over
 * Drive's REST v3 API and OkHttp: no Google library, no Play Services.
 *
 * Drive has no folders Mnemo can see here and allows two files with one name, so, like [FolderSyncStore], the layout is
 * flattened (`/` is `__` in a file name) and a name is **claimed** by creating the file: a create looks first for a file of
 * that name, and for `sync.json` also afterwards, when the older of two files wins (the loser deletes its own and reports
 * [SyncAlreadyExistsException]). Every file carries an app property `g` with its first path segment, so a listing of
 * `media/` or `devices/` asks Drive for only that group instead of the whole folder. A file is created in one request,
 * or in a resumable session above [resumableThreshold]; Drive makes the file only when the last byte arrived, so a cut-off
 * upload leaves nothing under the final name.
 *
 * Failures are [SyncException]s: no network is offline; a 401 gets one new access token and then is auth; a 403 is auth
 * unless its reason says the Drive is full (quota) or that Google asks us to slow down (retried with a pause, as are 429
 * and 5xx); a 404 is not found. Redirects are never followed, and the session address of a resumable upload is only used
 * if it is on the upload host.
 */
class GoogleDriveSyncStore(
    http: OkHttpClient,
    private val tokens: AccessTokenSource,
    private val endpoints: GoogleEndpoints = GoogleEndpoints(),
    private val resumableThreshold: Int = RESUMABLE_THRESHOLD,
    private val chunkSize: Int = CHUNK_SIZE,
    private val pause: (millis: Long) -> Unit = { Thread.sleep(it) },
) : SyncStore {
    private val http = http.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val json = Json { ignoreUnknownKeys = true }

    /** path → file id, from listings and writes. */
    private val ids = HashMap<String, String>()

    init {
        require(chunkSize > 0) { "chunkSize must be positive" }
    }

    @Synchronized
    override fun list(prefix: String): List<String> {
        val paths = LinkedHashMap<String, DriveFile>()
        for (file in listFiles(groupOf(prefix))) {
            val path = pathOf(file.name) ?: continue
            if (!path.startsWith(prefix)) continue
            val known = paths[path]
            if (known == null || file.isOlderThan(known)) paths[path] = file
        }
        paths.forEach { (path, file) -> ids[path] = file.id }
        return paths.keys.sorted()
    }

    @Synchronized
    override fun read(path: String): ByteArray {
        val name = nameOf(path)
        var id = ids[path] ?: locate(name)?.also { ids[path] = it.id }?.id ?: throw SyncNotFoundException("$path isn't in Google Drive")
        var retried = false
        while (true) {
            try {
                return download(id)
            } catch (e: SyncNotFoundException) {
                // The cached id may be of a file that was replaced behind our back: look the name up once more.
                ids.remove(path)
                val current = if (retried) null else locate(name)
                if (current == null || current.id == id) throw SyncNotFoundException("$path isn't in Google Drive", e)
                ids[path] = current.id
                id = current.id
                retried = true
            }
        }
    }

    @Synchronized
    override fun write(path: String, bytes: ByteArray) {
        val name = nameOf(path)
        if (locate(name) != null) throw SyncAlreadyExistsException("$path exists")
        val group = groupOf(path) ?: ROOT_GROUP
        val id = create(name, group, bytes)
        // Two devices can claim a name at the same moment, and both creates succeed. Only `sync.json` is a name two devices
        // write with different contents (every other file is one device's own or named by its contents), so only there
        // is the older file declared the winner; a duplicated media file does no harm, and a delete removes every copy.
        if (group == ROOT_GROUP) {
            val winner = filesNamed(name).minWithOrNull(DriveFile.OLDEST_FIRST)
            if (winner != null && winner.id != id) {
                runCatching { deleteFile(id) }
                ids.remove(path)
                throw SyncAlreadyExistsException("$path exists")
            }
        }
        ids[path] = id
    }

    @Synchronized
    override fun overwrite(path: String, bytes: ByteArray) {
        val name = nameOf(path)
        val existing = locate(name)
        if (existing == null) {
            ids[path] = create(name, groupOf(path) ?: ROOT_GROUP, bytes)
            return
        }
        ids[path] = existing.id
        replaceContent(existing.id, bytes)
    }

    @Synchronized
    override fun delete(path: String) {
        val name = nameOf(path)
        ids.remove(path)
        // Every file of the name, in case a lost race left two.
        filesNamed(name).forEach { deleteFile(it.id) }
    }

    // --- Drive calls ----------------------------------------------------------------------------------------------

    private fun listFiles(group: String?): List<DriveFile> {
        val files = ArrayList<DriveFile>()
        var pageToken: String? = null
        do {
            val url = filesUrl().newBuilder()
                .addQueryParameter("spaces", SPACE)
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter("fields", "nextPageToken,files(id,name,createdTime)")
                .apply {
                    if (group != null) addQueryParameter("q", "appProperties has { key='$GROUP_KEY' and value='$group' }")
                    pageToken?.let { addQueryParameter("pageToken", it) }
                }
                .build()
            val page = parse(execute("list the files") { Request.Builder().url(url).get() }.body)
            files += page.files()
            pageToken = page["nextPageToken"]?.jsonPrimitive?.contentOrNull
        } while (pageToken != null)
        return files
    }

    private fun filesNamed(name: String): List<DriveFile> {
        val url = filesUrl().newBuilder()
            .addQueryParameter("spaces", SPACE)
            .addQueryParameter("q", "name = '$name'")
            .addQueryParameter("fields", "files(id,name,createdTime)")
            .build()
        return parse(execute("look up $name") { Request.Builder().url(url).get() }.body).files()
    }

    private fun locate(name: String): DriveFile? = filesNamed(name).minWithOrNull(DriveFile.OLDEST_FIRST)

    private fun exists(id: String): Boolean = try {
        execute("check a file") { Request.Builder().url(fileUrl(id, "fields" to "id")).get() }
        true
    } catch (_: SyncNotFoundException) {
        false
    }

    private fun download(id: String): ByteArray =
        execute("read a file") { Request.Builder().url(fileUrl(id, "alt" to "media")).get() }.body

    private fun deleteFile(id: String) {
        try {
            execute("delete a file") { Request.Builder().url(fileUrl(id)).delete() }
        } catch (_: SyncNotFoundException) {
            // Gone already.
        }
    }

    private fun create(name: String, group: String, bytes: ByteArray): String {
        val metadata = buildJsonObject {
            put("name", name)
            putJsonArray("parents") { add(kotlinx.serialization.json.JsonPrimitive(SPACE)) }
            putJsonObject("appProperties") { put(GROUP_KEY, group) }
        }
        val body = if (bytes.size > resumableThreshold) {
            resumable("POST", "${endpoints.upload}/files", metadata, bytes)
        } else {
            val url = uploadUrl("/files", "uploadType" to "multipart", "fields" to "id")
            val multipart = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadata.toString().toRequestBody(JSON))
                .addPart(bytes.toRequestBody(BINARY))
                .build()
            execute("upload a file") { Request.Builder().url(url).post(multipart) }.body
        }
        return parse(body)["id"]?.jsonPrimitive?.contentOrNull ?: throw SyncIoException("Google Drive didn't say which file it created")
    }

    private fun replaceContent(id: String, bytes: ByteArray) {
        if (bytes.size > resumableThreshold) {
            resumable("PATCH", "${endpoints.upload}/files/$id", JsonObject(emptyMap()), bytes)
        } else {
            val url = uploadUrl("/files/$id", "uploadType" to "media", "fields" to "id")
            execute("replace a file") { Request.Builder().url(url).patch(bytes.toRequestBody(BINARY)) }
        }
    }

    /**
     * A resumable upload: one request that opens a session, then the bytes in chunks. A chunk that fails halfway asks Drive
     * how much arrived and goes on from there, a few times; then the write fails and the whole file is sent again at the
     * next round. Returns the body of the final response (the file's id).
     */
    private fun resumable(method: String, address: String, metadata: JsonObject, bytes: ByteArray): ByteArray {
        val start = address.toHttpUrl().newBuilder().addQueryParameter("uploadType", "resumable").addQueryParameter("fields", "id").build()
        val opened = execute("start an upload") {
            Request.Builder().url(start)
                .header("X-Upload-Content-Type", BINARY.toString())
                .header("X-Upload-Content-Length", bytes.size.toString())
                .method(method, metadata.toString().toRequestBody(JSON))
        }
        val session = opened.headers["Location"]?.let { start.resolve(it) } ?: throw SyncIoException("Google Drive didn't open an upload session")
        if (session.host != start.host || session.port != start.port || session.scheme != start.scheme) {
            throw SyncIoException("Google Drive sent the upload somewhere unexpected")
        }
        val total = bytes.size
        var offset = 0
        var failures = 0
        while (true) {
            val end = minOf(offset + chunkSize, total)
            val response = try {
                putChunk(session, bytes, offset, end, total)
            } catch (e: SyncOfflineException) {
                if (++failures > MAX_RESUMES) throw e
                RESUME
            } catch (e: SyncIoException) {
                if (++failures > MAX_RESUMES) throw e
                RESUME
            }
            if (response === RESUME) {
                // How much did arrive? An answer of 200/201 means the file is complete.
                val status = putChunk(session, bytes, total, total, total, statusOnly = true)
                if (status.code in 200..201) return status.body
                offset = status.received
                continue
            }
            if (response.code in 200..201) return response.body
            offset = response.received
            if (offset >= total) throw SyncIoException("Google Drive didn't finish the upload")
        }
    }

    private fun putChunk(session: HttpUrl, bytes: ByteArray, from: Int, to: Int, total: Int, statusOnly: Boolean = false): Reply {
        val range = if (statusOnly) "bytes */$total" else "bytes $from-${to - 1}/$total"
        val body = if (statusOnly) ByteArray(0) else bytes.copyOfRange(from, to)
        val request = Request.Builder().url(session).header("Content-Range", range).put(body.toRequestBody(BINARY)).build()
        // The session address carries its own authorization; no token is sent with it.
        val response = try {
            http.newCall(request).execute().use { Reply(it.code, it.headers, it.body.bytes()) }
        } catch (e: IOException) {
            throw SyncOfflineException("Couldn't reach Google Drive", e)
        }
        return when {
            response.code in 200..201 -> response
            response.code == 308 -> response
            response.code == 404 || response.code == 410 -> throw SyncIoException("The upload session expired")
            response.code == 429 || response.code >= 500 -> throw SyncIoException("Google Drive is unavailable (${response.code})")
            else -> throw failure("upload a file", response)
        }
    }

    /** What a 308 says has arrived: the end of its `Range` header plus one, or nothing. */
    private val Reply.received: Int
        get() = headers["Range"]?.substringAfter('-', "")?.toLongOrNull()?.plus(1)?.toInt() ?: 0

    // --- requests -------------------------------------------------------------------------------------------------

    private class Reply(val code: Int, val headers: Headers, val body: ByteArray)

    /**
     * Runs a request with the access token, and handles the answers every call has the same way: a 401 gets a fresh token
     * once, rate limits and server errors are retried with a growing pause, everything else that isn't a 2xx is mapped.
     */
    private fun execute(what: String, build: () -> Request.Builder): Reply {
        var forceRefresh = false
        var refreshed = false
        var attempt = 0
        while (true) {
            val token = tokens.accessToken(forceRefresh)
            forceRefresh = false
            val reply = try {
                http.newCall(build().header("Authorization", "Bearer $token").build()).execute().use { Reply(it.code, it.headers, it.body.bytes()) }
            } catch (e: IOException) {
                throw SyncOfflineException("Couldn't reach Google Drive ($what)", e)
            }
            when {
                reply.code in 200..299 -> return reply
                reply.code == 401 -> {
                    if (refreshed) throw SyncAuthException("Google Drive refused the sign-in ($what)")
                    refreshed = true
                    forceRefresh = true
                }
                shouldRetry(reply) && attempt < MAX_RETRIES -> pause(RETRY_PAUSE_MS shl attempt++)
                else -> throw failure(what, reply)
            }
        }
    }

    private fun shouldRetry(reply: Reply): Boolean = reply.code == 429 || reply.code >= 500 ||
        (reply.code == 403 && reasons(reply).any { it in RATE_LIMIT_REASONS })

    private fun failure(what: String, reply: Reply): SyncException {
        val reasons = reasons(reply)
        return when {
            reply.code == 404 -> SyncNotFoundException("Not found in Google Drive ($what)")
            reply.code == 403 && "storageQuotaExceeded" in reasons -> SyncQuotaException("Google Drive is full ($what)")
            reply.code == 403 && reasons.any { it in RATE_LIMIT_REASONS || it == "dailyLimitExceeded" } ->
                SyncIoException("Google Drive is asking Mnemo to slow down ($what)")
            reply.code == 403 -> SyncAuthException("Google Drive refused access ($what)")
            reply.code == 401 -> SyncAuthException("Google Drive refused the sign-in ($what)")
            reply.code == 429 || reply.code >= 500 -> SyncIoException("Google Drive is unavailable (${reply.code}, $what)")
            else -> SyncIoException("Google Drive refused the request (${reply.code}, $what)")
        }
    }

    private fun reasons(reply: Reply): List<String> = try {
        json.parseToJsonElement(reply.body.decodeToString()).jsonObject["error"]?.jsonObject?.get("errors")?.jsonArray
            ?.mapNotNull { it.jsonObject["reason"]?.jsonPrimitive?.contentOrNull }.orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    private fun filesUrl(): HttpUrl = "${endpoints.api}/files".toHttpUrl()

    private fun fileUrl(id: String, vararg query: Pair<String, String>): HttpUrl =
        endpoints.api.toHttpUrl().newBuilder().addPathSegment("files").addPathSegment(id).apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()

    private fun uploadUrl(path: String, vararg query: Pair<String, String>): HttpUrl =
        "${endpoints.upload}$path".toHttpUrl().newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()

    private fun parse(body: ByteArray): JsonObject = try {
        json.parseToJsonElement(body.decodeToString()).jsonObject
    } catch (e: Exception) {
        throw SyncIoException("Google Drive's answer couldn't be read", e)
    }

    private fun JsonObject.files(): List<DriveFile> = (this["files"] as? JsonArray).orEmpty().map {
        val file = it.jsonObject
        DriveFile(
            id = file.getValue("id").jsonPrimitive.content,
            name = file.getValue("name").jsonPrimitive.content,
            created = file["createdTime"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    private class DriveFile(val id: String, val name: String, val created: String) {
        fun isOlderThan(other: DriveFile) = OLDEST_FIRST.compare(this, other) < 0

        companion object {
            val OLDEST_FIRST: Comparator<DriveFile> = compareBy<DriveFile> { it.created }.thenBy { it.id }
        }
    }

    // --- names ----------------------------------------------------------------------------------------------------

    private fun nameOf(path: String): String {
        SyncPaths.requireValid(path)
        return path.replace("/", SEPARATOR)
    }

    private fun pathOf(name: String): String? = name.replace(SEPARATOR, "/").takeIf(SyncPaths::isValid)

    /** The group a listing of [prefix] can be narrowed to, or null when it spans groups. */
    private fun groupOf(prefix: String): String? =
        GROUPS.firstOrNull { prefix.startsWith("$it/") }

    private companion object {
        const val SEPARATOR = "__"
        const val SPACE = "appDataFolder"
        const val GROUP_KEY = "g"
        const val ROOT_GROUP = "root"
        val GROUPS = listOf("devices", "snapshots", "media")
        const val RESUMABLE_THRESHOLD = 5 * 1024 * 1024

        /** A multiple of 256 KiB, as Drive requires of every chunk but the last. */
        const val CHUNK_SIZE = 8 * 1024 * 1024
        const val MAX_RETRIES = 4
        const val RETRY_PAUSE_MS = 500L
        const val MAX_RESUMES = 3
        val RATE_LIMIT_REASONS = setOf("rateLimitExceeded", "userRateLimitExceeded", "backendError")
        val JSON = "application/json; charset=UTF-8".toMediaType()
        val BINARY = "application/octet-stream".toMediaType()
        val RESUME = Reply(-1, Headers.headersOf(), ByteArray(0))
    }
}
