package com.yahyafati.mnemo.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.time.Instant

fun main() = application {
    val cards = sampleCards(Instant.now())
    Window(
        onCloseRequest = ::exitApplication,
        title = "Mnemo",
        state = rememberWindowState(size = DpSize(760.dp, 520.dp)),
    ) {
        SampleCardScreen(cards)
    }
}
