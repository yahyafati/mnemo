package com.yahyafati.mnemo.feature.settings

import com.yahyafati.mnemo.feature.settings.ai.AiProvidersViewModel
import com.yahyafati.mnemo.feature.settings.ai.ProviderEditorViewModel
import com.yahyafati.mnemo.feature.settings.sync.SyncViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The ViewModels of this feature. Screens get them with `koinViewModel()`. */
val settingsModule = module {
    viewModelOf(::SettingsViewModel)
    viewModelOf(::AiProvidersViewModel)
    viewModelOf(::ProviderEditorViewModel)
    viewModelOf(::SyncViewModel)
}
