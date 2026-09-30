package com.yahyafati.mnemo.core.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.data.android.AndroidAppDirectories
import com.yahyafati.mnemo.core.data.job.BackupJob
import com.yahyafati.mnemo.core.data.repository.FileMediaRepository
import com.yahyafati.mnemo.core.database.DatabaseSnapshot
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.model.BackupSettings
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.platform.FakeDocumentAccess
import com.yahyafati.mnemo.core.testing.repository.FakeUserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.time.Duration
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Manual and automatic backups through [DocumentAccess] fakes: no content resolver, no WorkManager. */
@RunWith(RobolectricTestRunner::class)
class BackupJobTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val real = AndroidAppDirectories(context)
    private val directories: AppDirectories by lazy {
        val scratch = tmp.newFolder("cache")
        object : AppDirectories by real {
            override val cache: File = scratch
        }
    }
    private val clock = TestClock()
    private val documents = FakeDocumentAccess()
    private val settings = FakeUserSettingsRepository()
    private var database: MnemoDatabase? = null

    @After
    fun tearDown() {
        database?.close()
    }

    private fun manager(): BackupManager {
        val db = MnemoDatabase.build(context).also { database = it }
        val media = FileMediaRepository(real.media, db.mediaDao(), db.noteDao(), clock, Dispatchers.Unconfined)
        return BackupManager(directories, documents, DatabaseSnapshot(real, db), media, clock, Dispatchers.Unconfined)
    }

    private fun job(manager: BackupManager = manager()) = BackupJob(manager, settings, documents, directories, clock)

    private fun automatic(folder: String, keep: Int = 7) {
        documents.folder(folder)
        settings.settings.value = settings.settings.value.copy(
            backup = BackupSettings(autoBackupEnabled = true, folderUri = folder, keepCount = keep),
        )
    }

    private fun entries(bytes: ByteArray) =
        ZipInputStream(bytes.inputStream()).use { zip -> generateSequence { zip.nextEntry?.name }.toList() }

    @Test
    fun aManualBackupIsWrittenToThePickedFile() = runTest {
        val written = job().run("out/backup.zip", "m1")

        assertTrue(written)
        assertTrue("manifest.json" in entries(documents.content("out/backup.zip")))
        assertNotNull(settings.settings.value.backup.lastBackupAt)
        assertTrue(directories.cache.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun aFailedManualBackupLeavesNoFileBehind() = runTest {
        val manager = manager()
        // The database is closed under the job, so writing the snapshot fails after the file exists.
        database?.close()

        assertFailsWith<Exception> { job(manager).run("out/backup.zip", "m2") }

        assertFalse(documents.exists("out/backup.zip"))
        assertNull(settings.settings.value.backup.lastBackupAt)
    }

    @Test
    fun anAutomaticBackupDoesNothingWhileItIsOff() = runTest {
        documents.folder("folder")

        assertFalse(job().run(null, "a1"))

        assertTrue(documents.names("folder").isEmpty())
    }

    @Test
    fun anAutomaticBackupGoesIntoTheFolderAndPrunesOldOnes() = runTest {
        automatic("folder", keep = 2)
        // Two older automatic backups, a manual one, and a file of the user's own.
        documents.putInFolder("folder", "mnemo-auto-backup-2025-12-30-090000.zip")
        documents.putInFolder("folder", "mnemo-auto-backup-2025-12-31-090000.zip")
        documents.putInFolder("folder", "mnemo-backup-2020-01-01-000000.zip")
        documents.putInFolder("folder", "notes.txt")

        assertTrue(job().run(null, "a2"))

        val names = documents.names("folder")
        val automatic = names.filter { it.startsWith(BackupManager.AUTO_PREFIX) }
        // The oldest is gone, the newer one stays, and today's is new (its name follows the local time zone).
        assertEquals(2, automatic.size)
        assertTrue("mnemo-auto-backup-2025-12-31-090000.zip" in automatic)
        assertFalse("mnemo-auto-backup-2025-12-30-090000.zip" in names)
        assertTrue("mnemo-backup-2020-01-01-000000.zip" in names)
        assertTrue("notes.txt" in names)
        assertEquals(clock.now(), settings.settings.value.backup.lastBackupAt)
    }

    @Test
    fun aBackupIsAlwaysKeptEvenWithAKeepCountOfZero() = runTest {
        automatic("folder", keep = 0)
        documents.putInFolder("folder", "mnemo-auto-backup-2025-12-31-090000.zip")

        job().run(null, "a3")
        clock.advanceBy(Duration.ofDays(1))
        job().run(null, "a4")

        assertEquals(1, documents.names("folder").count { it.startsWith(BackupManager.AUTO_PREFIX) })
    }

    @Test
    fun aVanishedFolderIsAnError() = runTest {
        automatic("folder")
        documents.unavailableFolders += "folder"

        assertFailsWith<IOException> { job().run(null, "a5") }
        assertNull(settings.settings.value.backup.lastBackupAt)
    }
}
