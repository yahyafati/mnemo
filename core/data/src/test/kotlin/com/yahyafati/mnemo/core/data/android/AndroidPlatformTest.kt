package com.yahyafati.mnemo.core.data.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.core.net.toUri
import com.yahyafati.mnemo.core.model.MediaRef
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AndroidPlatformTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun directoriesFollowTheLayoutBackupsRelyOn() {
        val directories = AndroidAppDirectories(context)

        assertEquals(context.filesDir, directories.files)
        assertEquals(File(context.filesDir, MediaRef.DIRECTORY), directories.media)
        assertEquals(File(context.filesDir, "restore-pending"), directories.restoreStaging)
        assertEquals(context.getDatabasePath("mnemo.db"), directories.databaseFile("mnemo.db"))
        assertEquals("user_preferences.preferences_pb", directories.dataStoreFile("user_preferences").name)
        // Secrets are outside everything a backup reads, and outside Auto Backup.
        assertEquals(File(context.noBackupFilesDir, "secrets"), directories.secrets)
        assertFalse(directories.secrets.startsWith(directories.files))
    }

    @Test
    fun readsAndWritesADocumentByUri() {
        val access = AndroidDocumentAccess(context)
        val file = tmp.newFile("notes.txt").apply { writeText("old content that is longer") }
        val uri = file.toUri().toString()

        assertEquals("notes.txt", access.info(uri).name)
        access.openOutput(uri).use { it.write("new".toByteArray()) }
        assertEquals("new", access.openInput(uri).use { it.readBytes().decodeToString() })
    }

    @Test
    fun aMissingDocumentIsAnIoError() {
        val access = AndroidDocumentAccess(context)
        val missing = File(tmp.root, "gone.txt").toUri().toString()

        assertFailsWith<IOException> { access.openInput(missing) }
        assertNull(access.info(missing).size)
    }

    @Test
    fun deletingSomethingThatIsNotADocumentIsFalseNotACrash() {
        val access = AndroidDocumentAccess(context)

        assertFalse(access.delete(tmp.newFile().toUri().toString()))
        assertTrue(runCatching { access.keepAccess("content://nothing/tree/x") }.isSuccess)
        assertTrue(runCatching { access.releaseAccess("content://nothing/tree/x") }.isSuccess)
    }
}
