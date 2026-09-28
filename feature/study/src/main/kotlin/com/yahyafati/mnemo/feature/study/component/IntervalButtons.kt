package com.yahyafati.mnemo.feature.study.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.ui.format.formatInterval
import com.yahyafati.mnemo.feature.study.R
import java.time.Duration

/** The four FSRS rating buttons, each with the interval it would schedule. */
@Composable
internal fun IntervalButtons(
    intervals: Map<Rating, Duration>,
    onRate: (Rating) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs)) {
        Rating.entries.forEach { rating ->
            val label = stringResource(rating.labelRes())
            val interval = intervals[rating]?.let(::formatInterval).orEmpty()
            val description = stringResource(R.string.feature_study_rate_description, label, interval)
            Surface(
                onClick = { onRate(rating) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = description },
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = rating.color(),
                    )
                    Text(
                        text = interval,
                        style = MnemoTheme.typography.metricSm,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

internal fun Rating.labelRes(): Int = when (this) {
    Rating.Again -> R.string.feature_study_again
    Rating.Hard -> R.string.feature_study_hard
    Rating.Good -> R.string.feature_study_good
    Rating.Easy -> R.string.feature_study_easy
}

@Composable
internal fun Rating.color(): Color = when (this) {
    Rating.Again -> MaterialTheme.colorScheme.error
    Rating.Hard -> MaterialTheme.colorScheme.tertiary
    Rating.Good -> MaterialTheme.colorScheme.primary
    Rating.Easy -> MaterialTheme.colorScheme.secondary
}
