package com.yahyafati.mnemo.feature.settings.navigation

import androidx.annotation.RawRes
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.yahyafati.mnemo.core.ui.navigation.AiProviderEditorRoute
import com.yahyafati.mnemo.core.ui.navigation.AiProvidersRoute
import com.yahyafati.mnemo.core.ui.navigation.LicensesRoute
import com.yahyafati.mnemo.core.ui.navigation.SettingsRoute
import com.yahyafati.mnemo.feature.settings.LicensesRoute as LicensesScreenRoute
import com.yahyafati.mnemo.feature.settings.SettingsScreen
import com.yahyafati.mnemo.feature.settings.ai.AiProvidersRoute as AiProvidersScreenRoute
import com.yahyafati.mnemo.feature.settings.ai.ProviderEditorRoute

fun NavController.navigateToSettings(navOptions: NavOptions? = null) = navigate(SettingsRoute, navOptions)

/** Settings › AI providers. Also the target of every AI setup prompt. */
fun NavController.navigateToAiProviders(navOptions: NavOptions? = null) = navigate(AiProvidersRoute, navOptions)

/** Edits [providerId], or adds a provider (from [presetId], if given). */
fun NavController.navigateToAiProviderEditor(providerId: String? = null, presetId: String? = null, navOptions: NavOptions? = null) =
    navigate(AiProviderEditorRoute(providerId = providerId, presetId = presetId), navOptions)

/** Settings › About › Open-source licenses. */
fun NavController.navigateToLicenses(navOptions: NavOptions? = null) = navigate(LicensesRoute, navOptions)

fun NavGraphBuilder.settingsScreen(onBackClick: () -> Unit, onOpenAiProviders: () -> Unit, onOpenLicenses: () -> Unit) {
    composable<SettingsRoute> {
        SettingsScreen(onBackClick = onBackClick, onOpenAiProviders = onOpenAiProviders, onOpenLicenses = onOpenLicenses)
    }
}

/**
 * The open-source licenses list. [librariesRes] is the raw resource the AboutLibraries plugin
 * generates in `:app`, which a feature module can't reference itself.
 */
fun NavGraphBuilder.licensesScreen(@RawRes librariesRes: Int, onBack: () -> Unit) {
    composable<LicensesRoute> {
        LicensesScreenRoute(librariesRes = librariesRes, onBack = onBack)
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
