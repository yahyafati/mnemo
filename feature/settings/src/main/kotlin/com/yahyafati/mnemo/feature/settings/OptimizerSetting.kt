package com.yahyafati.mnemo.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.yahyafati.mnemo.core.designsystem.component.MnemoButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoButtonStyle
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.FsrsOptimizationOutcome
import com.yahyafati.mnemo.core.model.FsrsWeights
import com.yahyafati.mnemo.core.model.TransferState
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Settings › Scheduling › FSRS parameters: fit them to the review log, or go back to the defaults. */
internal class OptimizerCallbacks(
    val onOptimize: () -> Unit,
    val onReset: () -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
internal fun FsrsParametersSetting(
    weights: FsrsWeights?,
    state: TransferState<FsrsOptimizationOutcome>,
    callbacks: OptimizerCallbacks,
) {
    val colors = MaterialTheme.colorScheme
    val running = state is TransferState.Running
    Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
        Text(stringResource(R.string.feature_settings_fsrs_parameters), style = MaterialTheme.typography.titleSmall)
        Hint(
            if (weights == null) {
                stringResource(R.string.feature_settings_fsrs_default)
            } else {
                pluralStringResource(
                    R.plurals.feature_settings_fsrs_fitted,
                    weights.trainingReviews,
                    NumberFormat.getIntegerInstance().format(weights.trainingReviews),
                    weights.optimizedAt.atZone(ZoneId.systemDefault()).toLocalDate()
                        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    loss(weights.previousLoss),
                    loss(weights.loss),
                )
            },
        )
        if (state is TransferState.Running) {
            val progress = state.progress
            if (progress == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
        outcomeText(state)?.let { (text, isError) ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (isError) colors.error else colors.secondary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
            MnemoButton(
                text = stringResource(if (running) R.string.feature_settings_fsrs_optimizing else R.string.feature_settings_fsrs_optimize),
                onClick = {
                    callbacks.onDismiss()
                    callbacks.onOptimize()
                },
                enabled = !running,
                leadingIcon = MnemoIcons.Tune,
            )
            if (weights != null) {
                MnemoButton(
                    text = stringResource(R.string.feature_settings_fsrs_reset),
                    onClick = callbacks.onReset,
                    enabled = !running,
                    style = MnemoButtonStyle.Text,
                )
            }
        }
    }
}

/** The last run's result, and whether it is an error. */
@Composable
private fun outcomeText(state: TransferState<FsrsOptimizationOutcome>): Pair<String, Boolean>? = when (state) {
    TransferState.Idle, is TransferState.Running -> null
    is TransferState.Failed -> stringResource(R.string.feature_settings_fsrs_failed) to true
    is TransferState.Succeeded -> when (val outcome = state.result) {
        is FsrsOptimizationOutcome.Applied ->
            stringResource(R.string.feature_settings_fsrs_applied, loss(outcome.previousLoss), loss(outcome.loss)) to false
        is FsrsOptimizationOutcome.NoImprovement ->
            stringResource(R.string.feature_settings_fsrs_unchanged, loss(outcome.previousLoss)) to false
        is FsrsOptimizationOutcome.NotEnoughReviews ->
            pluralStringResource(R.plurals.feature_settings_fsrs_too_few, outcome.required, outcome.required, outcome.trainingReviews) to true
    }
}

private fun loss(value: Double): String = String.format(Locale.getDefault(), "%.3f", value)
