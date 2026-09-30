package com.yahyafati.mnemo.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.model.Rating

/** The D1 window: sample cards with the FSRS interval behind each rating button. */
@Composable
fun SampleCardScreen(cards: List<SampleCard>) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Mnemo for desktop", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Shared code from :core:model and :core:scheduler. " +
                        "Next review after each answer, by FSRS-6:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                cards.forEach { SampleCardPanel(it) }
            }
        }
    }
}

@Composable
private fun SampleCardPanel(sample: SampleCard) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(sample.title, style = MaterialTheme.typography.titleMedium)
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
