package com.yahyafati.mnemo.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yahyafati.mnemo.core.designsystem.platform.LocalPlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.DarkThemeConfig
import com.yahyafati.mnemo.core.model.ExportFormat
import com.yahyafati.mnemo.core.ui.adaptive.LocalMaxContentWidth
import com.yahyafati.mnemo.core.ui.adaptive.LocalWindowLayout
import com.yahyafati.mnemo.core.ui.adaptive.currentWindowLayout
import com.yahyafati.mnemo.core.ui.card.LocalCardFontScale
import com.yahyafati.mnemo.core.ui.card.audio.LocalAutoPlayAudio
import com.yahyafati.mnemo.core.ui.files.FilePicker
import com.yahyafati.mnemo.core.ui.files.FileSaver
import com.yahyafati.mnemo.core.ui.files.fileDropTarget
import com.yahyafati.mnemo.core.ui.files.rememberFilePicker
import com.yahyafati.mnemo.core.ui.files.rememberFileSaver
import com.yahyafati.mnemo.core.ui.keyboard.FindRequests
import com.yahyafati.mnemo.core.ui.keyboard.LocalFindRequests
import com.yahyafati.mnemo.core.ui.keyboard.ShortcutBinding
import com.yahyafati.mnemo.core.ui.keyboard.Shortcuts
import com.yahyafati.mnemo.core.ui.keyboard.does
import com.yahyafati.mnemo.core.ui.keyboard.shortcuts
import com.yahyafati.mnemo.core.ui.navigation.BrowseRoute
import com.yahyafati.mnemo.core.ui.navigation.CreateRoute
import com.yahyafati.mnemo.core.ui.navigation.NoteEditorRoute
import com.yahyafati.mnemo.feature.browse.navigation.navigateToBrowse
import com.yahyafati.mnemo.feature.create.navigation.navigateToBookImport
import com.yahyafati.mnemo.feature.create.navigation.navigateToNoteEditor
import com.yahyafati.mnemo.feature.settings.RestoreDialogs
import com.yahyafati.mnemo.feature.settings.backupFileName
import com.yahyafati.mnemo.feature.settings.navigation.navigateToLicenses
import com.yahyafati.mnemo.shell.navigation.TopLevelDestination
import com.yahyafati.mnemo.shell.onboarding.OnboardingScreen
import com.yahyafati.mnemo.shell.resources.Res
import com.yahyafati.mnemo.shell.resources.drop_to_open
import com.yahyafati.mnemo.shell.resources.message_backup_done
import com.yahyafati.mnemo.shell.resources.message_export_done
import com.yahyafati.mnemo.shell.resources.message_transfer_failed
import com.yahyafati.mnemo.shell.resources.message_unsupported_file
import org.jetbrains.compose.resources.getString
import java.time.Instant
import org.jetbrains.compose.resources.stringResource

/** Whether the app is drawn dark: what the setting says, or the system's theme when it follows it. */
@Composable
fun DarkThemeConfig.isDark(): Boolean = when (this) {
    DarkThemeConfig.FollowSystem -> isSystemInDarkTheme()
    DarkThemeConfig.Light -> false
    DarkThemeConfig.Dark -> true
}

/**
 * The whole UI, from the app settings down: the theme and the card settings, then the first-run
 * onboarding or the app shell. The launchers (`MainActivity`, the desktop window) are thin around it.
 *
 * - [providePlatform] wraps everything in what only the platform has (capabilities, card math,
 *   images and sound). The theme reads some of it, so it sits outside the theme.
 * - [loadLicenses] returns the generated open-source licenses JSON (an Android raw resource, a
 *   classpath resource on the desktop).
 * - [systemBars] is called with the resolved dark-theme flag, for launchers that draw under system
 *   bars whose icon colors must follow the app theme (Android's edge-to-edge).
 * - [commands] is how a menu bar, a dropped file or any other launcher-side control asks the app to
 *   do something ([AppCommand]); the shortcuts of the app send the same commands.
 * - [maxContentWidth] stops the body of a screen growing past it on a very wide window; unspecified
 *   (the phone's default) lets it use all the width.
 *
 * Nothing is drawn until the settings are in, a few milliseconds after start.
 */
