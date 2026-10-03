package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.data.repository.MediaRepository
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.repository.FakeMediaRepository
import org.junit.Test
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncMediaFilesTest : PlatformTest() {
    // The temporary folder rule is only in place once the test runs.
    private val directory by lazy { File(tmp.newFolder(), "media") }
    private val repository: MediaRepository by lazy {
        object : MediaRepository by FakeMediaRepository() {
            override val directory: File = this@SyncMediaFilesTest.directory

            override fun file(hash: String) = File(this@SyncMediaFilesTest.directory, hash)
        }
    }
    private val files by lazy { RepositorySyncMediaFiles(repository) }
    private val hash = "ab".repeat(32)

    @Test
    fun aFileIsStoredUnderItsHashAndReadBack() {
        assertFalse(files.exists(hash))
        assertNull(files.read(hash))

        files.write(hash, byteArrayOf(1, 2, 3))

        assertTrue(files.exists(hash))
        assertContentEquals(byteArrayOf(1, 2, 3), files.read(hash))
        assertEquals(listOf(hash), directory.list()!!.toList(), "no temporary file is left behind")
    }

    @Test
    fun aFileThatIsAlreadyThereIsLeftAlone() {
        files.write(hash, byteArrayOf(1))
        files.write(hash, byteArrayOf(2))
        assertContentEquals(byteArrayOf(1), files.read(hash))
    }

    @Test
    fun aNameFromAChangeFileCannotLeaveTheFolder() {
        for (name in listOf("../escape", "a/b", "AB".repeat(32), "ab", "")) {
            assertFailsWith<IllegalArgumentException>(name) { files.write(name, byteArrayOf(1)) }
            assertFalse(files.exists(name))
            assertNull(files.read(name))
        }
    }
}
