package com.yahyafati.mnemo.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.component.MnemoLogo
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.CardSides
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.ui.card.CardFace
import com.yahyafati.mnemo.core.ui.platform.desktop.ProvideDesktopPlatform
import java.io.File

/**
 * The window until the app shell arrives (D6): the shared theme and card rendering (D5) on sample
 * cards, with the FSRS interval behind each rating button (D1).
 */
@Composable
fun SampleCardScreen(cards: List<SampleCard>, mediaDirectory: File) {
    ProvideDesktopPlatform(mediaDirectory) {
        MnemoTheme {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MnemoLogo()
                        Text("Mnemo for desktop", style = MaterialTheme.typography.headlineMedium)
                    }
                    Text(
                        "Shared code from :core:model, :core:scheduler, :core:designsystem and :core:ui. " +
                            "Next review after each answer, by FSRS-6:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    cards.forEach { SampleCardPanel(it) }
                }
            }
        }
    }
}

@Composable
private fun SampleCardPanel(sample: SampleCard) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(sample.title, style = MaterialTheme.typography.titleMedium)
            CardFace(sides = sample.sides, revealed = true)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Rating.entries.forEach { rating ->
                    Column {
                        Text(rating.name, style = MaterialTheme.typography.labelMedium)
                        Text(
                            formatInterval(sample.intervals.getValue(rating)),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
        }
    }
}

/** What each sample card shows: Markdown, and math drawn by JLaTeXMath. */
private val SampleCard.sides: CardSides
    get() = if (card.id == "sample-new") {
        CardSides(
            front = "What does **long-term potentiation** strengthen?",
            back = "Synapses, after repeated high-frequency stimulation.\n\n- NMDA receptors\n- `Ca²⁺` influx",
        )
    } else {
        CardSides(
            front = "What is the retrievability of a card after \\(t\\) days?",
            back = "\\[R(t, S) = \\left(1 + \\frac{19}{81}\\cdot\\frac{t}{S}\\right)^{-0.5}\\]",
        )
    }
