package com.yahyafati.mnemo.core.sync.store

import com.yahyafati.mnemo.core.sync.DEVICE_A
import com.yahyafati.mnemo.core.sync.DirectoryDocumentAccess
import com.yahyafati.mnemo.core.sync.SyncAlreadyExistsException
import com.yahyafati.mnemo.core.sync.SyncAuthException
import com.yahyafati.mnemo.core.sync.SyncNotFoundException
import com.yahyafati.mnemo.core.sync.SyncOfflineException
import com.yahyafati.mnemo.core.sync.SyncQuotaException
import com.yahyafati.mnemo.core.sync.SyncStore
import com.yahyafati.mnemo.core.sync.SyncStoreContract
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InMemorySyncStoreContractTest : SyncStoreContract() {
    override val store: SyncStore = InMemorySyncStore()

    @Test
    fun aFailureIsThrownByEveryCallUntilItIsCleared() {
        val memory = InMemorySyncStore()
        memory.failure = SyncOfflineException("no network")
        assertFailsWith<SyncOfflineException> { memory.list("") }
        assertFailsWith<SyncOfflineException> { memory.read("sync.json") }
        assertFailsWith<SyncOfflineException> { memory.write("sync.json", byteArrayOf(1)) }
        assertFailsWith<SyncOfflineException> { memory.overwrite("sync.json", byteArrayOf(1)) }
        assertFailsWith<SyncOfflineException> { memory.delete("sync.json") }
        memory.failure = null
        memory.write("sync.json", byteArrayOf(1))
    }
}

class FolderSyncStoreContractTest : SyncStoreContract() {
    @get:Rule
    val folder = TemporaryFolder()

    private val documents = DirectoryDocumentAccess()

    override val store: SyncStore by lazy { FolderSyncStore(documents, folder.root.absolutePath) }

    private fun names() = folder.root.list().orEmpty().sorted()

    @Test
    fun theLayoutIsFlattenedIntoOneFolder() {
        store.write("devices/$DEVICE_A/changes/3.mnc", byteArrayOf(1))
        store.write("sync.json", byteArrayOf(1))
        assertEquals(listOf("devices__${DEVICE_A}__changes__3.mnc", "sync.json"), names())
    }

    @Test
    fun aSecondStoreOnTheSameFolderSeesTheFiles() {
        store.write("sync.json", byteArrayOf(7))
        val other = FolderSyncStore(DirectoryDocumentAccess(), folder.root.absolutePath)
        assertContentEquals(byteArrayOf(7), other.read("sync.json"))
        other.write("media/${"c".repeat(64)}", byteArrayOf(8))
        assertEquals(listOf("media/${"c".repeat(64)}", "sync.json"), store.list(""))
        assertContentEquals(byteArrayOf(8), store.read("media/${"c".repeat(64)}"))
    }

    @Test
    fun filesThatAreNotOursAreNeverListed() {
        File(folder.root, ".DS_Store").writeText("x")
        File(folder.root, "notes.txt").writeText("x")
        File(folder.root, "sync (1).json").writeText("a conflict copy")
        File(folder.root, "devices__${DEVICE_A}__changes__3.mnc (1)").writeText("a conflict copy")
        File(folder.root, "sub").mkdir()
        store.write("sync.json", byteArrayOf(1))
        assertEquals(listOf("sync.json"), store.list(""))
    }

    @Test
    fun aNameTakenBehindOurBackIsStillAnAlreadyExistsErrorAndLeavesNoCopy() {
        assertEquals(emptyList(), store.list(""))
        // Another device's sync tool delivers the file after our listing: the platform renames our create.
        File(folder.root, "sync.json").writeText("theirs")
        assertFailsWith<SyncAlreadyExistsException> { store.write("sync.json", byteArrayOf(1)) }
        assertEquals(listOf("sync.json"), names())
        assertEquals("theirs", File(folder.root, "sync.json").readText())
    }

    @Test
    fun aFileDeletedBehindOurBackIsNotFoundAndItsNameIsFreeAgain() {
        store.write("sync.json", byteArrayOf(1))
        File(folder.root, "sync.json").delete()
        assertFailsWith<SyncNotFoundException> { store.read("sync.json") }
        store.write("sync.json", byteArrayOf(2))
        assertContentEquals(byteArrayOf(2), store.read("sync.json"))
    }

    @Test
    fun aFolderThatIsGoneIsNotFound() {
        val gone = FolderSyncStore(documents, File(folder.root, "missing").absolutePath)
        assertFailsWith<SyncNotFoundException> { gone.list("") }
        assertFailsWith<SyncNotFoundException> { gone.read("sync.json") }
        assertFailsWith<SyncNotFoundException> { gone.write("sync.json", byteArrayOf(1)) }
    }

    @Test
    fun aFullDiskIsAQuotaErrorAndLeavesNoPartialFile() {
        documents.writeLimit = 10
        assertFailsWith<SyncQuotaException> { store.write("sync.json", ByteArray(100)) }
        assertEquals(emptyList(), names())
        documents.writeLimit = null
        store.write("sync.json", ByteArray(100))
    }

    @Test
    fun aRevokedFolderIsAnAuthError() {
        val revoked = FolderSyncStore(
            object : com.yahyafati.mnemo.core.common.platform.DocumentAccess by documents {
                override fun listFolder(folderUri: String) = throw java.io.IOException("Permission denied for the folder")
            },
            folder.root.absolutePath,
        )
        assertFailsWith<SyncAuthException> { revoked.list("") }
    }
}
