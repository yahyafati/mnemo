package com.yahyafati.mnemo.core.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yahyafati.mnemo.core.data.repository.FileMediaRepository
import com.yahyafati.mnemo.core.data.repository.OfflineCardRepository
import com.yahyafati.mnemo.core.data.repository.OfflineDeckRepository
import com.yahyafati.mnemo.core.database.DatabaseSnapshot
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.RoomTransactionRunner
import com.yahyafati.mnemo.core.model.MediaRef
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.testing.TestClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Backup → fresh install → restore recovers decks, cards, history and media (ROADMAP Phase 2). */
@RunWith(RobolectricTestRunner::class)
class BackupTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = TestClock()
    private var db: MnemoDatabase? = null

    @After
    fun tearDown() {
        db?.close()
    }

    private fun open(): MnemoDatabase = MnemoDatabase.build(context).also { db = it }

    private fun media(database: MnemoDatabase) =
        FileMediaRepository(File(context.filesDir, MediaRef.DIRECTORY), database.mediaDao(), database.noteDao(), clock, Dispatchers.Unconfined)

    private fun manager(database: MnemoDatabase) =
        BackupManager(context, DatabaseSnapshot(context, database), media(database), clock, Dispatchers.Unconfined)

    @Test
    fun backupAndRestoreOnAFreshInstall() = runTest {
        // A collection with a deck, a note, and an image.
        val database = open()
        val transaction = RoomTransactionRunner(database)
        val decks = OfflineDeckRepository(database.deckDao(), database.noteDao(), database.cardDao(), transaction, clock, Dispatchers.Unconfined)
        val cards = OfflineCardRepository(database.noteDao(), database.cardDao(), database.deckDao(), transaction, clock)
        val image = media(database).store("png-bytes".byteInputStream(), "cell.png")
        val deck = decks.saveDeck("Biology::Cells")
        cards.addNote(deck, NoteKind.Basic, listOf("What is this? ![](${MediaRef.of(image.id)})", "A cell"), listOf("bio"))

        val backup = ByteArrayOutputStream()
        manager(database).write(backup, tmp.newFolder())

        // "Fresh install": everything is wiped.
        database.close()
        db = null
        DatabaseSnapshot.files(context).forEach { it.delete() }
        File(context.filesDir, MediaRef.DIRECTORY).deleteRecursively()

        val empty = open()
        assertTrue(empty.deckDao().getDecks().isEmpty())
        val info = manager(empty).stage(backup.toByteArray().inputStream())
        assertEquals(2, info.schemaVersion)
        empty.close()
        db = null

        // The next start applies it before anything opens the database.
        assertTrue(PendingRestore.applyIfPresent(context))
        assertFalse(PendingRestore.directory(context).exists())
        val restored = open()
        assertEquals(setOf("Biology", "Cells"), restored.deckDao().getDecks().map { it.name }.toSet())
        val note = restored.noteDao().getPage(0, 10).single().note
        assertEquals("A cell", note.fields[1])
        assertEquals(1, restored.cardDao().observeTotalCount().first())
        assertEquals(listOf("cell.png"), restored.mediaDao().getAll().map { it.name })
        assertEquals("png-bytes", File(context.filesDir, "${MediaRef.DIRECTORY}/${image.id}").readText())
    }

    @Test
    fun rejectsFilesThatAreNotBackups() = runTest {
        val database = open()
        val notZip = "hello".byteInputStream()
        assertFailsWith<BackupFormatException> { manager(database).stage(notZip) }
        val noManifest = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("database/mnemo.db"))
                zip.write(1)
                zip.closeEntry()
            }
        }
        assertFailsWith<BackupFormatException> { manager(database).stage(noManifest.toByteArray().inputStream()) }
        // A failed stage leaves nothing for the next start to apply.
        assertFalse(PendingRestore.applyIfPresent(context))
    }

    @Test
    fun pathsCannotEscapeTheStagingDirectory() = runTest {
        val database = open()
        val evil = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("../../evil"))
                zip.write(1)
                zip.closeEntry()
            }
        }
        assertFailsWith<BackupFormatException> { manager(database).stage(evil.toByteArray().inputStream()) }
        assertFalse(File(context.filesDir.parentFile, "evil").exists())
    }
}
