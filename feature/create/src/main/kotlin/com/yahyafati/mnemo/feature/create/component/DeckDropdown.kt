package com.yahyafati.mnemo.feature.create.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.feature.create.DeckOption
import com.yahyafati.mnemo.feature.create.R

/** The destination deck, with "New deck…" at the end of the list. */
@Composable
internal fun DeckDropdown(
    decks: List<DeckOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onNewDeck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = decks.firstOrNull { it.id == selectedId }
    OptionDropdown(
        value = selected?.path ?: stringResource(R.string.feature_create_no_decks),
        label = stringResource(R.string.feature_create_deck),
        leadingIcon = MnemoIcons.Decks,
        options = decks.map { deck -> deck.path to { onSelect(deck.id) } },
        extra = stringResource(R.string.feature_create_new_deck) to onNewDeck,
        modifier = modifier,
    )
}

/** A read-only field that opens a menu of [options]; [extra] is an accented last entry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OptionDropdown(
    value: String,
    label: String,
    leadingIcon: ImageVector,
    options: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
    extra: Pair<String, () -> Unit>? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = { Icon(leadingIcon, null, tint = MaterialTheme.colorScheme.secondary) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            colors = editorFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (text, onClick) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onClick()
                    },
                )
            }
            if (extra != null) {
                DropdownMenuItem(
                    text = { Text(extra.first, color = MaterialTheme.colorScheme.primary) },
                    leadingIcon = { Icon(MnemoIcons.Add, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = {
                        expanded = false
                        extra.second()
                    },
                )
            }
        }
    }
}

/** Text fields on the editor cards: white on the tinted card, as in the mockup. */
@Composable
internal fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
)
