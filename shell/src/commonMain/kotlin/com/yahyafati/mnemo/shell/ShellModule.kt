package com.yahyafati.mnemo.shell

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The shell's own binding: the app-wide ViewModel (appearance, onboarding, where to open). */
val shellModule = module {
    viewModelOf(::MainViewModel)
}
