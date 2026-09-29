package com.yahyafati.mnemo.core.data.repository

import androidx.test.core.app.ApplicationProvider
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.RoomTransactionRunner
import com.yahyafati.mnemo.core.model.MediaRef
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.testing.TestClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class FileMediaRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val db = MnemoDatabase.build(ApplicationProvider.getApplicationContext(), name = null)
    private val clock = TestClock(Instant.parse("2026-03-01T10:00:00Z"))
    private val dir by lazy { tmp.newFolder("media") }
    private val media by lazy { FileMediaRepository(dir, db.mediaDao(), db.noteDao(), clock, Dispatchers.Unconfined) }
    private val cards by lazy { OfflineCardRepository(db.noteDao(), db.cardDao(), db.deckDao(), RoomTransactionRunner(db), clock) }

    @After
    fun tearDown() = db.close()

    @Test
    fun storesByContentHash() = runTest {
        val first = media.store("same bytes".byteInputStream(), "a.png")
        val again = media.store("same bytes".byteInputStream(), "b.png")
        assertEquals(first, again)
        assertEquals("a.png", again.name)
        assertEquals("image/png", first.mimeType)
        assertEquals(64, first.id.length)
        assertEquals("same bytes", media.file(first.id).readText())
        assertEquals(listOf(first.id), dir.list()!!.toList())
    }

    @Test
    fun garbageCollectionKeepsWhatNotesUseAndRecentFiles() = runTest {
        val used = media.store("used".byteInputStream(), "used.png")
        val unused = media.store("unused".byteInputStream(), "unused.png")
        cards.addNote("deck", NoteKind.Basic, listOf("![](${MediaRef.of(used.id)})", "back"), emptyList())
        val stray = File(dir, "f".repeat(64)).apply { writeText("stray") }

        // Too recent: nothing goes.
        assertEquals(0, media.collectGarbage())

        clock.advanceBy(Duration.ofDays(2))
        stray.setLastModified(0)
        assertEquals(2, media.collectGarbage())
        assertTrue(media.file(used.id).exists())
        assertFalse(media.file(unused.id).exists())
        assertFalse(stray.exists())
        assertEquals(listOf("used.png"), media.getAll().map { it.name })

        // Stored again, it comes back.
        media.store("unused".byteInputStream(), "unused.png")
        assertEquals(setOf("used.png", "unused.png"), media.getAll().map { it.name }.toSet())
    }
}
