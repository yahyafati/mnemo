package com.yahyafati.mnemo.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.yahyafati.mnemo.core.ui.navigation.AiProviderEditorRoute
import com.yahyafati.mnemo.core.ui.navigation.AiProvidersRoute
import com.yahyafati.mnemo.core.ui.navigation.LicensesRoute
import com.yahyafati.mnemo.core.ui.navigation.SettingsRoute
import com.yahyafati.mnemo.core.ui.navigation.SyncRoute
import com.yahyafati.mnemo.feature.settings.LicensesRoute as LicensesScreenRoute
import com.yahyafati.mnemo.feature.settings.SettingsScreen
import com.yahyafati.mnemo.feature.settings.ai.AiProvidersRoute as AiProvidersScreenRoute
import com.yahyafati.mnemo.feature.settings.ai.ProviderEditorRoute
import com.yahyafati.mnemo.feature.settings.sync.SyncRoute as SyncScreenRoute

fun NavController.navigateToSettings(navOptions: NavOptions? = null) = navigate(SettingsRoute, navOptions)

/** Settings › AI providers. Also the target of every AI setup prompt. */
fun NavController.navigateToAiProviders(navOptions: NavOptions? = null) = navigate(AiProvidersRoute, navOptions)

/** Edits [providerId], or adds a provider (from [presetId], if given). */
fun NavController.navigateToAiProviderEditor(providerId: String? = null, presetId: String? = null, navOptions: NavOptions? = null) =
    navigate(AiProviderEditorRoute(providerId = providerId, presetId = presetId), navOptions)

/** Settings › About › Open-source licenses. */
fun NavController.navigateToLicenses(navOptions: NavOptions? = null) = navigate(LicensesRoute, navOptions)

/** Settings › Sync. Also the target of the Decks screen's sync banner and of "Sync now" while sync is off. */
fun NavController.navigateToSync(navOptions: NavOptions? = null) = navigate(SyncRoute, navOptions)

fun NavGraphBuilder.settingsScreen(
    onBackClick: () -> Unit,
    onOpenAiProviders: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(onBackClick = onBackClick, onOpenAiProviders = onOpenAiProviders, onOpenSync = onOpenSync, onOpenLicenses = onOpenLicenses)
    }
}

fun NavGraphBuilder.syncScreen(onBack: () -> Unit) {
    composable<SyncRoute> {
        SyncScreenRoute(onBack = onBack)
    }
}

/**
 * The open-source licenses list. [loadLibraries] reads the JSON the AboutLibraries plugin
 * generates, which lives in the launcher (`:app` or `:desktop`), not in a feature module.
 */
fun NavGraphBuilder.licensesScreen(loadLibraries: suspend () -> String, onBack: () -> Unit) {
    composable<LicensesRoute> {
        LicensesScreenRoute(loadLibraries = loadLibraries, onBack = onBack)
    }
}

fun NavGraphBuilder.aiProvidersScreen(onBack: () -> Unit, onAddProvider: () -> Unit, onEditProvider: (String) -> Unit) {
    composable<AiProvidersRoute> {
        AiProvidersScreenRoute(onBack = onBack, onAddProvider = onAddProvider, onEditProvider = onEditProvider)
    }
}

fun NavGraphBuilder.aiProviderEditorScreen(onClose: () -> Unit) {
    composable<AiProviderEditorRoute> {
        ProviderEditorRoute(onClose = onClose)
    }
}
