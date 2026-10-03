package com.yahyafati.mnemo.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.ui.keyboard.Shortcut
import com.yahyafati.mnemo.core.ui.keyboard.Shortcuts
import com.yahyafati.mnemo.shell.resources.Res
import com.yahyafati.mnemo.shell.resources.shortcuts_back
import com.yahyafati.mnemo.shell.resources.shortcuts_close
import com.yahyafati.mnemo.shell.resources.shortcuts_cloze
import com.yahyafati.mnemo.shell.resources.shortcuts_edit_note
import com.yahyafati.mnemo.shell.resources.shortcuts_find
import com.yahyafati.mnemo.shell.resources.shortcuts_group_anywhere
import com.yahyafati.mnemo.shell.resources.shortcuts_group_editing
import com.yahyafati.mnemo.shell.resources.shortcuts_group_studying
import com.yahyafati.mnemo.shell.resources.shortcuts_import
import com.yahyafati.mnemo.shell.resources.shortcuts_new_note
import com.yahyafati.mnemo.shell.resources.shortcuts_or
import com.yahyafati.mnemo.shell.resources.shortcuts_rate
import com.yahyafati.mnemo.shell.resources.shortcuts_reveal
import com.yahyafati.mnemo.shell.resources.shortcuts_save_note
import com.yahyafati.mnemo.shell.resources.shortcuts_settings
import com.yahyafati.mnemo.shell.resources.shortcuts_show_list
import com.yahyafati.mnemo.shell.resources.shortcuts_sync_now
import com.yahyafati.mnemo.shell.resources.shortcuts_tabs
import com.yahyafati.mnemo.shell.resources.shortcuts_title
import com.yahyafati.mnemo.shell.resources.shortcuts_undo
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One row of the list: what it does, and the keys (several ways to press it are separated by "or"). */
private class ShortcutRow(val action: StringResource, val keys: List<String>)

private class ShortcutGroup(val title: StringResource, val rows: List<ShortcutRow>)

private fun List<Shortcut>.labels() = map { it.label() }

/** Every shortcut of the app, grouped by where it works. Opens with `?` and from Help. */
private val shortcutGroups: List<ShortcutGroup> = listOf(
    ShortcutGroup(
        Res.string.shortcuts_group_anywhere,
        listOf(
            ShortcutRow(Res.string.shortcuts_new_note, listOf(Shortcuts.NewNote.label())),
            ShortcutRow(Res.string.shortcuts_find, listOf(Shortcuts.Search.label())),
            ShortcutRow(Res.string.shortcuts_tabs, listOf("${Shortcuts.Tabs.first().label()}–${Shortcuts.Tabs.size}")),
            ShortcutRow(Res.string.shortcuts_settings, listOf(Shortcuts.Settings.label())),
            ShortcutRow(Res.string.shortcuts_back, listOf(Shortcuts.Back.label())),
            ShortcutRow(Res.string.shortcuts_import, listOf(Shortcuts.Import.label())),
            ShortcutRow(Res.string.shortcuts_sync_now, listOf(Shortcuts.SyncNow.label())),
            ShortcutRow(Res.string.shortcuts_show_list, listOf(Shortcuts.ShowShortcuts.label())),
        ),
    ),
    ShortcutGroup(
        Res.string.shortcuts_group_studying,
        listOf(
            ShortcutRow(Res.string.shortcuts_reveal, Shortcuts.Reveal.labels()),
            ShortcutRow(Res.string.shortcuts_rate, listOf("1–${Shortcuts.Answers.size}")),
            ShortcutRow(Res.string.shortcuts_undo, listOf(Shortcuts.Undo.label())),
            ShortcutRow(Res.string.shortcuts_edit_note, listOf(Shortcuts.EditNote.label())),
        ),
    ),
    ShortcutGroup(
        Res.string.shortcuts_group_editing,
        listOf(
            ShortcutRow(Res.string.shortcuts_save_note, listOf(Shortcuts.Save.label())),
            ShortcutRow(Res.string.shortcuts_cloze, listOf(Shortcuts.Cloze.label())),
        ),
    ),
)

@Composable
fun ShortcutsDialog(onDismiss: () -> Unit) {
    val spacing = MnemoTheme.spacing
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(MnemoIcons.Keyboard, null) },
        title = { Text(stringResource(Res.string.shortcuts_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                shortcutGroups.forEach { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(
                            text = stringResource(group.title).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        group.rows.forEach { row -> ShortcutRowView(row) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.shortcuts_close)) } },
    )
}

@Composable
private fun ShortcutRowView(row: ShortcutRow) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(row.action), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        row.keys.forEachIndexed { index, key ->
            if (index > 0) {
                Text(
                    text = stringResource(Res.string.shortcuts_or),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(
                    text = key,
                    style = MnemoTheme.typography.metricSm,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}
