package com.yahyafati.mnemo.core.ui.permission

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yahyafati.mnemo.core.designsystem.platform.LocalPlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.platform.PlatformCapabilities
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PermissionRequestTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun request(permission: AppPermission, capabilities: PlatformCapabilities, onResult: (Boolean) -> Unit = {}): PermissionRequest {
        lateinit var request: PermissionRequest
        composeRule.setContent {
            CompositionLocalProvider(LocalPlatformCapabilities provides capabilities) {
                request = rememberPermissionRequest(permission, onResult)
            }
        }
        composeRule.waitForIdle()
        return request
    }

    @Test
    fun aPlatformWithoutRuntimePermissionsGrantsEverythingAndAsksNothing() {
        val answers = mutableListOf<Boolean>()
        val request = request(AppPermission.Microphone, PlatformCapabilities(runtimePermissions = false), answers::add)

        assertTrue(request.isGranted)
        assertFalse(request.canRequest)
        request.launch()
        assertEquals(listOf(true), answers)
        assertNull(shadowOf(composeRule.activity).nextStartedActivityForResult, "no system prompt")
    }

    @Test
    fun androidAsksForAPermissionItDoesNotHold() {
        val request = request(AppPermission.Microphone, PlatformCapabilities())

        assertFalse(request.isGranted)
        assertTrue(request.canRequest)
        request.launch()
        composeRule.waitForIdle()
        val prompt = assertNotNull(shadowOf(composeRule.activity).nextStartedActivityForResult)
        val asked = prompt.intent.getStringArrayExtra(REQUEST_PERMISSIONS_NAMES).orEmpty()
        assertEquals(listOf(Manifest.permission.RECORD_AUDIO), asked.toList())
    }

    @Test
    fun aGrantedPermissionIsNotAskedForAgain() {
        shadowOf(composeRule.activity.applicationContext as Application).grantPermissions(Manifest.permission.RECORD_AUDIO)

        val request = request(AppPermission.Microphone, PlatformCapabilities())

        assertTrue(request.isGranted)
        assertFalse(request.canRequest)
    }

    @Test
    fun notificationsAreAPermissionFromAndroid13() {
        shadowOf(composeRule.activity.applicationContext as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val request = request(AppPermission.Notifications, PlatformCapabilities())

        assertTrue(request.isGranted)
        assertFalse(request.canRequest)
    }

    private companion object {
        /** `PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES`, which the SDK doesn't publish. */
        const val REQUEST_PERMISSIONS_NAMES = "android.content.pm.extra.REQUEST_PERMISSIONS_NAMES"
    }
}
