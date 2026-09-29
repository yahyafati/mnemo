package com.yahyafati.mnemo

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.yahyafati.mnemo.core.data.backup.PendingRestore
import com.yahyafati.mnemo.core.data.repository.DataTransferRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MnemoApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var transferRepository: DataTransferRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        // A restore staged in Settings replaces the collection before Hilt creates the database
        // and preferences, which happens in super.onCreate().
        PendingRestore.applyIfPresent(this)
        super.onCreate()
        scope.launch { transferRepository.scheduleMaintenance() }
    }

    /** Workers get their dependencies from Hilt (`@HiltWorker` in `:core:data`). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
