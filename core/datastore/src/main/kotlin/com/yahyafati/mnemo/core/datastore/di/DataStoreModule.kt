package com.yahyafati.mnemo.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.yahyafati.mnemo.core.common.di.dispatcher
import com.yahyafati.mnemo.core.common.dispatchers.MnemoDispatchers
import com.yahyafati.mnemo.core.datastore.UserPreferencesDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/** The names of the files this module keeps, for the code that backs them up and restores them. */
object DataStoreFiles {
    const val USER_PREFERENCES = "user_preferences"
}

/** The preferences DataStore. Tests replace it with one on a file of their own. */
val dataStoreModule = module {
    single<DataStore<Preferences>> {
        val context = get<Context>()
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + dispatcher(MnemoDispatchers.IO)),
            produceFile = { context.preferencesDataStoreFile(DataStoreFiles.USER_PREFERENCES) },
        )
    }
    factoryOf(::UserPreferencesDataSource)
}
