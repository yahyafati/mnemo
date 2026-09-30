package com.yahyafati.mnemo.core.ui.files

import com.yahyafati.mnemo.core.ui.files.desktop.DesktopFileDialogs
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopFileDialogsTest {
    @Test
    fun aWildcardOrAnUnknownTypeShowsEveryFile() {
        assertNull(DesktopFileDialogs.extensionsFor(listOf("*/*")))
        assertNull(DesktopFileDialogs.extensionsFor(listOf("application/zip", "application/octet-stream")))
        assertNull(DesktopFileDialogs.extensionsFor(listOf("application/x-unknown")))
    }

    @Test
    fun aTypeFiltersByItsExtensions() {
        assertEquals(listOf("pdf"), DesktopFileDialogs.extensionsFor(listOf("application/pdf")))
        assertTrue("jpg" in DesktopFileDialogs.extensionsFor(listOf("image/*")).orEmpty())
        assertTrue("mp3" in DesktopFileDialogs.extensionsFor(listOf("audio/*")).orEmpty())
        assertTrue("apkg" in DesktopFileDialogs.extensionsFor(listOf("application/zip")).orEmpty())
    }
}
