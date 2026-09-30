package com.yahyafati.mnemo.desktop

import com.yahyafati.mnemo.core.common.di.commonModule
import com.yahyafati.mnemo.core.data.di.dataLayerModules
import com.yahyafati.mnemo.core.domain.di.domainModule
import com.yahyafati.mnemo.feature.analytics.analyticsModule
import com.yahyafati.mnemo.feature.browse.browseModule
import com.yahyafati.mnemo.feature.create.createModule
import com.yahyafati.mnemo.feature.decks.decksModule
import com.yahyafati.mnemo.feature.settings.settingsModule
import com.yahyafati.mnemo.feature.study.studyModule
import com.yahyafati.mnemo.shell.shellModule
import org.koin.core.module.Module

/**
 * The desktop dependency graph: the same modules as the Android app's `mnemoModules`, with the data
 * layer's desktop bindings (plain files, the bundled SQLite, the OS keychain, jobs on coroutines).
 * It has no widget; the rest is shared.
 */
val desktopModules: List<Module> = listOf(commonModule) + dataLayerModules + listOf(
    domainModule,
    analyticsModule,
    browseModule,
    createModule,
    decksModule,
    settingsModule,
    studyModule,
    shellModule,
)
