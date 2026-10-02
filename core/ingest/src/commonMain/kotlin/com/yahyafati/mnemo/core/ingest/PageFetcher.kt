package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.ProjectLinks
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * The fetching half of reading a link: one GET with redirects, a 30 s call timeout, the app's User-Agent
 * and the size limit ([MAX_BYTES], checked while reading, so a huge page fails without being downloaded).
 * [WebPageExtractor] and every [SiteExtractor] fetch through it; none builds its own client (ADR 0012).
 * No cookies or credentials are sent. Blocking.
 */
class PageFetcher(
    client: OkHttpClient,
    private val userAgent: String = USER_AGENT,
) {
    private val client = client.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** The response of [url], or why there is none: [SourceProblem.HttpError], [SourceProblem.TooLarge] or [SourceProblem.Unreachable]. */
    fun fetch(url: HttpUrl, accept: String = ACCEPT_ANY): Fetched {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", accept)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Fetched.Failed(SourceResult.Failure(SourceProblem.HttpError, "HTTP ${response.code}"))
                val body = response.body
                val source = body.source()
                // Read at most one byte past the limit, so a huge page fails without being downloaded.
                if (source.request(MAX_BYTES + 1)) return Fetched.Failed(SourceResult.Failure(SourceProblem.TooLarge))
                Fetched.Page(source.readByteArray(), body.contentType(), response.request.url)
            }
        } catch (e: IOException) {
            Fetched.Failed(SourceResult.Failure(SourceProblem.Unreachable, e.message))
        }
    }

    sealed interface Fetched {
        /** A successful response. [url] is the final one, after redirects. */
        class Page(val bytes: ByteArray, val contentType: MediaType?, val url: HttpUrl) : Fetched {
            /** The body as text, in the response's charset (UTF-8 when it names none). */
            fun text(): String = String(bytes, charset ?: Charsets.UTF_8)

            val charset: Charset? get() = contentType?.charset()
        }

        class Failed(val failure: SourceResult.Failure) : Fetched
    }

    companion object {
        const val MAX_BYTES = 8L * 1024 * 1024
        const val ACCEPT_ANY = "text/html,application/xhtml+xml,text/plain;q=0.9,application/pdf;q=0.8,*/*;q=0.5"
        private const val CALL_TIMEOUT_SECONDS = 30L

        /** Names the app and where to reach its authors, as Wikimedia's User-Agent policy asks. */
        const val USER_AGENT = "Mnemo (+${ProjectLinks.SOURCE})"
    }
}
