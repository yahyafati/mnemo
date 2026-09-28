package com.yahyafati.mnemo.feature.study.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.component.MnemoButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoButtonStyle
import com.yahyafati.mnemo.core.designsystem.component.StatTile
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.feature.study.R
import com.yahyafati.mnemo.feature.study.SessionSummary

@Composable
internal fun SessionSummaryView(
    summary: SessionSummary,
    laterCount: Int,
    onCheckAgain: () -> Unit,
    onDone: () -> Unit,
    doneLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MnemoTheme.spacing
    val time = formatDuration(summary.totalTimeMs)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenMargin, vertical = spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Icon(MnemoIcons.CheckCircle, null, tint = colors.secondary, modifier = Modifier.size(48.dp))
        Text(stringResource(R.string.feature_study_finished_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = pluralStringResource(R.plurals.feature_study_finished_message, summary.reviewed, summary.reviewed, time),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            StatTile(stringResource(R.string.feature_study_stat_reviewed), summary.reviewed.toString(), Modifier.weight(1f))
            StatTile(
                label = stringResource(R.string.feature_study_stat_accuracy),
                value = "${summary.accuracyPercent}%",
                valueColor = colors.secondary,
                modifier = Modifier.weight(1f),
            )
            StatTile(stringResource(R.string.feature_study_stat_time), time, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Rating.entries.forEach { rating ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                ) {
                    Text((summary.ratings[rating] ?: 0).toString(), style = MnemoTheme.typography.metricLg, color = rating.color())
                    Text(stringResource(rating.labelRes()), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }
        }
        if (laterCount > 0) {
            Text(
                text = pluralStringResource(R.plurals.feature_study_later_message, laterCount, laterCount),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            MnemoButton(stringResource(R.string.feature_study_check_again), onCheckAgain, style = MnemoButtonStyle.Text)
        }
        MnemoButton(doneLabel, onDone)
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}
