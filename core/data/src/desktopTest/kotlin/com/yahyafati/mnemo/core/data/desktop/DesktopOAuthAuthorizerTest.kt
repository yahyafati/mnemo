package com.yahyafati.mnemo.core.data.desktop

import com.yahyafati.mnemo.core.sync.SyncIoException
import com.yahyafati.mnemo.core.sync.SyncSignInCancelledException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The loopback listener of the desktop sign-in, on a real socket of this machine (nothing leaves it). */
class DesktopOAuthAuthorizerTest {
    /** A "browser" that visits [paths] on the redirect address, one after the other, and records the answers' status codes. */
    private fun browser(vararg paths: String, statuses: MutableList<Int> = ArrayList(), body: AtomicReference<String> = AtomicReference("")): (String) -> Unit = { url ->
        val redirect = Regex("redirect_uri=([^&]+)").find(url)!!.groupValues[1].let { java.net.URLDecoder.decode(it, "UTF-8") }
        Thread {
            for (path in paths) {
                val connection = URI("$redirect$path").toURL().openConnection() as HttpURLConnection
                statuses += connection.responseCode
                if (connection.responseCode == 200) body.set(connection.inputStream.readBytes().decodeToString())
            }
        }.start()
    }

    @Test
    fun theRedirectCarriesBackTheAnswerAndGetsAPageToCloseTheTab() = runBlocking {
        val statuses = ArrayList<Int>()
        val page = AtomicReference("")
        val authorizer = DesktopOAuthAuthorizer(browser("/?state=s-1&code=4%2F0AbC&scope=x", statuses = statuses, body = page))
        var requested = ""
        val response = authorizer.authorize { redirect -> "https://accounts.example/auth?redirect_uri=${java.net.URLEncoder.encode(redirect, "UTF-8")}".also { requested = it } }

        assertEquals(mapOf("state" to "s-1", "code" to "4/0AbC", "scope" to "x"), response.parameters)
        assertTrue(response.redirectUri.matches(Regex("http://127\\.0\\.0\\.1:\\d+")), response.redirectUri)
        assertTrue(requested.contains(java.net.URLEncoder.encode(response.redirectUri, "UTF-8")))
        Thread.sleep(100)
        assertEquals(listOf(200), statuses)
        assertTrue("close this tab" in page.get())
    }

    @Test
    fun anErrorFromGoogleIsAnAnswerToo() = runBlocking {
        val authorizer = DesktopOAuthAuthorizer(browser("/?state=s-1&error=access_denied"))
        val response = authorizer.authorize { "https://accounts.example/auth?redirect_uri=${java.net.URLEncoder.encode(it, "UTF-8")}" }
        assertEquals(mapOf("state" to "s-1", "error" to "access_denied"), response.parameters)
    }

    @Test
    fun otherRequestsAreRefusedAndTheListenerKeepsWaiting() = runBlocking {
        val statuses = ArrayList<Int>()
        val authorizer = DesktopOAuthAuthorizer(browser("/favicon.ico", "/other?code=nope", "/?state=s-2&code=ok", statuses = statuses))
        val response = authorizer.authorize { "https://accounts.example/auth?redirect_uri=${java.net.URLEncoder.encode(it, "UTF-8")}" }
        assertEquals(mapOf("state" to "s-2", "code" to "ok"), response.parameters)
        Thread.sleep(100)
        assertEquals(listOf(404, 404, 200), statuses)
    }

    @Test
    fun theListenerIsOnTheLoopbackAddressOnly() = runBlocking {
        var address: InetAddress? = null
        val authorizer = DesktopOAuthAuthorizer({ url ->
            val port = Regex("127\\.0\\.0\\.1%3A(\\d+)|127\\.0\\.0\\.1:(\\d+)").find(url)!!.groupValues.drop(1).first { it.isNotEmpty() }.toInt()
            // Connecting through the machine's other address must fail: the socket is bound to 127.0.0.1.
            address = InetAddress.getLocalHost().takeUnless { it.isLoopbackAddress }
            val reachable = address?.let { runCatching { java.net.Socket(it, port).use { true } }.getOrDefault(false) } ?: false
            assertTrue(!reachable, "The listener must not be reachable on ${address}")
            browser("/?state=s&code=c")(url)
        })
        authorizer.authorize { "https://accounts.example/auth?redirect_uri=${java.net.URLEncoder.encode(it, "UTF-8")}" }
        Unit
    }

    @Test
    fun noAnswerIsACancelAfterTheTimeoutAndFreesThePort() {
        var port = 0
        val authorizer = DesktopOAuthAuthorizer({ url -> port = Regex("127\\.0\\.0\\.1%3A(\\d+)").find(url)!!.groupValues[1].toInt() }, timeoutMillis = 400)
        assertFailsWith<SyncSignInCancelledException> {
            runBlocking { authorizer.authorize { "https://accounts.example/auth?redirect_uri=${java.net.URLEncoder.encode(it, "UTF-8")}" } }
        }
        // The port can be bound again: the listener was closed.
        ServerSocket(port, 1, InetAddress.getLoopbackAddress()).close()
    }

    @Test
    fun cancellingTheCoroutineStopsTheWaitAndClosesTheListener() = runBlocking {
        var port = 0
        val authorizer = DesktopOAuthAuthorizer({ url -> port = Regex("127\\.0\\.0\\.1%3A(\\d+)").find(url)!!.groupValues[1].toInt() })
        val waiting = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            authorizer.authorize { "https://accounts.example/auth?redirect_uri=${java.net.URLEncoder.encode(it, "UTF-8")}" }
        }
        while (port == 0) withContext(Dispatchers.IO) { Thread.sleep(10) }
        waiting.cancelAndJoin()
        ServerSocket(port, 1, InetAddress.getLoopbackAddress()).close()
    }

    @Test
    fun aBrowserThatCantBeOpenedIsAnIoError() {
        val authorizer = DesktopOAuthAuthorizer({ throw java.io.IOException("no browser") })
        assertFailsWith<SyncIoException> {
            runBlocking { authorizer.authorize { "https://accounts.example/auth?redirect_uri=${java.net.URLEncoder.encode(it, "UTF-8")}" } }
        }
    }
}
