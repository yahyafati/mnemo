package com.yahyafati.mnemo.shell.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.component.MnemoButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoButtonStyle
import com.yahyafati.mnemo.core.designsystem.component.MnemoLogo
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.platform.LocalPlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.ui.files.rememberFilePicker
import com.yahyafati.mnemo.core.ui.permission.AppPermission
import com.yahyafati.mnemo.core.ui.permission.rememberPermissionRequest
import com.yahyafati.mnemo.shell.resources.Res
import com.yahyafati.mnemo.shell.resources.onboarding_back
import com.yahyafati.mnemo.shell.resources.onboarding_create_deck
import com.yahyafati.mnemo.shell.resources.onboarding_deck_name
import com.yahyafati.mnemo.shell.resources.onboarding_deck_placeholder
import com.yahyafati.mnemo.shell.resources.onboarding_explore
import com.yahyafati.mnemo.shell.resources.onboarding_import
import com.yahyafati.mnemo.shell.resources.onboarding_next
import com.yahyafati.mnemo.shell.resources.onboarding_page
import com.yahyafati.mnemo.shell.resources.onboarding_reminder
import com.yahyafati.mnemo.shell.resources.onboarding_reminder_hint
import com.yahyafati.mnemo.shell.resources.onboarding_skip
import com.yahyafati.mnemo.shell.resources.onboarding_start_message
import com.yahyafati.mnemo.shell.resources.onboarding_start_title
import com.yahyafati.mnemo.shell.resources.onboarding_study_flip
import com.yahyafati.mnemo.shell.resources.onboarding_study_flip_keys
import com.yahyafati.mnemo.shell.resources.onboarding_study_rate
import com.yahyafati.mnemo.shell.resources.onboarding_study_rate_keys
import com.yahyafati.mnemo.shell.resources.onboarding_study_title
import com.yahyafati.mnemo.shell.resources.onboarding_study_types
import com.yahyafati.mnemo.shell.resources.onboarding_welcome_ai
import com.yahyafati.mnemo.shell.resources.onboarding_welcome_fsrs
import com.yahyafati.mnemo.shell.resources.onboarding_welcome_local
import com.yahyafati.mnemo.shell.resources.onboarding_welcome_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * First run: what Mnemo is, how studying works, then a first deck (or an Anki import) and an
 * optional daily reminder. Everything here can be skipped; nothing needs an account or network.
 */
