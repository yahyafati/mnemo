package com.yahyafati.mnemo.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaRefTest {
    @Test
    fun extensionsRoundTripThroughMimeTypes() {
        for (extension in listOf("png", "jpg", "gif", "webp", "svg", "bmp", "mp3", "ogg", "wav", "m4a", "mp4", "webm")) {
            assertEquals(extension, MediaRef.extensionFor(MediaRef.mimeTypeFor("file.$extension")))
        }
    }

    @Test
    fun aMimeTypeParameterIsIgnored() {
        assertEquals("png", MediaRef.extensionFor("Image/PNG; charset=binary"))
    }

    @Test
    fun unknownTypesHaveNoExtension() {
        assertNull(MediaRef.extensionFor("application/octet-stream"))
        assertNull(MediaRef.extensionFor(""))
    }
}
