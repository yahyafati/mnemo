package com.yahyafati.mnemo.feature.settings.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yahyafati.mnemo.core.designsystem.component.EmptyState
import com.yahyafati.mnemo.core.designsystem.component.MnemoButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoButtonStyle
import com.yahyafati.mnemo.core.designsystem.component.MnemoTopBar
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.AiConnectionStatus
import com.yahyafati.mnemo.core.model.AiEndpoint
import com.yahyafati.mnemo.core.model.AiModel
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.AiTaskRoute
import com.yahyafati.mnemo.core.model.AiUsageTotal
import com.yahyafati.mnemo.feature.settings.Hint
import com.yahyafati.mnemo.feature.settings.Section
import com.yahyafati.mnemo.feature.settings.resources.Res
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_add
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_default
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_disabled
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_edit
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_empty_message
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_empty_title
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_enable
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_intro
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_local
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_move_down
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_move_up
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_no_key
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_no_model
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_order_hint
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_providers
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_route_default
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_route_default_now
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_route_model
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_route_model_default
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_route_none
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_route_provider
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_route_unavailable
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_routing
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_test_failed
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_test_ok
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_usage
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_usage_empty
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_usage_hint
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_usage_removed
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_usage_requests
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_usage_tests
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_usage_tokens
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_back
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import java.text.NumberFormat
import java.time.Instant

@Composable
internal fun AiProvidersRoute(
    onBack: () -> Unit,
    onAddProvider: () -> Unit,
    onEditProvider: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiProvidersViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AiProvidersScreen(uiState, viewModel::onAction, onBack, onAddProvider, onEditProvider, modifier)
}

/** Settings › AI providers. Not a tab: owns its Scaffold and top bar. */
@Composable
internal fun AiProvidersScreen(
    uiState: AiProvidersUiState,
    onAction: (AiProvidersAction) -> Unit,
    onBack: () -> Unit,
    onAddProvider: () -> Unit,
    onEditProvider: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MnemoTopBar(
                title = stringResource(Res.string.feature_settings_ai_providers),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MnemoIcons.ArrowBack, contentDescription = stringResource(Res.string.feature_settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                Row(horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
                    Icon(MnemoIcons.Privacy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Hint(stringResource(Res.string.feature_settings_ai_intro))
                }
                if (uiState.providers.isEmpty()) {
                    EmptyState(
                        icon = MnemoIcons.Cloud,
                        title = stringResource(Res.string.feature_settings_ai_empty_title),
                        message = stringResource(Res.string.feature_settings_ai_empty_message),
                        action = { MnemoButton(stringResource(Res.string.feature_settings_ai_add), onAddProvider, leadingIcon = MnemoIcons.Add) },
                    )
                } else {
                    ProvidersSection(uiState, onAction, onAddProvider, onEditProvider)
                    RoutingSection(uiState, onAction)
                }
                UsageSection(uiState.usage)
            }
        }
    }
}

@Composable
private fun ProvidersSection(
    uiState: AiProvidersUiState,
    onAction: (AiProvidersAction) -> Unit,
    onAddProvider: () -> Unit,
    onEditProvider: (String) -> Unit,
) {
    Section(stringResource(Res.string.feature_settings_ai_providers), MnemoIcons.Cloud) {
        uiState.providers.forEachIndexed { index, provider ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ProviderRow(
                provider = provider,
                isDefault = provider.id == uiState.defaultProviderId,
                canMoveUp = index > 0,
                canMoveDown = index < uiState.providers.lastIndex,
                onEdit = { onEditProvider(provider.id) },
                onAction = onAction,
            )
        }
        Hint(stringResource(Res.string.feature_settings_ai_order_hint))
        MnemoButton(
            text = stringResource(Res.string.feature_settings_ai_add),
            onClick = onAddProvider,
            style = MnemoButtonStyle.Text,
            leadingIcon = MnemoIcons.Add,
        )
    }
}

