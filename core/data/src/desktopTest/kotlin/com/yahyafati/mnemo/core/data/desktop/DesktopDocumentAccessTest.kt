package com.yahyafati.mnemo.core.data.desktop

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopDocumentAccessTest {
    private val folder: File = Files.createTempDirectory("mnemo-documents").toFile()
    private val access = DesktopDocumentAccess()

    @AfterTest
    fun deleteFiles() {
        folder.deleteRecursively()
    }

    @Test
    fun readsAndWritesAPathOrAFileUri() {
        val file = File(folder, "notes.txt").apply { writeText("old content that is longer") }

        assertEquals("notes.txt", access.info(file.absolutePath).name)
        assertEquals("text/plain", access.info(file.absolutePath).mimeType)
        assertEquals(file.length(), access.info(file.toURI().toString()).size)
        access.openOutput(file.absolutePath).use { it.write("new".toByteArray()) }
        assertEquals("new", access.openInput(file.toURI().toString()).use { it.readBytes().decodeToString() })
    }

    @Test
    fun writingCreatesMissingFolders() {
        val file = File(folder, "a/b/backup.zip")

        access.openOutput(file.absolutePath).use { it.write(1) }

        assertTrue(file.exists())
    }

    @Test
    fun aMissingDocumentIsAnIoError() {
        val missing = File(folder, "gone.txt").absolutePath

        assertFailsWith<IOException> { access.openInput(missing) }
        assertNull(access.info(missing).size)
        assertFalse(access.delete(missing))
    }

    @Test
    fun packagesAreZipsAndPdfsArePdfs() {
        assertEquals("application/zip", access.info("/x/deck.apkg").mimeType)
        assertEquals("application/zip", access.info("/x/all.colpkg").mimeType)
        assertEquals("application/pdf", access.info("/x/paper.pdf").mimeType)
    }

    @Test
    fun aFileInAFolderNeverOverwritesOneOfTheUsers() {
        val first = access.createInFolder(folder.absolutePath, "mnemo-auto-backup.zip", "application/zip")
        val second = access.createInFolder(folder.absolutePath, "mnemo-auto-backup.zip", "application/zip")
        File(folder, "sub").mkdir()

        assertEquals("mnemo-auto-backup.zip", File(first).name)
        assertEquals("mnemo-auto-backup (1).zip", File(second).name)
        // Files only, not subfolders.
        assertEquals(setOf("mnemo-auto-backup.zip", "mnemo-auto-backup (1).zip"), access.listFolder(folder.absolutePath).map { it.name }.toSet())
    }

    @Test
    fun aMissingFolderIsAnIoError() {
        val gone = File(folder, "gone").absolutePath

        assertFailsWith<IOException> { access.createInFolder(gone, "a.zip", "application/zip") }
        assertFailsWith<IOException> { access.listFolder(gone) }
        assertTrue(runCatching { access.keepAccess(gone); access.releaseAccess(gone) }.isSuccess)
    }
}
