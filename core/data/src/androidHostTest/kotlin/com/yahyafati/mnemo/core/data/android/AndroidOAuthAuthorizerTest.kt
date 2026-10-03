package com.yahyafati.mnemo.core.data.android

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.yahyafati.mnemo.core.sync.SyncSignInCancelledException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The phone's sign-in: the browser is sent the consent page, and the redirect link comes back through [OAuthRedirectHub]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidOAuthAuthorizerTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun theBrowserOpensTheConsentPageAndTheRedirectLinkComesBack() = runBlocking {
        val authorizer = AndroidOAuthAuthorizer(application)
        var requestedRedirect = ""
        val sender = thread {
            // The activity that receives the link calls the hub once the user has agreed in the browser.
            while (shadowOf(application).nextStartedActivity == null) Thread.sleep(10)
            assertTrue(OAuthRedirectHub.deliver(Uri.parse("${application.packageName}:/oauth2redirect?code=c-1&state=s-1")))
        }
        val response = authorizer.authorize { redirect ->
            requestedRedirect = redirect
            "https://accounts.example/auth?redirect_uri=$redirect"
        }
        sender.join()

        assertEquals("${application.packageName}:/oauth2redirect", requestedRedirect)
        assertEquals(requestedRedirect, response.redirectUri)
        assertEquals(mapOf("code" to "c-1", "state" to "s-1"), response.parameters)
    }

    @Test
    fun theConsentPageIsOpenedInTheSystemBrowser() = runBlocking {
        val authorizer = AndroidOAuthAuthorizer(application, timeoutMillis = 200)
        assertFailsWith<SyncSignInCancelledException> { authorizer.authorize { "https://accounts.example/auth?x=$it" } }
        val started = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("accounts.example", started.data!!.host)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun aRedirectWithNobodyWaitingIsDropped() {
        assertFalse(OAuthRedirectHub.deliver(Uri.parse("${application.packageName}:/oauth2redirect?code=c&state=s")))
    }

    @Test
    fun aSignInThatTakesTooLongIsACancelAndLeavesNobodyWaiting() = runBlocking {
        val authorizer = AndroidOAuthAuthorizer(application, timeoutMillis = 100)
        assertFailsWith<SyncSignInCancelledException> { authorizer.authorize { "https://accounts.example/auth?x=$it" } }
        assertFalse(OAuthRedirectHub.deliver(Uri.parse("${application.packageName}:/oauth2redirect?code=late&state=s")))
    }

    @Test
    fun theRedirectUriIsThePackageNameWithOneSlash() {
        assertEquals("com.yahyafati.mnemo:/oauth2redirect", AndroidOAuthAuthorizer.redirectUri("com.yahyafati.mnemo"))
    }
}
