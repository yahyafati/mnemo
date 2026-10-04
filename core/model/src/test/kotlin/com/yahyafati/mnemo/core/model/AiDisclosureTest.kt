package com.yahyafati.mnemo.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiDisclosureTest {
    @Test
    fun textComesBeforeImages() {
        assertEquals(
            listOf(DisclosureStep.Text, DisclosureStep.Images),
            pendingDisclosures(textAccepted = false, sendsImages = true, imagesAccepted = false),
        )
    }

    @Test
    fun eachNoticeIsAskedOncePerProvider() {
        assertEquals(listOf(DisclosureStep.Images), pendingDisclosures(textAccepted = true, sendsImages = true, imagesAccepted = false))
        assertEquals(emptyList(), pendingDisclosures(textAccepted = true, sendsImages = true, imagesAccepted = true))
        // Accepting the images never stands in for the text notice.
        assertEquals(listOf(DisclosureStep.Text), pendingDisclosures(textAccepted = false, sendsImages = true, imagesAccepted = true))
    }

    @Test
    fun aRequestWithoutImagesNeverAsksAboutThem() {
        assertEquals(listOf(DisclosureStep.Text), pendingDisclosures(textAccepted = false, sendsImages = false, imagesAccepted = false))
        assertEquals(emptyList(), pendingDisclosures(textAccepted = true, sendsImages = false, imagesAccepted = false))
    }

    @Test
    fun theImageNoticeIsKeptPerProviderInTheSettings() {
        val settings = UserSettings(imageDisclosureProviders = setOf("a"))
        assertTrue(settings.hasAcceptedImages("a"))
        assertFalse(settings.hasAcceptedImages("b"))
        assertFalse(UserSettings().hasAcceptedImages("a"))
    }

    @Test
    fun onlyAConclusiveCheckChangesVision() {
        val failure = AiFailure(AiProblem.Unauthorized)
        assertEquals(true, ImageCheck.Reads.vision)
        assertEquals(false, ImageCheck.Misread("no").vision)
        assertEquals(false, ImageCheck.Refused(AiFailure(AiProblem.ImagesNotAccepted)).vision)
        assertNull(ImageCheck.Inconclusive(failure).vision)
    }
}
