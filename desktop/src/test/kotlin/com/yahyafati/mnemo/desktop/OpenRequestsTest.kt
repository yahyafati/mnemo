package com.yahyafati.mnemo.desktop

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Handing the files of a second launch to the running app (desktop ROADMAP D8). */
class OpenRequestsTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun requestsComeBackOldestFirstAndAreRemoved() {
        assertTrue(OpenRequests.send(folder.root, listOf("/a/one.apkg", "/a/two.apkg"), nowMillis = 2_000))
        assertTrue(OpenRequests.send(folder.root, listOf("/b/three.colpkg"), nowMillis = 1_000))
        assertEquals(listOf("/b/three.colpkg", "/a/one.apkg", "/a/two.apkg"), OpenRequests.take(folder.root, nowMillis = System.currentTimeMillis()))
        assertEquals(emptyList(), OpenRequests.take(folder.root))
        assertEquals(0, File(folder.root, OpenRequests.DIRECTORY).list().orEmpty().size)
    }

    @Test
    fun nothingWaitingIsNoFiles() {
        assertEquals(emptyList(), OpenRequests.take(folder.root))
        assertTrue(OpenRequests.send(folder.root, emptyList()))
        assertFalse(File(folder.root, OpenRequests.DIRECTORY).exists())
    }

    @Test
    fun aRequestLeftForAnAppThatHasClosedIsDroppedNotOpened() {
        OpenRequests.send(folder.root, listOf("/old.apkg"))
        val request = File(folder.root, OpenRequests.DIRECTORY).listFiles()!!.single()
        request.setLastModified(System.currentTimeMillis() - OpenRequests.MAX_AGE_MILLIS - 1_000)
        assertEquals(emptyList(), OpenRequests.take(folder.root))
        assertFalse(request.exists())
    }

    @Test
    fun aHalfWrittenRequestIsNotRead() {
        val directory = File(folder.root, OpenRequests.DIRECTORY).apply { mkdirs() }
        File(directory, "0001.tmp").writeText("/half.apk")
        assertEquals(emptyList(), OpenRequests.take(folder.root))
    }

    @Test
    fun aWriteThatFailsIsReportedNotThrown() {
        val notADirectory = folder.newFile("taken")
        assertFalse(OpenRequests.send(notADirectory, listOf("/a.apkg")))
    }

    @Test
    fun onlyFilesThatExistAreTakenFromTheArguments() {
        val package_ = folder.newFile("deck.apkg")
        val files = OpenRequests.existingFiles(arrayOf(package_.path, folder.root.path, File(folder.root, "missing.apkg").path, "--flag"))
        assertEquals(listOf(package_.absolutePath), files)
    }
}
