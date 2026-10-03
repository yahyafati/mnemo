package com.yahyafati.mnemo.core.data.desktop

import com.yahyafati.mnemo.core.sync.SyncIoException
import com.yahyafati.mnemo.core.sync.SyncSignInCancelledException
import com.yahyafati.mnemo.core.sync.oauth.OAuthAuthorizer
import com.yahyafati.mnemo.core.sync.oauth.OAuthResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder

/**
 * Google sign-in on a computer (docs/sync/ROADMAP.md S6): the default browser opens the consent page and Google sends it
 * back to `http://127.0.0.1:<port>`, where this class listens, on the loopback address only, until one answer has come
 * or the wait ends. A plain [ServerSocket] (`java.base`) rather than `jdk.httpserver`, so the packaged runtime needs no
 * further module. The listener lives for one sign-in. A request that isn't an answer (the browser asking for an icon)
 * gets a 404 and is ignored; whether an answer belongs to our request is the sign-in's `state` check.
 */
internal class DesktopOAuthAuthorizer(
    private val openBrowser: (url: String) -> Unit = ::openInBrowser,
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
) : OAuthAuthorizer {
    override suspend fun authorize(authorizationUrl: (redirectUri: String) -> String): OAuthResponse = withContext(Dispatchers.IO) {
        ServerSocket(0, BACKLOG, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = POLL_MILLIS
            val redirectUri = "http://127.0.0.1:${server.localPort}"
            try {
                openBrowser(authorizationUrl(redirectUri))
            } catch (e: IOException) {
                throw SyncIoException("Couldn't open the browser to sign in", e)
            }
            OAuthResponse(redirectUri, waitForAnswer(server))
        }
    }

    private suspend fun waitForAnswer(server: ServerSocket): Map<String, String> {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (true) {
            currentCoroutineContext().ensureActive()
            if (System.nanoTime() > deadline) throw SyncSignInCancelledException("Sign-in took too long")
            val socket = try {
                server.accept()
            } catch (_: SocketTimeoutException) {
                continue
            }
            socket.use { handle(it) }?.let { return it }
        }
    }

    /** Answers one request; the query parameters if it was the redirect, null if it was anything else. */
    private fun handle(socket: Socket): Map<String, String>? {
        socket.soTimeout = REQUEST_MILLIS
        return try {
            val reader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val requestLine = reader.readLine() ?: return null
            // The rest of the headers are not needed; the browser is waiting for an answer to the request line.
            val target = requestLine.split(' ').getOrNull(1)
            val uri = target?.let { runCatching { URI("http://127.0.0.1$it") }.getOrNull() }
            val parameters = uri?.rawQuery?.let(::parseQuery).orEmpty()
            val isAnswer = requestLine.startsWith("GET ") && uri?.rawPath == "/" && ("code" in parameters || "error" in parameters)
            respond(socket, if (isAnswer) 200 else 404, if (isAnswer) PAGE else "")
            parameters.takeIf { isAnswer }
        } catch (_: IOException) {
            null
        }
    }

    private fun respond(socket: Socket, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code ${if (code == 200) "OK" else "Not Found"}\r\n" +
            "Content-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\n" +
            "Referrer-Policy: no-referrer\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(head.toByteArray(Charsets.ISO_8859_1))
            write(bytes)
            flush()
        }
    }

    private fun parseQuery(query: String): Map<String, String> = query.split('&').filter { it.isNotEmpty() }.associate {
        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
    }

    companion object {
        const val TIMEOUT_MILLIS = 5 * 60_000L
        private const val POLL_MILLIS = 250
        private const val REQUEST_MILLIS = 3_000
        private const val BACKLOG = 4

        private const val PAGE = "<!doctype html><html><head><meta charset=\"utf-8\"><title>Mnemo</title>" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>" +
            "<body style=\"font-family:system-ui,sans-serif;max-width:32em;margin:4em auto;padding:0 1em\">" +
            "<h1>You can close this tab</h1><p>Mnemo has what it needs. Go back to the app.</p></body></html>"

        /** The default browser; `xdg-open` where the Java runtime has no desktop integration (some Linux setups). */
        fun openInBrowser(url: String) {
            val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
            if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(URI(url))
            } else {
                ProcessBuilder("xdg-open", url).redirectErrorStream(true).start()
            }
        }
    }
}
