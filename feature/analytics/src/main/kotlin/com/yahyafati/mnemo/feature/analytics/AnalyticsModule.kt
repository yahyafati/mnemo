package com.yahyafati.mnemo.feature.analytics

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The ViewModels of this feature. Screens get them with `koinViewModel()`. */
val analyticsModule = module {
    viewModelOf(::AnalyticsViewModel)
}
