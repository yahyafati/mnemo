package com.yahyafati.mnemo.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import com.yahyafati.mnemo.core.designsystem.component.MnemoIconButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoTopBar
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.ProjectLinks
import com.yahyafati.mnemo.core.ui.adaptive.readingWidth
import com.yahyafati.mnemo.core.ui.scroll.ScrollbarBox
import com.yahyafati.mnemo.feature.settings.resources.Res
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_back
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_licenses
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_licenses_intro
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_licenses_mnemo
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ok
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › About › Open-source licenses. [loadLibraries] returns the JSON the AboutLibraries
 * plugin generates from the release dependency graph, plus the bundled files it doesn't know about
 * (`app/config`): an Android raw resource on the phone, a classpath resource on the desktop.
 */
@Composable
internal fun LicensesRoute(loadLibraries: suspend () -> String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val libraries by produceLibraries(loadLibraries)
    LicensesScreen(libraries, onBack, modifier)
}

/** Not a tab: owns its Scaffold and top bar. [libraries] is null while the list loads. */
@Composable
internal fun LicensesScreen(libraries: Libs?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MnemoTopBar(
                title = stringResource(Res.string.feature_settings_licenses),
                navigationIcon = {
                    MnemoIconButton(
                        icon = MnemoIcons.ArrowBack,
                        contentDescription = stringResource(Res.string.feature_settings_back),
                        onClick = onBack,
                    )
                },
            )
        },
    ) { padding ->
        val listState = rememberLazyListState()
        ScrollbarBox(listState, Modifier.fillMaxSize().padding(padding)) {
            LibrariesContainer(
                libraries = libraries,
                lazyListState = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .readingWidth(),
                licenseDialogConfirmText = stringResource(Res.string.feature_settings_ok),
                header = { mnemoHeader() },
            )
        }
    }
}

private fun LazyListScope.mnemoHeader() {
    item(key = "mnemo") {
        Column(
            modifier = Modifier.padding(horizontal = MnemoTheme.spacing.screenMargin, vertical = MnemoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(Res.string.feature_settings_licenses_mnemo, ProjectLinks.LICENSE_NAME), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(Res.string.feature_settings_licenses_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
