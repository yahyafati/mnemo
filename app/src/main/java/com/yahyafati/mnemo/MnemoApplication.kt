package com.yahyafati.mnemo

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.yahyafati.mnemo.core.data.backup.PendingRestore
import com.yahyafati.mnemo.core.data.repository.DataTransferRepository
import com.yahyafati.mnemo.core.data.repository.ReminderRepository
import com.yahyafati.mnemo.widget.TodayWidgetUpdater
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

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var widgetUpdater: TodayWidgetUpdater

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        // A restore staged in Settings replaces the collection before Hilt creates the database
        // and preferences, which happens in super.onCreate().
        PendingRestore.applyIfPresent(this)
        super.onCreate()
        scope.launch { transferRepository.scheduleMaintenance() }
        // After an update, a restore or a time-zone change the next reminder is recomputed.
        scope.launch { reminderRepository.reschedule() }
        widgetUpdater.start(scope)
    }

    /** Workers get their dependencies from Hilt (`@HiltWorker` in `:core:data`). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
