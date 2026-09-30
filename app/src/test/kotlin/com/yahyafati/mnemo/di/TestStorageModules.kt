package com.yahyafati.mnemo.di

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.android.build
import com.yahyafati.mnemo.core.datastore.UserPreferencesDataSource
import com.yahyafati.mnemo.core.security.SecretCipher
import com.yahyafati.mnemo.core.testing.security.SoftwareSecretCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.util.UUID

/**
 * What app tests replace in the production graph ([mnemoModules]); later modules win.
 *
 * - Every test gets a fresh in-memory database.
 * - Every test gets its own preferences file: DataStore allows one instance per file per process.
 *   Onboarding starts out finished, so tests open on the Decks tab; the onboarding test resets it.
 * - A synchronous test WorkManager: the real one needs `MnemoApplication`'s configuration.
 * - Robolectric has no Android Keystore: keys are encrypted with an in-memory AES key instead.
 */
val testStorageModule = module {
    single { MnemoDatabase.build(get<Context>(), name = null) }
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
            File(get<Context>().cacheDir, "test-${UUID.randomUUID()}.preferences_pb")
        }.also { store -> runBlocking { UserPreferencesDataSource(store).setOnboardingCompleted(true) } }
    }
    single<WorkManager> {
        val context = get<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        WorkManager.getInstance(context)
    }
    single<SecretCipher> { SoftwareSecretCipher() }
}

/**
 * The application for app tests (`@Config(application = TestMnemoApplication::class)`): the real
 * graph with [testStorageModule] on top, and none of `MnemoApplication`'s background work.
 * Robolectric makes a new application per test, so the previous test's graph is stopped first.
 */
class TestMnemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        stopKoin()
        startKoin {
            androidContext(this@TestMnemoApplication)
            modules(mnemoModules + testStorageModule)
        }
    }
}
