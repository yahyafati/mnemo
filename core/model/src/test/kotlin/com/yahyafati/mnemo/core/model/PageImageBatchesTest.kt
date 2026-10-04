package com.yahyafati.mnemo.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageImageBatchesTest {
    @Test
    fun pagesGoThreeToARequestInOrder() {
        assertEquals(listOf(listOf(14, 15, 16), listOf(17, 20)), PageImageBatches.of(listOf(14, 15, 16, 17, 20)))
    }

    @Test
    fun theSelectionIsSortedAndDeduplicatedFirst() {
        assertEquals(listOf(listOf(1, 2, 9)), PageImageBatches.of(listOf(9, 2, 1, 2)))
        assertEquals(emptyList(), PageImageBatches.of(emptyList()))
    }

    @Test
    fun aPageIsWorthItsWordsOrTheTextLayersIfMore() {
        assertEquals(900, PageImageBatches.words(3, layerWords = 120))
        assertEquals(1_400, PageImageBatches.words(3, layerWords = 1_400))
    }

    @Test
    fun onlyTheImageModeSendsImagesWithoutTranscribing() {
        assertTrue(PdfReadMode.PageImages.usesAi)
        assertEquals(listOf(PdfReadMode.Auto, PdfReadMode.ReadWithAi), PdfReadMode.entries.filter { it.transcribes })
    }
}
