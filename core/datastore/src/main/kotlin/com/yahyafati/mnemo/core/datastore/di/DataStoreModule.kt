package com.yahyafati.mnemo.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.yahyafati.mnemo.core.common.platform.AppDirectories
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

/** The preferences DataStore, in the file [AppDirectories] names. Tests replace it with one on a file of their own. */
val dataStoreModule = module {
    single<DataStore<Preferences>> {
        val directories = get<AppDirectories>()
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + dispatcher(MnemoDispatchers.IO)),
            produceFile = { directories.dataStoreFile(DataStoreFiles.USER_PREFERENCES) },
        )
    }
    factoryOf(::UserPreferencesDataSource)
}
