package com.yahyafati.mnemo.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.ReminderSettings
import com.yahyafati.mnemo.core.model.UserSettings
import com.yahyafati.mnemo.core.ui.format.rememberIs24HourFormat
import com.yahyafati.mnemo.core.ui.permission.AppPermission
import com.yahyafati.mnemo.core.ui.permission.rememberPermissionRequest
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A setting with a switch. The whole row toggles it, so the touch target is large and a screen
 * reader reads the label with the switch's state.
 */
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Column(Modifier.weight(1f).padding(end = MnemoTheme.spacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            summary?.let { Hint(it) }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Settings › Study: how cards sound. */
@Composable
internal fun StudySection(settings: UserSettings, onAutoPlayAudio: (Boolean) -> Unit) {
    Section(stringResource(R.string.feature_settings_study), MnemoIcons.StudySelected) {
        SwitchRow(
            title = stringResource(R.string.feature_settings_autoplay),
            summary = stringResource(R.string.feature_settings_autoplay_summary),
            checked = settings.autoPlayAudio,
            onCheckedChange = onAutoPlayAudio,
        )
    }
}

/**
 * Settings › Reminders: one notification a day at a chosen time, only when cards are due. Turning
 * it on asks for notification permission first where the platform requires it (Android 13+);
 * without it the reminder can't show. Only shown where the platform has reminders.
 */
@Composable
internal fun ReminderSection(reminder: ReminderSettings, onChange: (ReminderSettings) -> Unit) {
    // Whatever the answer, the reminder turns on; the hint below says when notifications are blocked.
    val notifications = rememberPermissionRequest(AppPermission.Notifications) { onChange(reminder.copy(enabled = true)) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    Section(stringResource(R.string.feature_settings_reminders), MnemoIcons.Notifications) {
        SwitchRow(
            title = stringResource(R.string.feature_settings_reminder),
            summary = stringResource(R.string.feature_settings_reminder_summary),
            checked = reminder.enabled,
            onCheckedChange = { on ->
                if (on && notifications.canRequest) {
                    notifications.launch()
                } else {
                    onChange(reminder.copy(enabled = on))
                }
            },
        )
        if (reminder.enabled) {
            OutlinedButton(onClick = { pickingTime = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(MnemoIcons.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.feature_settings_reminder_time, reminder.time.format(TIME_FORMAT)),
                    modifier = Modifier.padding(start = MnemoTheme.spacing.sm),
                )
            }
            if (!notifications.isGranted) Hint(stringResource(R.string.feature_settings_reminder_blocked))
        }
    }
    if (pickingTime) {
        ReminderTimeDialog(
            initial = reminder.time,
            onPicked = {
                pickingTime = false
                onChange(reminder.copy(time = it))
            },
            onDismiss = { pickingTime = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(initial: LocalTime, onPicked: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = rememberIs24HourFormat(),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_settings_reminder_pick_time)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.feature_settings_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_settings_cancel)) } },
    )
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