@Composable
private fun ProviderRow(
    provider: AiProvider,
    isDefault: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onAction: (AiProvidersAction) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClickLabel = stringResource(Res.string.feature_settings_ai_edit), onClick = onEdit)
                .padding(vertical = MnemoTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs)) {
                Icon(if (provider.isLocal) MnemoIcons.Local else MnemoIcons.Cloud, null, Modifier.padding(end = 2.dp), tint = colors.onSurfaceVariant)
                Text(provider.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = listOfNotNull(AiEndpoint.host(provider.baseUrl), provider.defaultModel).joinToString(" · "),
                style = MnemoTheme.typography.metricSm,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (isDefault) StatusLabel(stringResource(Res.string.feature_settings_ai_default), colors.primaryContainer, colors.onPrimaryContainer)
                if (!provider.enabled) StatusLabel(stringResource(Res.string.feature_settings_ai_disabled), colors.surfaceContainerHighest, colors.onSurfaceVariant)
                if (provider.defaultModel.isNullOrBlank()) StatusLabel(stringResource(Res.string.feature_settings_ai_no_model), colors.errorContainer, colors.onErrorContainer)
                if (provider.isLocal) StatusLabel(stringResource(Res.string.feature_settings_ai_local), colors.secondaryContainer, colors.onSecondaryContainer)
                if (!provider.hasApiKey && !provider.isLocal) StatusLabel(stringResource(Res.string.feature_settings_ai_no_key), colors.tertiaryContainer, colors.onTertiaryContainer)
                provider.lastTest?.let { test ->
                    if (test.ok) {
                        StatusLabel(stringResource(Res.string.feature_settings_ai_test_ok), colors.secondaryContainer, colors.onSecondaryContainer)
                    } else {
                        StatusLabel(stringResource(Res.string.feature_settings_ai_test_failed), colors.errorContainer, colors.onErrorContainer)
                    }
                }
            }
        }
        IconButton(onClick = { onAction(AiProvidersAction.Move(provider.id, -1)) }, enabled = canMoveUp) {
            Icon(MnemoIcons.MoveUp, stringResource(Res.string.feature_settings_ai_move_up, provider.name))
        }
        IconButton(onClick = { onAction(AiProvidersAction.Move(provider.id, 1)) }, enabled = canMoveDown) {
            Icon(MnemoIcons.MoveDown, stringResource(Res.string.feature_settings_ai_move_down, provider.name))
        }
        val toggle = stringResource(Res.string.feature_settings_ai_enable, provider.name)
        Switch(
            checked = provider.enabled,
            onCheckedChange = { onAction(AiProvidersAction.SetEnabled(provider.id, it)) },
            modifier = Modifier.semantics { contentDescription = toggle },
        )
    }
}

