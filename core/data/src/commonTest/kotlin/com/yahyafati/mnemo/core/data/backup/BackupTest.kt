package com.yahyafati.mnemo.core.data.backup

import com.yahyafati.mnemo.core.ai.client.OpenAiCompatibleClient
import com.yahyafati.mnemo.core.ai.probe.ConnectionProbe
import com.yahyafati.mnemo.core.data.repository.AiProviderDraft
import com.yahyafati.mnemo.core.data.repository.ApiKeyChange
import com.yahyafati.mnemo.core.data.repository.DefaultAiProviderRepository
import com.yahyafati.mnemo.core.data.repository.FileMediaRepository
import com.yahyafati.mnemo.core.data.repository.OfflineCardRepository
import com.yahyafati.mnemo.core.data.repository.OfflineDeckRepository
import com.yahyafati.mnemo.core.database.DatabaseSnapshot
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.RoomTransactionRunner
import com.yahyafati.mnemo.core.model.MediaRef
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.security.FileSecretStore
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.TestAppDirectories
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.fileDatabase
import com.yahyafati.mnemo.core.testing.platform.FakeDocumentAccess
import com.yahyafati.mnemo.core.testing.security.SoftwareSecretCipher
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

/** Backup → fresh install → restore recovers decks, cards, history and media (ROADMAP Phase 2). */
class BackupTest : PlatformTest() {
    private val directories = TestAppDirectories()
    private val clock = TestClock()
    private var db: MnemoDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        directories.delete()
    }

    private fun open(): MnemoDatabase = fileDatabase(directories.databaseFile(MnemoDatabase.NAME)).also { db = it }

    private fun media(database: MnemoDatabase) =
        FileMediaRepository(directories.media, database.mediaDao(), database.noteDao(), clock, Dispatchers.Unconfined)

    private fun manager(database: MnemoDatabase) =
        BackupManager(directories, FakeDocumentAccess(), DatabaseSnapshot(database), media(database), clock, Dispatchers.Unconfined)

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
        DatabaseSnapshot.files(directories).forEach { it.delete() }
        directories.media.deleteRecursively()

        val empty = open()
        assertTrue(empty.deckDao().getDecks().isEmpty())
        val info = manager(empty).stage(backup.toByteArray().inputStream())
        assertEquals(4, info.schemaVersion)
        empty.close()
        db = null

        // The next start applies it before anything opens the database.
        assertTrue(PendingRestore.applyIfPresent(directories))
        assertFalse(directories.restoreStaging.exists())
        val restored = open()
        assertEquals(setOf("Biology", "Cells"), restored.deckDao().getDecks().map { it.name }.toSet())
        val note = restored.noteDao().getPage(0, 10).single().note
        assertEquals("A cell", note.fields[1])
        assertEquals(1, restored.cardDao().observeTotalCount().first())
        assertEquals(listOf("cell.png"), restored.mediaDao().getAll().map { it.name })
        assertEquals("png-bytes", File(directories.media, image.id).readText())
    }

    /** API keys live encrypted outside the database, so no backup carries them (ADR 0005). */
    @Test
    fun backupsNeverContainApiKeys() = runTest {
        val database = open()
        val cipher = SoftwareSecretCipher()
        val secrets = FileSecretStore(directories, cipher, Dispatchers.Unconfined)
        fun providers(db: MnemoDatabase) = DefaultAiProviderRepository(
            db.aiProviderDao(), secrets, ConnectionProbe(OpenAiCompatibleClient(okhttp3.OkHttpClient())),
            RoomTransactionRunner(db), clock, Dispatchers.Unconfined,
        )
        providers(database).saveProvider(
            AiProviderDraft(id = "p", name = "OpenAI", baseUrl = "https://api.openai.com/v1", apiKey = ApiKeyChange.Set(KEY), defaultModel = "m"),
        )
        val sealed = directories.secrets.listFiles()!!.single().readBytes()

        val backup = ByteArrayOutputStream()
        manager(database).write(backup, tmp.newFolder())
        val bytes = backup.toByteArray()
        ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val content = zip.readBytes()
                assertFalse(KEY.toByteArray().isIn(content), entry.name)
                assertFalse(sealed.isIn(content), entry.name)
            }
        }

        // A fresh install has no Keystore key and no secrets: the provider comes back without its key.
        database.close()
        db = null
        DatabaseSnapshot.files(directories).forEach { it.delete() }
        directories.secrets.deleteRecursively()
        val empty = open()
        manager(empty).stage(bytes.inputStream())
        empty.close()
        db = null
        assertTrue(PendingRestore.applyIfPresent(directories))
        val restored = providers(open()).getProvider("p")!!
        assertEquals("OpenAI", restored.name)
        assertFalse(restored.hasApiKey)
    }

    private fun ByteArray.isIn(content: ByteArray): Boolean =
        content.size >= size && (0..content.size - size).any { start -> indices.all { content[start + it] == this[it] } }

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
        assertFalse(PendingRestore.applyIfPresent(directories))
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
        assertFalse(File(directories.files.parentFile, "evil").exists())
    }

    private companion object {
        const val KEY = "sk-proj-backup-test-0123456789"
    }
}
