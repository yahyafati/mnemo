package com.yahyafati.mnemo.core.sync

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncPathsTest {
    private val sha = "a".repeat(64)

    @Test
    fun theLayoutIsWhatTheAdrSays() {
        assertEquals("devices/$DEVICE_A/device.json", SyncPaths.deviceInfo(DEVICE_A))
        assertEquals("devices/$DEVICE_A/changes/12.mnc", SyncPaths.changeFile(DEVICE_A, 12))
        assertEquals("snapshots/$DEVICE_A-345.mns", SyncPaths.snapshot(DEVICE_A, 345))
        assertEquals("media/$sha", SyncPaths.media(sha))
        assertEquals("sync.json", SyncPaths.MANIFEST)
    }

    @Test
    fun pathsParseBack() {
        assertEquals(DEVICE_A, SyncPaths.deviceOf(SyncPaths.changeFile(DEVICE_A, 3)))
        assertEquals(DEVICE_A, SyncPaths.deviceOf(SyncPaths.deviceInfo(DEVICE_A)))
        assertEquals(3L, SyncPaths.seqOf(SyncPaths.changeFile(DEVICE_A, 3)))
        assertEquals(DEVICE_A to 345L, SyncPaths.snapshotOf(SyncPaths.snapshot(DEVICE_A, 345)))
        assertEquals(sha, SyncPaths.mediaOf(SyncPaths.media(sha)))
    }

    @Test
    fun otherNamesAreNotChangeFilesSnapshotsOrMedia() {
        assertNull(SyncPaths.seqOf("devices/$DEVICE_A/device.json"))
        assertNull(SyncPaths.seqOf("devices/$DEVICE_A/changes/x.mnc"))
        assertNull(SyncPaths.seqOf("devices/$DEVICE_A/changes/3.mnc.tmp"))
        assertNull(SyncPaths.seqOf("devices/$DEVICE_A/other/3.mnc"))
        assertNull(SyncPaths.snapshotOf("snapshots/nothing.mns"))
        assertNull(SyncPaths.snapshotOf("snapshots/$DEVICE_A-1.zip"))
        assertNull(SyncPaths.mediaOf("media/not-a-hash"))
        assertNull(SyncPaths.deviceOf("sync.json"))
    }

    @Test
    fun onlyPlainNamesAreValid() {
        assertTrue(SyncPaths.isValid("devices/$DEVICE_A/changes/1.mnc"))
        for (bad in listOf("", "/a", "a/", "a//b", "../a", "a/../b", "a b", "a/.hidden", "a__b", "devices/x (1)", "a\\b", "é", "notes.txt", "other/sync.json")) {
            assertFalse(SyncPaths.isValid(bad), bad)
        }
        assertFailsWith<IllegalArgumentException> { SyncPaths.deviceInfo("../../etc") }
        assertFailsWith<IllegalArgumentException> { SyncPaths.deviceInfo("a/b") }
        assertFailsWith<IllegalArgumentException> { SyncPaths.media("short") }
    }
}
