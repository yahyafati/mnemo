package com.yahyafati.mnemo.feature.settings

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import com.yahyafati.mnemo.core.designsystem.component.MnemoTopBar
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.ProjectLinks

/**
 * Settings › About › Open-source licenses. [librariesRes] is the JSON the AboutLibraries plugin
 * generates from the release dependency graph, plus the bundled files it doesn't know about
 * (`app/config`).
 */
@Composable
internal fun LicensesRoute(@RawRes librariesRes: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val libraries by produceLibraries {
        resources.openRawResource(librariesRes).bufferedReader().use { it.readText() }
    }
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
                title = stringResource(R.string.feature_settings_licenses),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MnemoIcons.ArrowBack, contentDescription = stringResource(R.string.feature_settings_back))
                    }
                },
            )
        },
    ) { padding ->
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            licenseDialogConfirmText = stringResource(R.string.feature_settings_ok),
            header = { mnemoHeader() },
        )
    }
}

private fun LazyListScope.mnemoHeader() {
    item(key = "mnemo") {
        Column(
            modifier = Modifier.padding(horizontal = MnemoTheme.spacing.screenMargin, vertical = MnemoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.feature_settings_licenses_mnemo, ProjectLinks.LICENSE_NAME), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.feature_settings_licenses_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
