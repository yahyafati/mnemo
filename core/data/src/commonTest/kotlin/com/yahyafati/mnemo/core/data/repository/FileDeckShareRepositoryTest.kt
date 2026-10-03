package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.data.transfer.TransferTestCollection
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.TestAppDirectories
import java.io.File
import java.io.IOException
import java.time.Duration
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FileDeckShareRepositoryTest : PlatformTest() {
    private val directories by lazy {
        val real = TestAppDirectories(tmp.newFolder("files"))
        val scratch = tmp.newFolder("cache")
        object : AppDirectories by real {
            override val cache: File = scratch
        }
    }

    private fun repository(c: TransferTestCollection) = FileDeckShareRepository(c.exporter, directories, c.clock, Dispatchers.Unconfined)

    @Test
    fun writesTheDeckAsAnApkgInTheShareFolder() = runTest {
        TransferTestCollection(tmp.newFolder()).use { c ->
            val id = c.decks.saveDeck("Biology")
            val progress = mutableListOf<Float>()

            val shared = repository(c).prepare(id, "Biology") { progress += it }

            assertEquals("Biology.apkg", shared.fileName)
            val file = File(shared.location)
            assertEquals(File(directories.cache, "share"), file.parentFile)
            ZipInputStream(file.inputStream()).use { assertTrue(it.nextEntry != null, "an .apkg is a zip") }
            assertTrue(progress.isNotEmpty() && progress.all { it in 0f..1f })
            assertEquals(listOf("share"), directories.cache.list().orEmpty().toList(), "the scratch directory is removed")
        }
    }

    @Test
    fun removesEarlierSharesOnlyOnceTheyAreADayOld() = runTest {
        TransferTestCollection(tmp.newFolder()).use { c ->
            val id = c.decks.saveDeck("Biology")
            val folder = File(directories.cache, "share").apply { mkdirs() }
            val stale = File(folder, "stale.apkg").apply {
                writeText("old")
                setLastModified(c.clock.now().minus(Duration.ofDays(2)).toEpochMilli())
            }
            val recent = File(folder, "recent.apkg").apply {
                writeText("new")
                setLastModified(c.clock.now().minus(Duration.ofHours(1)).toEpochMilli())
            }

            repository(c).prepare(id, "Biology")

            assertFalse(stale.exists())
            assertTrue(recent.exists())
        }
    }

    @Test
    fun failureLeavesNoPackageBehind() = runTest {
        TransferTestCollection(tmp.newFolder()).use { c ->
            val id = c.decks.saveDeck("Biology")
            // A file where the folder should be: nothing can be written there.
            File(directories.cache, "share").writeText("in the way")

            assertFailsWith<IOException> { repository(c).prepare(id, "Biology") }

            assertEquals(listOf("share"), directories.cache.list().orEmpty().toList())
        }
    }

    @Test
    fun deckNamesBecomeSafeFileNames() {
        assertEquals("Biology.apkg", FileDeckShareRepository.fileName("Biology"))
        assertEquals("Spanish_Verbs.apkg", FileDeckShareRepository.fileName("Spanish::Verbs"))
        assertEquals("a_b_c.apkg", FileDeckShareRepository.fileName("a/b\\c"))
        assertEquals("deck.apkg", FileDeckShareRepository.fileName("  ...  "))
        assertEquals("deck.apkg", FileDeckShareRepository.fileName(""))
        assertEquals("日本語.apkg", FileDeckShareRepository.fileName("日本語"))
        assertEquals(80 + ".apkg".length, FileDeckShareRepository.fileName("x".repeat(200)).length)
    }
}
