package com.yahyafati.mnemo.feature.settings.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yahyafati.mnemo.core.designsystem.component.MnemoButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoButtonStyle
import com.yahyafati.mnemo.core.designsystem.component.MnemoChip
import com.yahyafati.mnemo.core.designsystem.component.MnemoTopBar
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiConnectionReport
import com.yahyafati.mnemo.core.model.AiEndpoint
import com.yahyafati.mnemo.core.model.AiModel
import com.yahyafati.mnemo.core.model.AiProviderPresets
import com.yahyafati.mnemo.core.ui.ai.AiDisclosureDialog
import com.yahyafati.mnemo.core.ui.ai.aiFailureText
import com.yahyafati.mnemo.feature.settings.Hint
import com.yahyafati.mnemo.feature.settings.R
import com.yahyafati.mnemo.feature.settings.Section

@Composable
internal fun ProviderEditorRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProviderEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.closeRequested) {
        if (uiState.closeRequested) onClose()
    }
    ProviderEditorScreen(uiState, viewModel::onAction, onClose, modifier)
}

@Composable
internal fun ProviderEditorScreen(
    uiState: ProviderEditorUiState,
    onAction: (ProviderEditorAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MnemoTopBar(
                title = stringResource(if (uiState.isNew) R.string.feature_settings_ai_new else R.string.feature_settings_ai_edit),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(MnemoIcons.Close, contentDescription = stringResource(R.string.feature_settings_cancel))
                    }
                },
                actions = {
                    TextButton(onClick = { onAction(ProviderEditorAction.Save) }, enabled = uiState.canSave) {
                        Text(stringResource(R.string.feature_settings_ai_save))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (uiState.loading) return@Box
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .padding(horizontal = MnemoTheme.spacing.screenMargin, vertical = MnemoTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.lg),
            ) {
                if (uiState.isNew) PresetPicker(uiState.presetId, onAction)
                ConnectionSection(uiState, onAction)
                ModelSection(uiState, onAction)
                TestSection(uiState, onAction)
                AdvancedSection(uiState, onAction)
                if (!uiState.isNew) {
                    MnemoButton(
                        text = stringResource(R.string.feature_settings_ai_delete),
                        onClick = { onAction(ProviderEditorAction.Delete) },
                        style = MnemoButtonStyle.Text,
                        leadingIcon = MnemoIcons.Delete,
                    )
                }
            }
        }
    }

    if (uiState.showDisclosure) {
        AiDisclosureDialog(
            providerName = uiState.name.ifBlank { uiState.host },
            host = uiState.host,
            whatIsSent = stringResource(R.string.feature_settings_ai_test_what),
            onAccept = { onAction(ProviderEditorAction.AcceptDisclosure) },
            onDismiss = { onAction(ProviderEditorAction.DismissDisclosure) },
        )
    }
    if (uiState.confirmDelete) {
        AlertDialog(
            onDismissRequest = { onAction(ProviderEditorAction.DismissDelete) },
            title = { Text(stringResource(R.string.feature_settings_ai_delete_title, uiState.name)) },
            text = { Text(stringResource(R.string.feature_settings_ai_delete_message)) },
            confirmButton = {
                TextButton(onClick = { onAction(ProviderEditorAction.ConfirmDelete) }) { Text(stringResource(R.string.feature_settings_ai_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { onAction(ProviderEditorAction.DismissDelete) }) { Text(stringResource(R.string.feature_settings_cancel)) }
            },
        )
    }
    if (uiState.saveFailed) {
        AlertDialog(
            onDismissRequest = { onAction(ProviderEditorAction.DismissSaveError) },
            text = { Text(stringResource(R.string.feature_settings_ai_save_failed)) },
            confirmButton = {
                TextButton(onClick = { onAction(ProviderEditorAction.DismissSaveError) }) { Text(stringResource(R.string.feature_settings_ok)) }
            },
        )
    }
}

@Composable
private fun PresetPicker(selected: String?, onAction: (ProviderEditorAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
        Text(stringResource(R.string.feature_settings_ai_start_from), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
            AiProviderPresets.all.forEach { preset ->
                MnemoChip(label = preset.name, selected = preset.id == selected, onClick = { onAction(ProviderEditorAction.ChoosePreset(preset.id)) })
            }
        }
    }
}

@Composable
private fun ConnectionSection(uiState: ProviderEditorUiState, onAction: (ProviderEditorAction) -> Unit) {
    Section(stringResource(R.string.feature_settings_ai_connection), MnemoIcons.Cloud) {
        OutlinedTextField(
            value = uiState.name,
            onValueChange = { onAction(ProviderEditorAction.Name(it)) },
            label = { Text(stringResource(R.string.feature_settings_ai_name)) },
            placeholder = { Text(uiState.host) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val urlProblem = when (val check = uiState.urlCheck) {
            AiEndpoint.Check.Ok -> if (uiState.pointsAtThisDevice) R.string.feature_settings_ai_url_this_device else null
            AiEndpoint.Check.Invalid -> R.string.feature_settings_ai_url_invalid.takeIf { uiState.baseUrl.isNotBlank() }
            is AiEndpoint.Check.Insecure -> if (check.hostIsLocal) R.string.feature_settings_ai_url_insecure_local else R.string.feature_settings_ai_url_insecure
        }
        OutlinedTextField(
            value = uiState.baseUrl,
            onValueChange = { onAction(ProviderEditorAction.BaseUrl(it)) },
            label = { Text(stringResource(R.string.feature_settings_ai_base_url)) },
            supportingText = { Text(stringResource(urlProblem ?: R.string.feature_settings_ai_base_url_hint)) },
            isError = uiState.urlCheck != AiEndpoint.Check.Ok && uiState.baseUrl.isNotBlank(),
            singleLine = true,
            textStyle = MnemoTheme.typography.metricSm.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        SwitchRow(
            title = stringResource(R.string.feature_settings_ai_local_server),
            hint = stringResource(R.string.feature_settings_ai_local_server_hint),
            checked = uiState.isLocal,
            onChange = { onAction(ProviderEditorAction.Local(it)) },
        )
        ApiKeyField(uiState, onAction)
    }
}

@Composable
private fun ApiKeyField(uiState: ProviderEditorUiState, onAction: (ProviderEditorAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    when {
        uiState.removeKey -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MnemoIcons.Key, null, tint = colors.error)
            Text(
                stringResource(R.string.feature_settings_ai_key_will_remove),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = MnemoTheme.spacing.sm),
            )
            TextButton(onClick = { onAction(ProviderEditorAction.KeepKey) }) { Text(stringResource(R.string.feature_settings_ai_key_keep)) }
        }

        uiState.hasStoredKey && !uiState.replacingKey -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MnemoIcons.Key, null, tint = colors.primary)
            Text(
                stringResource(R.string.feature_settings_ai_key_saved),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = MnemoTheme.spacing.sm),
            )
            TextButton(onClick = { onAction(ProviderEditorAction.ReplaceKey) }) { Text(stringResource(R.string.feature_settings_ai_key_replace)) }
            TextButton(onClick = { onAction(ProviderEditorAction.RemoveKey) }) { Text(stringResource(R.string.feature_settings_ai_key_remove)) }
        }

        else -> {
            var visible by remember { mutableStateOf(false) }
            val required = uiState.preset?.requiresApiKey == true
            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = { onAction(ProviderEditorAction.ApiKey(it)) },
                label = { Text(stringResource(if (required) R.string.feature_settings_ai_key else R.string.feature_settings_ai_key_optional)) },
                supportingText = { Text(stringResource(R.string.feature_settings_ai_key_hint)) },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) MnemoIcons.Hide else MnemoIcons.Show,
                            stringResource(if (visible) R.string.feature_settings_ai_key_hide else R.string.feature_settings_ai_key_show),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (uiState.replacingKey) {
                TextButton(onClick = { onAction(ProviderEditorAction.KeepKey) }) { Text(stringResource(R.string.feature_settings_ai_key_keep)) }
            }
        }
    }
    if (uiState.missingKey) {
        Text(
            stringResource(R.string.feature_settings_ai_key_missing, uiState.preset?.name.orEmpty()),
            style = MaterialTheme.typography.labelMedium,
            color = colors.error,
        )
    }
    uiState.preset?.keyUrl?.let { url ->
        val uriHandler = LocalUriHandler.current
        MnemoButton(
            text = stringResource(R.string.feature_settings_ai_key_get),
            onClick = { uriHandler.openUri(url) },
            style = MnemoButtonStyle.Text,
            trailingIcon = MnemoIcons.OpenInNew,
        )
    }
}

