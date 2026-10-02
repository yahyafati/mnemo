package com.yahyafati.mnemo.feature.create

import com.yahyafati.mnemo.feature.create.coauthor.CoAuthorViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The ViewModels of this feature. Screens get them with `koinViewModel()`. */
val createModule = module {
    single { BookHandoff() }
    viewModelOf(::NoteEditorViewModel)
    viewModelOf(::SmartExtractViewModel)
    viewModelOf(::BookImportViewModel)
    viewModelOf(::CoAuthorViewModel)
}
