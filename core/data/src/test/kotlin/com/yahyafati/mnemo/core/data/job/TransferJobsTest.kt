package com.yahyafati.mnemo.core.data.job

import androidx.test.core.app.ApplicationProvider
import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.data.android.AndroidAppDirectories
import com.yahyafati.mnemo.core.data.transfer.TransferTestCollection
import com.yahyafati.mnemo.core.model.ExportFormat
import com.yahyafati.mnemo.core.model.TransferError
import com.yahyafati.mnemo.core.testing.platform.FakeDocumentAccess
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The transfer jobs against a fake [com.yahyafati.mnemo.core.common.platform.DocumentAccess]: the
 * same code the desktop app runs, with no WorkManager and no content resolver in the way.
 */
@RunWith(RobolectricTestRunner::class)
class TransferJobsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val documents = FakeDocumentAccess()
    /** Scratch space in a folder of its own, so a job that leaves files behind is caught. */
    private val directories by lazy {
        val real = AndroidAppDirectories(ApplicationProvider.getApplicationContext())
        val scratch = tmp.newFolder("cache")
        object : AppDirectories by real {
            override val cache: File = scratch
        }
    }

    @Test
    fun importJobReadsThePickedPackageAndCleansUp() = runTest {
        TransferTestCollection(tmp.newFolder()).use { c ->
            documents.put("picked/modern.apkg", TransferTestCollection.fixture("modern.apkg").readBytes())
            val progress = mutableListOf<Float>()

            val summary = ImportJob(c.importer, documents, directories).run("picked/modern.apkg", "run-1") { progress += it }

            assertEquals(7, summary.notes)
            assertEquals(9, summary.cards)
            assertTrue(progress.isNotEmpty() && progress.all { it in 0f..1f })
            assertTrue(directories.cache.listFiles().orEmpty().isEmpty(), "the scratch directory is removed")
        }
    }

    @Test
    fun importJobFailsWhenThePackageCannotBeOpened() = runTest {
        TransferTestCollection(tmp.newFolder()).use { c ->
            val error = assertFailsWith<IOException> { ImportJob(c.importer, documents, directories).run("gone", "run-2") {} }
            assertEquals(TransferError.Storage, error.toTransferError())
            assertTrue(directories.cache.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun exportJobWritesToThePickedFile() = runTest {
        TransferTestCollection(tmp.newFolder()).use { c ->
            c.decks.saveDeck("Biology")
            val job = ExportJob(c.exporter, c.json, documents, directories)

            job.run("out/collection.json", ExportFormat.Json, deckId = null, id = "run-3") {}
            job.run("out/deck.apkg", ExportFormat.Apkg, deckId = null, id = "run-4") {}

            assertTrue(documents.content("out/collection.json").decodeToString().contains("Biology"))
            // An .apkg is a zip.
            ZipInputStream(documents.content("out/deck.apkg").inputStream()).use { assertTrue(it.nextEntry != null) }
        }
    }

    @Test
    fun exportJobLeavesNoHalfWrittenFileBehind() = runTest {
        TransferTestCollection(tmp.newFolder()).use { c ->
            val job = ExportJob(c.exporter, c.json, documents, directories)
            // The target opens, then the disk fills up halfway through.
            documents.failingWrites += "out/collection.json"

            val error = assertFailsWith<IOException> {
                job.run("out/collection.json", ExportFormat.Json, deckId = null, id = "run-5") {}
            }

            assertEquals(TransferError.Storage, error.toTransferError())
            assertFalse(documents.exists("out/collection.json"))
        }
    }

    @Test
    fun exportJobReportsAnUnwritableTarget() = runTest {
        TransferTestCollection(tmp.newFolder()).use { c ->
            documents.unwritable += "out/deck.apkg"
            val error = assertFailsWith<IOException> {
                ExportJob(c.exporter, c.json, documents, directories).run("out/deck.apkg", ExportFormat.Apkg, null, "run-6") {}
            }
            assertEquals(TransferError.Storage, error.toTransferError())
        }
    }

    @Test
    fun errorsMapToWhatTheUserIsTold() {
        assertEquals(TransferError.Corrupt, java.util.zip.ZipException("bad").toTransferError())
        assertEquals(TransferError.Storage, SecurityException("revoked").toTransferError())
        assertEquals(TransferError.UnsupportedFile, com.yahyafati.mnemo.core.data.backup.BackupFormatException("no").toTransferError())
        assertEquals(TransferError.Unknown, IllegalStateException("bug").toTransferError())
    }
}
