package com.yahyafati.mnemo.feature.decks

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The ViewModels of this feature. Screens get them with `koinViewModel()`. */
val decksModule = module {
    viewModelOf(::DecksViewModel)
}
