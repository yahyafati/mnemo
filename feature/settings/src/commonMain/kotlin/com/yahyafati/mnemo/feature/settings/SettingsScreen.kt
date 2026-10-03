package com.yahyafati.mnemo.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yahyafati.mnemo.core.data.sync.SyncStatus
import com.yahyafati.mnemo.core.data.sync.lastSyncAt
import com.yahyafati.mnemo.core.designsystem.component.MnemoIconButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoTopBar
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.platform.LocalPlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.CardFontSize
import com.yahyafati.mnemo.core.model.DarkThemeConfig
import com.yahyafati.mnemo.core.model.FsrsOptimizationOutcome
import com.yahyafati.mnemo.core.model.ReminderSettings
import com.yahyafati.mnemo.core.model.TransferState
import com.yahyafati.mnemo.core.model.UserSettings
import com.yahyafati.mnemo.core.ui.scroll.ScrollbarBox
import com.yahyafati.mnemo.feature.settings.resources.Res
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_none
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_providers
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_summary
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ai_summary_unusable
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_appearance
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_back
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_decrease
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_dynamic_color
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_dynamic_color_summary
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_dynamic_color_unavailable
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_font_large
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_font_medium
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_font_size
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_font_small
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_increase
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_learning_steps
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_new_per_day
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_relearning_steps
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_retention
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_retention_summary
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_reviews_per_day
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_scheduling
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_steps_error
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_steps_hint
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_row
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_row_off
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_row_restored
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_row_idle
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_row_never
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_row_syncing
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_row_waiting
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_row_error
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_theme
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_theme_dark
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_theme_light
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_theme_system
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_title
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Callbacks for every setting, so the stateless screen stays previewable. */
internal class SettingsCallbacks(
    val onDesiredRetention: (Double) -> Unit,
    val onNewCardsPerDay: (Int) -> Unit,
    val onReviewsPerDay: (Int) -> Unit,
    val onLearningSteps: (String) -> Boolean,
    val onRelearningSteps: (String) -> Boolean,
    val onDarkThemeConfig: (DarkThemeConfig) -> Unit,
    val onUseDynamicColor: (Boolean) -> Unit,
    val onCardFontSize: (CardFontSize) -> Unit,
    val onAutoPlayAudio: (Boolean) -> Unit = {},
    val onReminder: (ReminderSettings) -> Unit = {},
)

@Composable
internal fun SettingsScreen(
    onBackClick: () -> Unit,
    onOpenAiProviders: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dataState by viewModel.dataState.collectAsStateWithLifecycle()
    val aiSummary by viewModel.aiSummary.collectAsStateWithLifecycle()
    val optimizerState by viewModel.optimizerState.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        syncStatus = syncStatus,
        dataState = dataState,
        aiSummary = aiSummary,
        optimizerState = optimizerState,
        optimizerCallbacks = OptimizerCallbacks(
            onOptimize = viewModel::optimizeFsrs,
            onReset = viewModel::resetFsrsWeights,
            onDismiss = viewModel::dismissOptimization,
        ),
        onOpenAiProviders = onOpenAiProviders,
        onOpenSync = onOpenSync,
        onOpenLicenses = onOpenLicenses,
        dataCallbacks = DataCallbacks(
            onBackUp = viewModel::backUpTo,
            onReadBackup = viewModel::readBackup,
            onConfirmRestore = viewModel::confirmRestore,
            onDismissRestore = viewModel::dismissRestore,
            onAutoBackup = viewModel::setAutoBackup,
            onExport = viewModel::exportTo,
            onDismissTransfers = viewModel::dismissTransfers,
        ),
        callbacks = SettingsCallbacks(
            onDesiredRetention = viewModel::setDesiredRetention,
            onNewCardsPerDay = viewModel::setNewCardsPerDay,
            onReviewsPerDay = viewModel::setReviewsPerDay,
            onLearningSteps = viewModel::setLearningSteps,
            onRelearningSteps = viewModel::setRelearningSteps,
            onDarkThemeConfig = viewModel::setDarkThemeConfig,
            onUseDynamicColor = viewModel::setUseDynamicColor,
            onCardFontSize = viewModel::setCardFontSize,
            onAutoPlayAudio = viewModel::setAutoPlayAudio,
            onReminder = viewModel::setReminder,
        ),
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

// Settings is not a tab, so it owns its Scaffold and top bar; the app shell hides its own bars.
@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    syncStatus: SyncStatus,
    dataState: DataUiState,
    aiSummary: AiSummary,
    optimizerState: TransferState<FsrsOptimizationOutcome>,
    optimizerCallbacks: OptimizerCallbacks,
    onOpenAiProviders: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenLicenses: () -> Unit,
    dataCallbacks: DataCallbacks,
    callbacks: SettingsCallbacks,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MnemoTopBar(
                title = stringResource(Res.string.feature_settings_title),
                navigationIcon = {
                    MnemoIconButton(
                        icon = MnemoIcons.ArrowBack,
                        contentDescription = stringResource(Res.string.feature_settings_back),
                        onClick = onBackClick,
                    )
                },
            )
        },
    ) { padding ->
        val settings = (uiState as? SettingsUiState.Success)?.settings
        val scroll = rememberScrollState()
        ScrollbarBox(scroll, Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxSize().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
                if (settings != null) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 680.dp)
                            .padding(horizontal = MnemoTheme.spacing.screenMargin, vertical = MnemoTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.lg),
                    ) {
                        SchedulingSection(settings, callbacks) { FsrsParametersSetting(settings.fsrsWeights, optimizerState, optimizerCallbacks) }
                        StudySection(settings, callbacks.onAutoPlayAudio)
                        if (LocalPlatformCapabilities.current.reminders) ReminderSection(settings.reminder, callbacks.onReminder)
                        AppearanceSection(settings, callbacks)
                        AiSection(aiSummary, onOpenAiProviders)
                        SyncSection(syncStatus, onOpenSync)
                        DataSection(settings.backup, dataState, dataCallbacks)
                        AboutSection(onOpenLicenses)
                    }
                }
                    }
        }
    }
}

