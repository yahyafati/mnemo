package com.yahyafati.mnemo.core.data.backup

import com.yahyafati.mnemo.core.data.repository.FileMediaRepository
import com.yahyafati.mnemo.core.database.DatabaseSnapshot
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.TestAppDirectories
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.fileDatabase
import com.yahyafati.mnemo.core.testing.platform.FakeDocumentAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What platform this test runs on, as the name of the fixture it makes (`<source>-backup.zip`). */
expect val fixtureSource: String

/**
 * A collection moves between a phone and a computer as a backup (ROADMAP "Moving a collection
 * between devices"): a backup made on Android restores on the desktop, and one made on the
 * desktop restores on Android. The fixtures in `resources/backups` were made by each platform
 * from [SampleCollection]; this test restores both on both targets, so each direction is checked
 * by the other platform's run.
 */
class CrossDeviceBackupTest : PlatformTest() {
    private val directories = TestAppDirectories()
    private val clock = TestClock()

    private fun open(): MnemoDatabase = fileDatabase(directories.databaseFile(MnemoDatabase.NAME))

    private fun manager(db: MnemoDatabase) = BackupManager(
        directories,
        FakeDocumentAccess(),
        DatabaseSnapshot(db),
        FileMediaRepository(directories.media, db.mediaDao(), db.noteDao(), clock, Dispatchers.Unconfined),
        clock,
        Dispatchers.Unconfined,
    )

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.classLoader.getResourceAsStream("backups/$name")) { "No fixture backups/$name" }.use { it.readBytes() }

    private fun restores(name: String) = runTest {
        // A fresh install stages the backup, and the next start applies it.
        val empty = open()
        val info = manager(empty).stage(fixture(name).inputStream())
        empty.close()
        assertEquals(4, info.schemaVersion)
        assertTrue(PendingRestore.applyIfPresent(directories))

        val restored = open()
        try {
            SampleCollection.assertRestored(directories, restored)
            // A backup from before sync gets an identity when it is migrated, with sync off.
            val sync = checkNotNull(restored.syncDao().getState())
            assertTrue(sync.deviceId.isNotBlank() && !sync.enabled)
        } finally {
            restored.close()
            directories.delete()
        }
    }

    @Test
    fun aBackupMadeOnAndroidRestoresHere() = restores("android-backup.zip")

    @Test
    fun aBackupMadeOnTheDesktopRestoresHere() = restores("desktop-backup.zip")

    /**
     * Makes this platform's fixture: `MNEMO_WRITE_FIXTURES=<directory> ./gradlew :core:data:testAndroidHostTest
     * :core:data:desktopTest --tests '*CrossDeviceBackupTest*'`. Does nothing otherwise. Commit the
     * files only when the backup format changes on purpose.
     */
    @Test
    fun writesThisPlatformsFixtureWhenAsked() = runTest {
        val target = System.getenv("MNEMO_WRITE_FIXTURES")?.takeIf { it.isNotBlank() } ?: return@runTest
        val db = open()
        try {
            SampleCollection.create(directories, db, clock)
            val backup = ByteArrayOutputStream()
            manager(db).write(backup, tmp.newFolder())
            File(target).apply { mkdirs() }.resolve("$fixtureSource-backup.zip").writeBytes(backup.toByteArray())
        } finally {
            db.close()
            directories.delete()
        }
    }
}
