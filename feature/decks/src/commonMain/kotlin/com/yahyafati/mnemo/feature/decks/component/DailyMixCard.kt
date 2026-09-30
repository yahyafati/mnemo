package com.yahyafati.mnemo.feature.decks.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yahyafati.mnemo.core.designsystem.component.MnemoButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoButtonStyle
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.TodaySummary
import com.yahyafati.mnemo.feature.decks.resources.Res
import com.yahyafati.mnemo.feature.decks.resources.feature_decks_due
import com.yahyafati.mnemo.feature.decks.resources.feature_decks_learning
import com.yahyafati.mnemo.feature.decks.resources.feature_decks_mix_done_message
import com.yahyafati.mnemo.feature.decks.resources.feature_decks_mix_label
import com.yahyafati.mnemo.feature.decks.resources.feature_decks_mix_message
import com.yahyafati.mnemo.feature.decks.resources.feature_decks_mix_title
import com.yahyafati.mnemo.feature.decks.resources.feature_decks_new
import com.yahyafati.mnemo.feature.decks.resources.feature_decks_start_session
import org.jetbrains.compose.resources.stringResource

/** The hero card from the mockup: today's queue across all decks and a Start Session button. */
@Composable
internal fun DailyMixCard(
    today: TodaySummary,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MnemoTheme.spacing
    val hasCards = today.totalToStudy > 0
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.primary,
        contentColor = colors.onPrimary,
        shadowElevation = 3.dp,
    ) {
        Column {
            Row(Modifier.padding(spacing.md), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .background(colors.primaryContainer, MaterialTheme.shapes.small)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(MnemoIcons.Bolt, null, tint = colors.onPrimaryContainer, modifier = Modifier.size(12.dp))
                        Text(
                            text = stringResource(Res.string.feature_decks_mix_label).uppercase(),
                            style = MnemoTheme.typography.metricSm.copy(fontSize = 11.sp),
                            color = colors.onPrimaryContainer,
                        )
                    }
                    Text(
                        text = stringResource(Res.string.feature_decks_mix_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                    Text(
                        text = stringResource(
                            if (hasCards) Res.string.feature_decks_mix_message else Res.string.feature_decks_mix_done_message,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onPrimary.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                }
                Box(
                    Modifier
                        .padding(start = spacing.sm)
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MnemoIcons.Play, contentDescription = null)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    QueueCount(today.dueCount, stringResource(Res.string.feature_decks_due))
                    Divider()
                    QueueCount(today.newCount, stringResource(Res.string.feature_decks_new))
                    Divider()
                    QueueCount(today.learningCount, stringResource(Res.string.feature_decks_learning))
                }
                MnemoButton(
                    text = stringResource(Res.string.feature_decks_start_session),
                    onClick = onStart,
                    enabled = hasCards,
                    style = MnemoButtonStyle.Secondary,
                    trailingIcon = MnemoIcons.ArrowForward,
                )
            }
        }
    }
}

@Composable
private fun QueueCount(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MnemoTheme.typography.metricLg.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = label.uppercase(),
            style = MnemoTheme.typography.metricSm.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun Divider() {
    Text("/", style = MnemoTheme.typography.metricLg, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f))
}
