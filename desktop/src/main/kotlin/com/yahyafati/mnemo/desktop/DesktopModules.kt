package com.yahyafati.mnemo.desktop

import com.yahyafati.mnemo.core.common.di.commonModule
import com.yahyafati.mnemo.core.data.di.dataLayerModules
import com.yahyafati.mnemo.core.domain.di.domainModule
import org.koin.core.module.Module

/**
 * The desktop dependency graph, below the UI: the same modules as the Android app's
 * `mnemoModules`, with the data layer's desktop bindings (plain files, the bundled SQLite, the OS
 * keychain, jobs on coroutines). The feature modules join it in D6.
 */
val desktopModules: List<Module> = listOf(commonModule) + dataLayerModules + listOf(domainModule)