@Composable
fun MnemoRoot(
    viewModel: MainViewModel,
    loadLicenses: suspend () -> String,
    providePlatform: @Composable (content: @Composable () -> Unit) -> Unit,
    systemBars: @Composable (darkTheme: Boolean) -> Unit = {},
    commands: AppCommands = remember { AppCommands() },
    maxContentWidth: Dp = Dp.Unspecified,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = uiState as? MainUiState.Ready ?: return
    val settings = ready.settings
    val darkTheme = settings.darkThemeConfig.isDark()
    systemBars(darkTheme)
    providePlatform {
        MnemoTheme(darkTheme = darkTheme, dynamicColor = settings.useDynamicColor) {
            CompositionLocalProvider(
                LocalCardFontScale provides settings.cardFontSize.scale,
                LocalAutoPlayAudio provides settings.autoPlayAudio,
                LocalWindowLayout provides currentWindowLayout(),
                LocalMaxContentWidth provides maxContentWidth,
            ) {
                Shell(viewModel, ready, loadLicenses, commands)
            }
        }
    }
}

@Composable
private fun Shell(
    viewModel: MainViewModel,
    ready: MainUiState.Ready,
    loadLicenses: suspend () -> String,
    commands: AppCommands,
) {
    val settings = ready.settings
    val onboarding = ready.showOnboarding
    val appState = rememberMnemoAppState()
    val findRequests = remember { FindRequests() }
    val snackbar = remember { SnackbarHostState() }
    var showShortcuts by remember { mutableStateOf(false) }
    var dropping by remember { mutableStateOf(false) }
    val restore by viewModel.restore.collectAsStateWithLifecycle()
    // File dialogs are for a computer's menu bar and keyboard: a phone has no command to send them.
    val launchers = if (LocalPlatformCapabilities.current.keyboardAndMouse) rememberFileCommandLaunchers(commands) else null

    LaunchedEffect(commands, onboarding) {
        commands.flow.collect { command ->
            if (onboarding) {
                // Before the first deck only a package to import and the shortcuts list make sense.
                when (command) {
                    is AppCommand.OpenFile -> if (FileKind.of(command.location) == FileKind.AnkiPackage) {
                        viewModel.importFromOnboarding(command.location, reminder = false)
                    }
                    AppCommand.ShowShortcuts -> showShortcuts = true
                    else -> Unit
                }
            } else {
                perform(command, appState, viewModel, findRequests, launchers) { showShortcuts = true }
            }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(
                getString(
                    when (message) {
                        ShellMessage.BackupDone -> Res.string.message_backup_done
                        ShellMessage.ExportDone -> Res.string.message_export_done
                        ShellMessage.TransferFailed -> Res.string.message_transfer_failed
                        ShellMessage.UnsupportedFile -> Res.string.message_unsupported_file
                    },
                ),
            )
        }
    }

    // The shortcuts work wherever the focus is inside the app. When nothing has it (the focused part of
    // a screen went away with the screen), the shell takes it, so the keys keep working. A phone has
    // no use for this: its focus is the text field that has the keyboard.
    val focus = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    val takesFocus = LocalPlatformCapabilities.current.keyboardAndMouse
    LaunchedEffect(hasFocus, takesFocus) {
        if (takesFocus && !hasFocus) {
            // A frame is time enough for the screen that replaced the old one to ask for the focus itself.
            withFrameNanos { }
            if (!hasFocus) runCatching { focus.requestFocus() }
        }
    }
    val bindings = if (onboarding) emptyList() else globalShortcuts(commands)
    CompositionLocalProvider(LocalFindRequests provides findRequests) {
        Box(
            Modifier
                .shortcuts(*bindings.toTypedArray())
                // A deck or a backup dragged from the file manager onto the window.
                .fileDropTarget(accepts = { FileKind.of(it) != FileKind.Unknown }, onHover = { dropping = it }) { paths ->
                    droppedFileCommands(paths).forEach(commands::send)
                }
                .onFocusChanged { hasFocus = it.hasFocus }
                .focusRequester(focus)
                .focusable(),
        ) {
            if (onboarding) {
                OnboardingScreen(
                    reminderTime = settings.reminder.time,
                    onCreateDeck = viewModel::finishOnboarding,
                    onImport = viewModel::importFromOnboarding,
                    onSkip = { reminder -> viewModel.finishOnboarding(deckName = null, reminder = reminder) },
                )
            } else {
                val destination by viewModel.destination.collectAsStateWithLifecycle()
                MnemoApp(
                    loadLicenses = loadLicenses,
                    appState = appState,
                    destination = destination,
                    onDestinationHandled = viewModel::destinationHandled,
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            if (dropping) DropOverlay(Modifier.matchParentSize())
        }
    }
    if (showShortcuts) ShortcutsDialog(onDismiss = { showShortcuts = false })
    RestoreDialogs(restore, onConfirm = viewModel::confirmRestore, onDismiss = viewModel::dismissRestore)
}

/**
 * The keys that work on every screen. Each one sends the command its menu item sends too. The
 * shell handles them while the focus is inside it; a launcher can also hand them the keys nothing
 * in the window handled (the desktop's window does), for when nothing has the focus.
 */
fun globalShortcuts(commands: AppCommands): List<ShortcutBinding> = buildList {
    Shortcuts.Tabs.zip(TopLevelDestination.entries).forEach { (shortcut, tab) ->
        add(shortcut does { commands.send(AppCommand.GoToTab(tab)) })
    }
    add(Shortcuts.NewNote does { commands.send(AppCommand.NewNote) })
    add(Shortcuts.Search does { commands.send(AppCommand.Find) })
    add(Shortcuts.Back does { commands.send(AppCommand.Back) })
    add(Shortcuts.Settings does { commands.send(AppCommand.OpenSettings) })
    add(Shortcuts.Import does { commands.send(AppCommand.ChooseFileToOpen) })
    add(Shortcuts.ShowShortcuts does { commands.send(AppCommand.ShowShortcuts) })
}

/** What the shell does for [command]. */
private fun perform(
    command: AppCommand,
    appState: MnemoAppState,
    viewModel: MainViewModel,
    findRequests: FindRequests,
    launchers: FileCommandLaunchers?,
    showShortcuts: () -> Unit,
) {
    when (command) {
        is AppCommand.GoToTab -> appState.navigateToTopLevelDestination(command.tab)
        // The Create tab writes cards itself, and the editor is one already.
        AppCommand.NewNote -> if (!appState.isShowing<NoteEditorRoute>() && !appState.isShowing<CreateRoute>()) {
            appState.navController.navigateToNoteEditor()
        }
        AppCommand.Find -> if (appState.isShowing<BrowseRoute>()) {
            findRequests.request()
        } else {
            appState.navController.navigateToBrowse(focusSearch = true)
        }
        AppCommand.Back -> appState.navigateBack()
        AppCommand.OpenSettings -> appState.navigateToSettings()
        AppCommand.OpenLicenses -> appState.navController.navigateToLicenses()
        AppCommand.ShowShortcuts -> showShortcuts()
        AppCommand.ChooseFileToOpen -> launchers?.openFile?.launch()
        AppCommand.ChooseBackupToRestore -> launchers?.restoreFile?.launch()
        AppCommand.ChooseBackupLocation -> launchers?.backupFile?.launch(backupFileName(Instant.now()))
        is AppCommand.ChooseExportLocation -> launchers?.export?.invoke(command.format)
        is AppCommand.OpenFile -> if (FileKind.of(command.location) == FileKind.Epub) {
            appState.navController.navigateToBookImport(command.location)
        } else if (viewModel.openFile(command.location)) {
            appState.navigateToTopLevelDestination(TopLevelDestination.Decks)
        }
        is AppCommand.RestoreBackup -> viewModel.stageRestore(command.location)
        is AppCommand.BackUpTo -> viewModel.backUpTo(command.location)
        is AppCommand.ExportTo -> viewModel.exportTo(command.location, command.format)
    }
}

/** The file dialogs behind the File menu's commands. */
private class FileCommandLaunchers(
    val openFile: FilePicker,
    val restoreFile: FilePicker,
    val backupFile: FileSaver,
    val export: (ExportFormat) -> Unit,
)

@Composable
private fun rememberFileCommandLaunchers(commands: AppCommands): FileCommandLaunchers {
    val open = rememberFilePicker(listOf(ZIP, EPUB)) { commands.send(AppCommand.OpenFile(it)) }
    val restoreFile = rememberFilePicker(listOf(ZIP)) { commands.send(AppCommand.RestoreBackup(it)) }
    val backup = rememberFileSaver(ZIP) { commands.send(AppCommand.BackUpTo(it)) }
    var format by remember { mutableStateOf(ExportFormat.Apkg) }
    val exportSaver = rememberFileSaver("*/*") { commands.send(AppCommand.ExportTo(it, format)) }
    return remember(open, restoreFile, backup, exportSaver) {
        FileCommandLaunchers(open, restoreFile, backup) { chosen ->
            format = chosen
            exportSaver.launch(if (chosen == ExportFormat.Apkg) "mnemo-collection.apkg" else "mnemo-collection.json")
        }
    }
}

private const val ZIP = "application/zip"
private const val EPUB = "application/epub+zip"

/** Over the whole window while a file is dragged above it: what dropping does. */
@Composable
private fun DropOverlay(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .background(colors.primaryContainer.copy(alpha = 0.9f))
            .border(3.dp, colors.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.drop_to_open),
            style = MaterialTheme.typography.titleLarge,
            color = colors.onPrimaryContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}
