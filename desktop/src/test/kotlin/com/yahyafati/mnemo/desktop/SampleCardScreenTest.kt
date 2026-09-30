package com.yahyafati.mnemo.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import java.io.File
import java.time.Instant
import kotlin.test.Test

class SampleCardScreenTest {
    private val cards = sampleCards(Instant.parse("2026-09-30T08:00:00Z"))

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `shows each sample card with its four ratings`() = runComposeUiTest {
        setContent { SampleCardScreen(cards, mediaDirectory = File(System.getProperty("java.io.tmpdir"), "mnemo-no-media")) }

        onNodeWithText("Mnemo for desktop").assertExists()
        onNodeWithText("New card").assertExists()
        listOf("Again", "Hard", "Good", "Easy").forEach { rating ->
            onAllNodesWithText(rating).assertCountEquals(cards.size)
        }
    }
}
