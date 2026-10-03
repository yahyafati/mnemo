package com.yahyafati.mnemo

import android.app.Application
import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The share sheet's file provider (`rememberAndroidFileSharer`, `:core:ui`): its authority and the
 * folder it may share from are declared in the manifest and `res/xml/share_paths.xml`, away from
 * the code that uses them, so this pins them together.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ShareDeckProviderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val authority get() = "${context.packageName}.fileprovider"

    // Canonical, because Robolectric's temp directory sits behind a symlink on macOS (/var → /private/var).
    private val cache get() = context.cacheDir.canonicalFile

    // One test: FileProvider caches its roots per authority for the whole JVM, and each test gets a new cache directory.
    @Test
    fun onlyTheShareFolderOfTheCacheIsShareable() {
        val shared = File(cache, "share/Biology.apkg").apply { parentFile!!.mkdirs(); writeText("zip") }
        val uri = FileProvider.getUriForFile(context, authority, shared)
        assertEquals("content", uri.scheme)
        assertEquals(authority, uri.authority)
        assertTrue(uri.path!!.endsWith("Biology.apkg"))
        assertEquals("zip", context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() })

        val scratch = File(cache, "export-1/collection.anki2").apply { parentFile!!.mkdirs(); writeText("db") }
        assertFailsWith<IllegalArgumentException> { FileProvider.getUriForFile(context, authority, scratch) }
    }
}
