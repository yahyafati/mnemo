package com.yahyafati.mnemo.core.ui.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.component.MnemoIconButton
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.ui.resources.core_ui_cancel
import com.yahyafati.mnemo.core.ui.resources.core_ui_deck_category
import com.yahyafati.mnemo.core.ui.resources.core_ui_deck_category_hint
import com.yahyafati.mnemo.core.ui.resources.core_ui_deck_create
import com.yahyafati.mnemo.core.ui.resources.core_ui_deck_description
import com.yahyafati.mnemo.core.ui.resources.core_ui_deck_edit_title
import com.yahyafati.mnemo.core.ui.resources.core_ui_deck_exam_add
import com.yahyafati.mnemo.core.ui.resources.core_ui_deck_exam_clear
import com.yahyafati.mnemo.core.ui.resources.core_ui_deck_exam_on
import com.yahyafati.mnemo.core.ui.resources.core_ui_deck_name
import com.yahyafati.mnemo.core.ui.resources.core_ui_deck_name_hint
import com.yahyafati.mnemo.core.ui.resources.core_ui_deck_new_title
import com.yahyafati.mnemo.core.ui.resources.core_ui_deck_save
import com.yahyafati.mnemo.core.ui.resources.core_ui_ok
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.ui.resources.Res
import org.jetbrains.compose.resources.stringResource

/** What the deck dialog edits. [path] is the full name, e.g. "Languages::Japanese". */
data class DeckDraft(
    val path: String = "",
    val category: String = "",
    val description: String = "",
    /** The exam the deck prepares for: the deck counts down to it. */
    val examDate: LocalDate? = null,
)

/**
 * Create or edit a deck. Shared by Decks and Create, which both let the user make a deck without
 * leaving the screen.
 */
@Composable
fun DeckEditorDialog(
    onConfirm: (DeckDraft) -> Unit,
    onDismiss: () -> Unit,
    initial: DeckDraft = DeckDraft(),
    isNew: Boolean = true,
) {
    var path by rememberSaveable { mutableStateOf(initial.path) }
    var category by rememberSaveable { mutableStateOf(initial.category) }
    var description by rememberSaveable { mutableStateOf(initial.description) }
    var examDay by rememberSaveable { mutableStateOf(initial.examDate?.toEpochDay()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val canSave = path.split("::").any { it.isNotBlank() }
    val focusRequester = remember { FocusRequester() }
    val confirm = {
        if (canSave) onConfirm(DeckDraft(path.trim(), category.trim(), description.trim(), examDay?.let(LocalDate::ofEpochDay)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) Res.string.core_ui_deck_new_title else Res.string.core_ui_deck_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(stringResource(Res.string.core_ui_deck_name)) },
                    supportingText = { Text(stringResource(Res.string.core_ui_deck_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(Res.string.core_ui_deck_category)) },
                    placeholder = { Text(stringResource(Res.string.core_ui_deck_category_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(Res.string.core_ui_deck_description)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExamDateRow(
                    date = examDay?.let(LocalDate::ofEpochDay),
                    onPick = { pickingDate = true },
                    onClear = { examDay = null },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = confirm, enabled = canSave) {
                Text(stringResource(if (isNew) Res.string.core_ui_deck_create else Res.string.core_ui_deck_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.core_ui_cancel)) }
        },
    )
    LaunchedEffect(Unit) { if (isNew) focusRequester.requestFocus() }
    if (pickingDate) {
        ExamDatePicker(
            initial = examDay?.let(LocalDate::ofEpochDay),
            onPicked = {
                examDay = it.toEpochDay()
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

@Composable
private fun ExamDateRow(date: LocalDate?, onPick: () -> Unit, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onPick, modifier = Modifier.weight(1f)) {
            Icon(MnemoIcons.Event, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = date?.let { stringResource(Res.string.core_ui_deck_exam_on, it.format(DATE_FORMAT)) }
                    ?: stringResource(Res.string.core_ui_deck_exam_add),
                modifier = Modifier.padding(start = MnemoTheme.spacing.sm),
            )
        }
        if (date != null) {
            MnemoIconButton(
                icon = MnemoIcons.Close,
                contentDescription = stringResource(Res.string.core_ui_deck_exam_clear),
                onClick = onClear,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamDatePicker(initial: LocalDate?, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The date picker works in UTC milliseconds at midnight.
    val state = rememberDatePickerState(initialSelectedDateMillis = initial?.let { it.toEpochDay() * DAY_MS })
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPicked(LocalDate.ofEpochDay(Math.floorDiv(it, DAY_MS))) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(Res.string.core_ui_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.core_ui_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

private const val DAY_MS = 86_400_000L
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Preview
@Composable
private fun DeckEditorDialogPreview() {
    MnemoTheme {
        DeckEditorDialog(onConfirm = {}, onDismiss = {}, initial = DeckDraft("Languages::Japanese", "Language"))
    }
}