@Composable
private fun SchedulingSection(settings: UserSettings, callbacks: SettingsCallbacks, parameters: @Composable () -> Unit) {
    Section(stringResource(Res.string.feature_settings_scheduling), MnemoIcons.Tune) {
        // The slider moves freely and saves on release, not on every frame.
        var retention by remember(settings.desiredRetention) { mutableFloatStateOf(settings.desiredRetention.toFloat()) }
        val percent = "${(retention * 100).roundToInt()}%"
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.feature_settings_retention), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(percent, style = MnemoTheme.typography.metricLg, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = retention,
            onValueChange = { retention = it },
            onValueChangeFinished = { callbacks.onDesiredRetention(retention.toDouble()) },
            // Continuous; saved rounded to whole percent.
            valueRange = 0.70f..0.97f,
            modifier = Modifier.semantics { stateDescription = percent },
        )
        Hint(stringResource(Res.string.feature_settings_retention_summary))

        NumberSetting(
            label = stringResource(Res.string.feature_settings_new_per_day),
            value = settings.newCardsPerDay,
            step = 5,
            onChange = callbacks.onNewCardsPerDay,
        )
        NumberSetting(
            label = stringResource(Res.string.feature_settings_reviews_per_day),
            value = settings.reviewsPerDay,
            step = 50,
            onChange = callbacks.onReviewsPerDay,
        )
        StepsSetting(
            label = stringResource(Res.string.feature_settings_learning_steps),
            value = StepsFormat.format(settings.learningSteps),
            onCommit = callbacks.onLearningSteps,
        )
        StepsSetting(
            label = stringResource(Res.string.feature_settings_relearning_steps),
            value = StepsFormat.format(settings.relearningSteps),
            onCommit = callbacks.onRelearningSteps,
        )
        parameters()
    }
}

@Composable
private fun AppearanceSection(settings: UserSettings, callbacks: SettingsCallbacks) {
    Section(stringResource(Res.string.feature_settings_appearance), MnemoIcons.Palette) {
        Text(stringResource(Res.string.feature_settings_theme), style = MaterialTheme.typography.titleSmall)
        Choice(
            options = listOf(
                DarkThemeConfig.FollowSystem to Res.string.feature_settings_theme_system,
                DarkThemeConfig.Light to Res.string.feature_settings_theme_light,
                DarkThemeConfig.Dark to Res.string.feature_settings_theme_dark,
            ),
            selected = settings.darkThemeConfig,
            onSelect = callbacks.onDarkThemeConfig,
        )

        val capabilities = LocalPlatformCapabilities.current
        if (capabilities.dynamicColorSetting) {
            val dynamicAvailable = capabilities.dynamicColor
            SwitchRow(
                title = stringResource(Res.string.feature_settings_dynamic_color),
                summary = stringResource(
                    if (dynamicAvailable) Res.string.feature_settings_dynamic_color_summary else Res.string.feature_settings_dynamic_color_unavailable,
                ),
                checked = settings.useDynamicColor && dynamicAvailable,
                onCheckedChange = callbacks.onUseDynamicColor,
                enabled = dynamicAvailable,
            )
        }

        Text(stringResource(Res.string.feature_settings_font_size), style = MaterialTheme.typography.titleSmall)
        Choice(
            options = listOf(
                CardFontSize.Small to Res.string.feature_settings_font_small,
                CardFontSize.Medium to Res.string.feature_settings_font_medium,
                CardFontSize.Large to Res.string.feature_settings_font_large,
            ),
            selected = settings.cardFontSize,
            onSelect = callbacks.onCardFontSize,
        )
    }
}