@Composable
internal fun OnboardingScreen(
    reminderTime: LocalTime,
    onCreateDeck: (name: String, reminder: Boolean) -> Unit,
    onImport: (uri: String, reminder: Boolean) -> Unit,
    onSkip: (reminder: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState { PAGES }
    val keyboard = LocalPlatformCapabilities.current.keyboardAndMouse
    val scope = rememberCoroutineScope()
    var reminder by rememberSaveable { mutableStateOf(false) }
    val importPicker = rememberFilePicker { onImport(it, reminder) }
    val notifications = rememberPermissionRequest(AppPermission.Notifications) { granted -> reminder = granted }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (pager.currentPage < PAGES - 1) {
                    TextButton(onClick = { onSkip(false) }) { Text(stringResource(Res.string.onboarding_skip)) }
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top,
            ) { page ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MnemoTheme.spacing.screenMargin),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(Modifier.widthIn(max = 560.dp).padding(top = MnemoTheme.spacing.lg)) {
                        when (page) {
                            0 -> InfoPage(
                                header = { MnemoLogo(size = 72.dp) },
                                title = stringResource(Res.string.onboarding_welcome_title),
                                points = listOf(
                                    MnemoIcons.Bolt to stringResource(Res.string.onboarding_welcome_fsrs),
                                    MnemoIcons.Privacy to stringResource(Res.string.onboarding_welcome_local),
                                    MnemoIcons.Sparkle to stringResource(Res.string.onboarding_welcome_ai),
                                ),
                            )
                            1 -> InfoPage(
                                header = { IconBadge(MnemoIcons.StudySelected) },
                                title = stringResource(Res.string.onboarding_study_title),
                                points = listOf(
                                    if (keyboard) {
                                        MnemoIcons.Keyboard to stringResource(Res.string.onboarding_study_flip_keys)
                                    } else {
                                        MnemoIcons.TouchApp to stringResource(Res.string.onboarding_study_flip)
                                    },
                                    MnemoIcons.Schedule to stringResource(
                                        if (keyboard) Res.string.onboarding_study_rate_keys else Res.string.onboarding_study_rate,
                                    ),
                                    MnemoIcons.Quiz to stringResource(Res.string.onboarding_study_types),
                                ),
                            )
                            else -> StartPage(
                                reminder = reminder,
                                reminderTime = reminderTime,
                                remindersAvailable = LocalPlatformCapabilities.current.reminders,
                                onReminder = { on ->
                                    if (on && notifications.canRequest) {
                                        notifications.launch()
                                    } else {
                                        reminder = on
                                    }
                                },
                                onCreateDeck = { onCreateDeck(it, reminder) },
                                onImport = { importPicker.launch() },
                                onSkip = { onSkip(reminder) },
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MnemoTheme.spacing.screenMargin, vertical = MnemoTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageDots(pager.currentPage, Modifier.weight(1f))
                if (pager.currentPage > 0) {
                    TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                        Text(stringResource(Res.string.onboarding_back))
                    }
                }
                if (pager.currentPage < PAGES - 1) {
                    MnemoButton(
                        text = stringResource(Res.string.onboarding_next),
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        trailingIcon = MnemoIcons.ArrowForward,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoPage(header: @Composable () -> Unit, title: String, points: List<Pair<ImageVector, String>>) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.lg)) {
        header()
        Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
        points.forEach { (pointIcon, text) ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(pointIcon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
                Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = MnemoTheme.spacing.md))
            }
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(16.dp).size(40.dp))
    }
}

@Composable
private fun StartPage(
    reminder: Boolean,
    reminderTime: LocalTime,
    remindersAvailable: Boolean,
    onReminder: (Boolean) -> Unit,
    onCreateDeck: (String) -> Unit,
    onImport: () -> Unit,
    onSkip: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.md)) {
        Text(stringResource(Res.string.onboarding_start_title), style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.onboarding_start_message), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(Res.string.onboarding_deck_name)) },
            placeholder = { Text(stringResource(Res.string.onboarding_deck_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onCreateDeck(name) }),
            modifier = Modifier.fillMaxWidth(),
        )
        MnemoButton(
            text = stringResource(Res.string.onboarding_create_deck),
            onClick = { onCreateDeck(name) },
            enabled = name.isNotBlank(),
            leadingIcon = MnemoIcons.Add,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        )
        MnemoButton(
            text = stringResource(Res.string.onboarding_import),
            onClick = onImport,
            style = MnemoButtonStyle.Secondary,
            leadingIcon = MnemoIcons.FileUpload,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        )
        // The desktop has no reminder notification (capability flag), so it skips this row.
        if (remindersAvailable) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(value = reminder, role = Role.Switch, onValueChange = onReminder),
            ) {
                Icon(MnemoIcons.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = MnemoTheme.spacing.md),
                ) {
                    Text(
                        stringResource(Res.string.onboarding_reminder, reminderTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(stringResource(Res.string.onboarding_reminder_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = reminder, onCheckedChange = null)
            }
        }
        TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(Res.string.onboarding_explore))
        }
        Spacer(Modifier.heightIn(min = MnemoTheme.spacing.lg))
    }
}

@Composable
private fun PageDots(current: Int, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.onboarding_page, current + 1, PAGES)
    Row(modifier.semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(PAGES) { page ->
            Box(
                Modifier
                    .size(if (page == current) 10.dp else 8.dp)
                    .background(
                        if (page == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

private const val PAGES = 3

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun OnboardingScreenPreview() {
    MnemoTheme {
        OnboardingScreen(LocalTime.of(19, 0), onCreateDeck = { _, _ -> }, onImport = { _, _ -> }, onSkip = {})
    }
}
