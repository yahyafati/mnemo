package com.yahyafati.mnemo.di

import com.yahyafati.mnemo.BuildConfig
import com.yahyafati.mnemo.core.common.di.commonModule
import com.yahyafati.mnemo.core.data.di.dataLayerModules
import com.yahyafati.mnemo.core.data.sync.GoogleClient
import com.yahyafati.mnemo.core.data.sync.GoogleClientConfigSource
import com.yahyafati.mnemo.core.domain.di.domainModule
import com.yahyafati.mnemo.feature.analytics.analyticsModule
import com.yahyafati.mnemo.feature.browse.browseModule
import com.yahyafati.mnemo.feature.create.createModule
import com.yahyafati.mnemo.feature.decks.decksModule
import com.yahyafati.mnemo.feature.settings.settingsModule
import com.yahyafati.mnemo.feature.study.studyModule
import com.yahyafati.mnemo.shell.shellModule
import com.yahyafati.mnemo.widget.TodayWidgetUpdater
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** What only the Android app provides: the widget and the Google client of this build. */
val appModule = module {
    singleOf(::TodayWidgetUpdater)

    // Google Drive sync is offered only in a build made with a Google client (`MNEMO_GOOGLE_CLIENT_ID`, ADR 0013).
    factory<GoogleClientConfigSource> {
        GoogleClientConfigSource { BuildConfig.GOOGLE_CLIENT_ID.takeIf { it.isNotBlank() }?.let { GoogleClient(it) } }
    }
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
    shellModule,
    appModule,
)