@Composable
private fun ModelSection(uiState: ProviderEditorUiState, onAction: (ProviderEditorAction) -> Unit) {
    Section(stringResource(R.string.feature_settings_ai_model), MnemoIcons.Tune) {
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedTextField(
                value = uiState.defaultModel,
                onValueChange = { onAction(ProviderEditorAction.DefaultModel(it)) },
                label = { Text(stringResource(R.string.feature_settings_ai_model)) },
                supportingText = {
                    val count = uiState.modelChoices.size
                    Text(
                        if (count > 0) pluralStringResource(R.plurals.feature_settings_ai_test_models, count, count)
                        else stringResource(R.string.feature_settings_ai_model_hint),
                    )
                },
                singleLine = true,
                textStyle = MnemoTheme.typography.metricSm.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                trailingIcon = if (uiState.modelChoices.isEmpty()) null else {
                    {
                        IconButton(onClick = { expanded = true }) {
                            Icon(MnemoIcons.ExpandMore, stringResource(R.string.feature_settings_ai_models_choose))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                uiState.modelChoices.forEach { id ->
                    DropdownMenuItem(
                        text = { Text(id, style = MnemoTheme.typography.metricSm) },
                        onClick = {
                            expanded = false
                            onAction(ProviderEditorAction.DefaultModel(id))
                        },
                    )
                }
            }
        }

        if (uiState.defaultModel.isNotBlank()) {
            Text(stringResource(R.string.feature_settings_ai_capabilities), style = MaterialTheme.typography.titleSmall)
            val capabilities = uiState.capabilities
            Hint(
                stringResource(
                    when {
                        uiState.capabilitiesSetByUser -> R.string.feature_settings_ai_capabilities_user
                        capabilities != null -> R.string.feature_settings_ai_capabilities_detected
                        else -> R.string.feature_settings_ai_capabilities_unknown
                    },
                ),
            )
            val current = capabilities ?: AiCapabilities()
            val set = { value: AiCapabilities -> onAction(ProviderEditorAction.Capabilities(value)) }
            CheckRow(stringResource(R.string.feature_settings_ai_cap_json), current.jsonOutput) { set(current.copy(jsonOutput = it)) }
            CheckRow(stringResource(R.string.feature_settings_ai_cap_vision), current.vision) { set(current.copy(vision = it)) }
            CheckRow(stringResource(R.string.feature_settings_ai_cap_streaming), current.streaming) { set(current.copy(streaming = it)) }
        }
    }
}

@Composable
private fun TestSection(uiState: ProviderEditorUiState, onAction: (ProviderEditorAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
        MnemoButton(
            text = stringResource(if (uiState.testing) R.string.feature_settings_ai_testing else R.string.feature_settings_ai_test),
            onClick = { onAction(ProviderEditorAction.Test) },
            enabled = uiState.canTest,
            leadingIcon = MnemoIcons.Bolt,
            modifier = Modifier.fillMaxWidth(),
        )
        if (uiState.testing) LinearProgressIndicator(Modifier.fillMaxWidth())
        uiState.report?.takeUnless { uiState.testing }?.let { TestResult(it) }
    }
}

@Composable
private fun TestResult(report: AiConnectionReport) {
    val colors = MaterialTheme.colorScheme
    val ok = report.ok || (report.testedModel == null && report.modelsFailure == null)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (ok) colors.secondaryContainer else colors.errorContainer,
        contentColor = if (ok) colors.onSecondaryContainer else colors.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(MnemoTheme.spacing.md), horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
            Icon(if (ok) MnemoIcons.CheckCircle else MnemoIcons.Error, null, Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs)) {
                val failure = report.completionFailure
                val tested = report.testedModel
                val completion = when {
                    failure != null -> stringResource(R.string.feature_settings_ai_test_failure, aiFailureText(failure))
                    tested != null -> stringResource(R.string.feature_settings_ai_test_success, tested)
                    report.modelsFailure == null -> stringResource(R.string.feature_settings_ai_test_pick_model)
                    else -> null
                }
                completion?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                val models = report.modelsFailure?.let { stringResource(R.string.feature_settings_ai_test_models_failed, aiFailureText(it)) }
                    ?: pluralStringResource(R.plurals.feature_settings_ai_test_models, report.models.size, report.models.size)
                Text(models, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AdvancedSection(uiState: ProviderEditorUiState, onAction: (ProviderEditorAction) -> Unit) {
    Section(stringResource(R.string.feature_settings_ai_advanced), MnemoIcons.Settings) {
        Text(stringResource(R.string.feature_settings_ai_headers), style = MaterialTheme.typography.titleSmall)
        Hint(stringResource(R.string.feature_settings_ai_headers_hint))
        uiState.headers.forEachIndexed { index, header ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
                OutlinedTextField(
                    value = header.name,
                    onValueChange = { onAction(ProviderEditorAction.HeaderName(index, it)) },
                    label = { Text(stringResource(R.string.feature_settings_ai_header_name)) },
                    isError = !header.isValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = header.value,
                    onValueChange = { onAction(ProviderEditorAction.HeaderValue(index, it)) },
                    label = { Text(stringResource(R.string.feature_settings_ai_header_value)) },
                    isError = !header.isValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onAction(ProviderEditorAction.RemoveHeader(index)) }) {
                    Icon(MnemoIcons.Close, stringResource(R.string.feature_settings_ai_header_remove))
                }
            }
        }
        if (!uiState.headersValid) {
            Text(stringResource(R.string.feature_settings_ai_header_invalid), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
        MnemoButton(
            text = stringResource(R.string.feature_settings_ai_header_add),
            onClick = { onAction(ProviderEditorAction.AddHeader) },
            style = MnemoButtonStyle.Text,
            leadingIcon = MnemoIcons.Add,
        )
        OutlinedTextField(
            value = uiState.timeoutSeconds,
            onValueChange = { onAction(ProviderEditorAction.Timeout(it)) },
            label = { Text(stringResource(R.string.feature_settings_ai_timeout)) },
            isError = uiState.timeout == null,
            supportingText = if (uiState.timeout == null) {
                { Text(stringResource(R.string.feature_settings_ai_timeout_invalid)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        SwitchRow(
            title = stringResource(R.string.feature_settings_ai_enabled),
            hint = null,
            checked = uiState.enabled,
            onChange = { onAction(ProviderEditorAction.Enabled(it)) },
        )
    }
}

@Composable
private fun SwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            hint?.let { Hint(it) }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = MnemoTheme.spacing.sm))
    }
}

@Preview(heightDp = 1500)
@Composable
private fun ProviderEditorScreenPreview() {
    MnemoTheme {
        ProviderEditorScreen(
            uiState = ProviderEditorUiState(
                loading = false,
                presetId = "ollama",
                name = "Home Ollama",
                baseUrl = "http://192.168.1.20:11434/v1",
                isLocal = true,
                defaultModel = "llama3.2",
                report = AiConnectionReport(
                    models = listOf(AiModel("p", "llama3.2"), AiModel("p", "qwen2.5-vl")),
                    modelsFailure = null,
                    testedModel = "llama3.2",
                    completionFailure = null,
                    capabilities = AiCapabilities(jsonOutput = true, vision = false, streaming = true),
                ),
            ),
            onAction = {},
            onClose = {},
        )
    }
}
