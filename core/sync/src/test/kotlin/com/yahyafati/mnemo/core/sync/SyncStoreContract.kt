package com.yahyafati.mnemo.core.sync

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** What every [SyncStore] must do; the subclasses supply the store. */
abstract class SyncStoreContract {
    abstract val store: SyncStore

    @Test
    fun aWrittenFileReadsBackAndANewFileMayNotReplaceIt() {
        store.write("media/${"b".repeat(64)}", byteArrayOf(1, 2, 3))
        assertContentEquals(byteArrayOf(1, 2, 3), store.read("media/${"b".repeat(64)}"))
        assertFailsWith<SyncAlreadyExistsException> { store.write("media/${"b".repeat(64)}", byteArrayOf(9)) }
        assertContentEquals(byteArrayOf(1, 2, 3), store.read("media/${"b".repeat(64)}"))
    }

    @Test
    fun anEmptyFileIsAFile() {
        store.write("sync.json", ByteArray(0))
        assertEquals(0, store.read("sync.json").size)
        assertEquals(listOf("sync.json"), store.list(""))
    }

    @Test
    fun aMissingFileIsNotFound() {
        assertFailsWith<SyncNotFoundException> { store.read("sync.json") }
        store.write("devices/$DEVICE_A/device.json", byteArrayOf(1))
        assertFailsWith<SyncNotFoundException> { store.read("devices/$DEVICE_B/device.json") }
    }

    @Test
    fun overwriteCreatesAndReplaces() {
        val path = "devices/$DEVICE_A/device.json"
        store.overwrite(path, byteArrayOf(1))
        store.overwrite(path, byteArrayOf(2, 2))
        assertContentEquals(byteArrayOf(2, 2), store.read(path))
        assertEquals(listOf(path), store.list(""))
    }

    @Test
    fun listFiltersByPrefixAndSorts() {
        store.write("devices/$DEVICE_B/changes/2.mnc", byteArrayOf(1))
        store.write("devices/$DEVICE_A/changes/10.mnc", byteArrayOf(1))
        store.write("devices/$DEVICE_A/changes/9.mnc", byteArrayOf(1))
        store.write("devices/$DEVICE_A/device.json", byteArrayOf(1))
        store.write("sync.json", byteArrayOf(1))
        assertEquals(
            listOf("devices/$DEVICE_A/changes/10.mnc", "devices/$DEVICE_A/changes/9.mnc"),
            store.list("devices/$DEVICE_A/changes/"),
        )
        assertEquals(4, store.list("devices/").size)
        assertEquals(5, store.list("").size)
        assertEquals(emptyList(), store.list("snapshots/"))
    }

    @Test
    fun deleteRemovesAFileAndForgivesAMissingOne() {
        store.write("sync.json", byteArrayOf(1))
        store.delete("sync.json")
        store.delete("sync.json")
        assertEquals(emptyList(), store.list(""))
        assertFailsWith<SyncNotFoundException> { store.read("sync.json") }
        // The name is free again.
        store.write("sync.json", byteArrayOf(2))
        assertContentEquals(byteArrayOf(2), store.read("sync.json"))
    }

    @Test
    fun bigFilesSurvive() {
        val bytes = ByteArray(3_000_000) { (it * 31).toByte() }
        store.write("snapshots/$DEVICE_A-1.mns", bytes)
        assertContentEquals(bytes, store.read("snapshots/$DEVICE_A-1.mns"))
    }

    @Test
    fun invalidPathsAreProgrammingErrors() {
        assertFailsWith<IllegalArgumentException> { store.write("../escape", byteArrayOf(1)) }
        assertFailsWith<IllegalArgumentException> { store.read("a b") }
        assertFailsWith<IllegalArgumentException> { store.overwrite("a__b", byteArrayOf(1)) }
        assertFailsWith<IllegalArgumentException> { store.delete("/abs") }
        assertTrue(store.list("").isEmpty())
    }
}
