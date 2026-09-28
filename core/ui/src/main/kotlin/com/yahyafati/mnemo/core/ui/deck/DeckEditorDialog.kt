package com.yahyafati.mnemo.core.ui.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.ui.R

/** What the deck dialog edits. [path] is the full name, e.g. "Languages::Japanese". */
data class DeckDraft(
    val path: String = "",
    val category: String = "",
    val description: String = "",
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
    val canSave = path.split("::").any { it.isNotBlank() }
    val focusRequester = remember { FocusRequester() }
    val confirm = { if (canSave) onConfirm(DeckDraft(path.trim(), category.trim(), description.trim())) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.core_ui_deck_new_title else R.string.core_ui_deck_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(stringResource(R.string.core_ui_deck_name)) },
                    supportingText = { Text(stringResource(R.string.core_ui_deck_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.core_ui_deck_category)) },
                    placeholder = { Text(stringResource(R.string.core_ui_deck_category_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.core_ui_deck_description)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = confirm, enabled = canSave) {
                Text(stringResource(if (isNew) R.string.core_ui_deck_create else R.string.core_ui_deck_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_ui_cancel)) }
        },
    )
    LaunchedEffect(Unit) { if (isNew) focusRequester.requestFocus() }
}

@Preview
@Composable
private fun DeckEditorDialogPreview() {
    MnemoTheme {
        DeckEditorDialog(onConfirm = {}, onDismiss = {}, initial = DeckDraft("Languages::Japanese", "Language"))
    }
}
