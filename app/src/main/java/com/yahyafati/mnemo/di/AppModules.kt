package com.yahyafati.mnemo.di

import com.yahyafati.mnemo.MainViewModel
import com.yahyafati.mnemo.widget.TodayWidgetUpdater
import com.yahyafati.mnemo.core.common.di.commonModule
import com.yahyafati.mnemo.core.data.di.dataLayerModules
import com.yahyafati.mnemo.core.domain.di.domainModule
import com.yahyafati.mnemo.feature.analytics.analyticsModule
import com.yahyafati.mnemo.feature.browse.browseModule
import com.yahyafati.mnemo.feature.create.createModule
import com.yahyafati.mnemo.feature.decks.decksModule
import com.yahyafati.mnemo.feature.settings.settingsModule
import com.yahyafati.mnemo.feature.study.studyModule
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** What only the app itself provides: the shell's ViewModel and the widget. */
val appModule = module {
    viewModelOf(::MainViewModel)
    singleOf(::TodayWidgetUpdater)
}

/**
 * The whole dependency graph. Definitions in later modules replace earlier ones, which is how the
 * app tests swap the database, preferences, WorkManager and cipher (`TestStorageModules`).
 */
val mnemoModules = listOf(commonModule) + dataLayerModules + listOf(
    domainModule,
    analyticsModule,
    browseModule,
    createModule,
    decksModule,
    settingsModule,
    studyModule,
    appModule,
)