@Composable
private fun RoutingSection(uiState: AiProvidersUiState, onAction: (AiProvidersAction) -> Unit) {
    Section(stringResource(Res.string.feature_settings_ai_routing), MnemoIcons.Route) {
        AiTask.entries.forEachIndexed { index, task ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TaskRouteRow(
                task = task,
                route = uiState.routes[task],
                effective = uiState.effective[task],
                providers = uiState.providers,
                models = uiState.models,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun TaskRouteRow(
    task: AiTask,
    route: AiTaskRoute?,
    effective: AiRoute?,
    providers: List<AiProvider>,
    models: Map<String, List<AiModel>>,
    onAction: (AiProvidersAction) -> Unit,
) {
    val label = taskLabel(task)
    val defaultNow = effective?.takeIf { it.usesDefault }?.let { "${it.provider.name} · ${it.modelId}" }
    val defaultLabel = defaultNow?.let { stringResource(Res.string.feature_settings_ai_route_default_now, it) }
        ?: stringResource(Res.string.feature_settings_ai_route_none)
    val chosen = route?.let { r -> providers.firstOrNull { it.id == r.providerId } }
    val off = stringResource(Res.string.feature_settings_ai_disabled)

    Column(Modifier.padding(vertical = MnemoTheme.spacing.xs), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Hint(taskHint(task))
        DropdownField(
            label = stringResource(Res.string.feature_settings_ai_route_provider, label),
            value = chosen?.name ?: defaultLabel,
            options = listOf(DropdownOption(stringResource(Res.string.feature_settings_ai_route_default)) { onAction(AiProvidersAction.SetRoute(task, null)) }) +
                providers.map { p ->
                    DropdownOption(if (p.enabled) p.name else "${p.name} ($off)") { onAction(AiProvidersAction.SetRoute(task, p.id)) }
                },
        )
        if (chosen != null) {
            val providerDefault = stringResource(Res.string.feature_settings_ai_route_model_default) + (chosen.defaultModel?.let { " ($it)" } ?: "")
            DropdownField(
                label = stringResource(Res.string.feature_settings_ai_route_model, label),
                value = route.modelId ?: providerDefault,
                options = listOf(DropdownOption(providerDefault) { onAction(AiProvidersAction.SetRoute(task, chosen.id, null)) }) +
                    models[chosen.id].orEmpty().map { m -> DropdownOption(m.id) { onAction(AiProvidersAction.SetRoute(task, chosen.id, m.id)) } },
            )
            if (effective?.usesDefault != false) Hint(stringResource(Res.string.feature_settings_ai_route_unavailable))
        }
    }
}

@Composable
private fun UsageSection(usage: List<AiUsageTotal>) {
    Section(stringResource(Res.string.feature_settings_ai_usage), MnemoIcons.Usage) {
        Hint(stringResource(Res.string.feature_settings_ai_usage_hint))
        if (usage.isEmpty()) {
            Text(stringResource(Res.string.feature_settings_ai_usage_empty), style = MaterialTheme.typography.bodyMedium)
        }
        val numbers = remember { NumberFormat.getIntegerInstance() }
        usage.forEach { total ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = total.providerName.ifBlank { stringResource(Res.string.feature_settings_ai_usage_removed) },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Hint(total.task?.let { taskLabel(it) } ?: stringResource(Res.string.feature_settings_ai_usage_tests))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(Res.string.feature_settings_ai_usage_tokens, numbers.format(total.promptTokens), numbers.format(total.completionTokens)),
                        style = MnemoTheme.typography.metricSm,
                    )
                    Text(
                        text = pluralStringResource(Res.plurals.feature_settings_ai_usage_requests, total.requests, total.requests),
                        style = MnemoTheme.typography.metricSm,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview(heightDp = 1200)
@Composable
private fun AiProvidersScreenPreview() {
    val openAi = AiProvider(
        id = "a", name = "OpenAI", baseUrl = "https://api.openai.com/v1", hasApiKey = true, defaultModel = "gpt-4.1-mini",
        lastTest = AiConnectionStatus(true, Instant.EPOCH), createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )
    val ollama = AiProvider(
        id = "b", name = "Home Ollama", baseUrl = "http://192.168.1.20:11434/v1", defaultModel = "llama3.2", isLocal = true,
        sortOrder = 1, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )
    MnemoTheme {
        AiProvidersScreen(
            uiState = AiProvidersUiState(
                loading = false,
                providers = listOf(openAi, ollama),
                routes = mapOf(AiTask.Explain to AiTaskRoute(AiTask.Explain, "b")),
                effective = AiTask.entries.associateWith { task ->
                    if (task == AiTask.Explain) AiRoute(task, ollama, "llama3.2", com.yahyafati.mnemo.core.model.AiCapabilities(), usesDefault = false)
                    else AiRoute(task, openAi, "gpt-4.1-mini", com.yahyafati.mnemo.core.model.AiCapabilities(), usesDefault = true)
                },
                usage = listOf(AiUsageTotal("a", "OpenAI", null, 3, 36, 3)),
            ),
            onAction = {},
            onBack = {},
            onAddProvider = {},
            onEditProvider = {},
        )
    }
}
