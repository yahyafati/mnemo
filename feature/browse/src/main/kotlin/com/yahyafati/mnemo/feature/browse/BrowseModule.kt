package com.yahyafati.mnemo.feature.browse

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The ViewModels of this feature. Screens get them with `koinViewModel()`. */
val browseModule = module {
    viewModelOf(::BrowseViewModel)
}