@Composable
private fun AiSection(summary: AiSummary, onOpen: () -> Unit) {
    Section(stringResource(Res.string.feature_settings_ai), MnemoIcons.CreateSelected) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.feature_settings_ai_providers), style = MaterialTheme.typography.titleSmall)
                Hint(
                    when {
                        summary.providerCount == 0 -> stringResource(Res.string.feature_settings_ai_none)
                        summary.defaultProvider == null -> pluralStringResource(
                            Res.plurals.feature_settings_ai_summary_unusable, summary.providerCount, summary.providerCount,
                        )
                        else -> pluralStringResource(Res.plurals.feature_settings_ai_summary, summary.providerCount, summary.providerCount, summary.defaultProvider)
                    },
                )
            }
            Icon(MnemoIcons.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Settings › Sync: one row that opens the Sync screen. It says only what matters: off, on, or needing the user. */
@Composable
private fun SyncSection(status: SyncStatus, onOpen: () -> Unit) {
    val needsUser = status is SyncStatus.Restored || status is SyncStatus.Error
    Section(stringResource(Res.string.feature_settings_sync), if (needsUser) MnemoIcons.SyncProblem else MnemoIcons.Sync) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.feature_settings_sync_row), style = MaterialTheme.typography.titleSmall)
                Hint(
                    text = when (status) {
                        SyncStatus.Off -> stringResource(Res.string.feature_settings_sync_row_off)
                        is SyncStatus.Restored -> stringResource(Res.string.feature_settings_sync_row_restored)
                        is SyncStatus.Idle -> status.lastSyncAt?.let { stringResource(Res.string.feature_settings_sync_row_idle, formatted(it)) }
                            ?: stringResource(Res.string.feature_settings_sync_row_never)
                        is SyncStatus.Syncing -> stringResource(Res.string.feature_settings_sync_row_syncing)
                        is SyncStatus.WaitingForNetwork -> stringResource(Res.string.feature_settings_sync_row_waiting)
                        is SyncStatus.Error -> stringResource(Res.string.feature_settings_sync_row_error)
                    },
                    color = if (needsUser) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(MnemoIcons.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun Section(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineSmall)
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(MnemoTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
                content()
            }
        }
    }
}

@Composable
private fun NumberSetting(label: String, value: Int, step: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        val decrease = stringResource(Res.string.feature_settings_decrease, label)
        MnemoIconButton(
            onClick = { onChange((value - step).coerceAtLeast(0)) },
            tooltip = decrease,
            enabled = value > 0,
            modifier = Modifier.semantics { contentDescription = decrease },
        ) {
            Text("−", style = MnemoTheme.typography.metricLg)
        }
        Text(
            text = value.toString(),
            style = MnemoTheme.typography.metricLg,
            modifier = Modifier.widthIn(min = 40.dp),
        )
        MnemoIconButton(
            icon = MnemoIcons.Add,
            contentDescription = stringResource(Res.string.feature_settings_increase, label),
            onClick = { onChange(value + step) },
        )
    }
}

@Composable
private fun StepsSetting(label: String, value: String, onCommit: (String) -> Boolean) {
    var text by rememberSaveable(value) { mutableStateOf(value) }
    var error by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val commit = {
        if (text != value) error = !onCommit(text)
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            error = false
        },
        label = { Text(label) },
        supportingText = {
            Text(stringResource(if (error) Res.string.feature_settings_steps_error else Res.string.feature_settings_steps_hint))
        },
        isError = error,
        singleLine = true,
        textStyle = MnemoTheme.typography.metricLg,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            commit()
            focusManager.clearFocus()
        }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused) commit() },
    )
}

@Composable
private fun <T> Choice(options: List<Pair<T, StringResource>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(stringResource(label), maxLines = 1) },
            )
        }
    }
}

@Composable
internal fun Hint(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
}

@Preview(heightDp = 1000)
@Composable
private fun SettingsScreenPreview() {
    MnemoTheme {
        SettingsScreen(
            uiState = SettingsUiState.Success(UserSettings()),
            syncStatus = SyncStatus.Off,
            dataState = DataUiState(),
            aiSummary = AiSummary(2, "OpenAI"),
            optimizerState = TransferState.Idle,
            optimizerCallbacks = OptimizerCallbacks({}, {}, {}),
            onOpenAiProviders = {},
            onOpenSync = {},
            onOpenLicenses = {},
            dataCallbacks = DataCallbacks({}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}),
            callbacks = SettingsCallbacks({}, {}, {}, { true }, { true }, {}, {}, {}),
            onBackClick = {},
        )
    }
}
