package com.yahyafati.mnemo.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.yahyafati.mnemo.core.data.di.WorkModule
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.di.DatabaseModule
import com.yahyafati.mnemo.core.datastore.di.DataStoreModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.UUID
import javax.inject.Singleton

/** Every test gets a fresh in-memory database. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): MnemoDatabase = MnemoDatabase.build(context, name = null)
}

/** Every test gets its own preferences file: DataStore allows one instance per file per process. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataStoreModule::class])
object TestDataStoreModule {
    @Provides
    @Singleton
    fun providesUserPreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
            File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb")
        }
}

/** A synchronous test WorkManager: HiltTestApplication doesn't configure the real one. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [WorkModule::class])
object TestWorkModule {
    @Provides
    @Singleton
    fun providesWorkManager(@ApplicationContext context: Context): WorkManager {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        return WorkManager.getInstance(context)
    }
}
