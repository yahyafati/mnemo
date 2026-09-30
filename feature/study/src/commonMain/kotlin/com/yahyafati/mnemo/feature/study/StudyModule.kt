package com.yahyafati.mnemo.feature.study

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The ViewModels of this feature. Screens get them with `koinViewModel()`. */
val studyModule = module {
    viewModelOf(::StudyViewModel)
    viewModelOf(::StudyAssistViewModel)
}
