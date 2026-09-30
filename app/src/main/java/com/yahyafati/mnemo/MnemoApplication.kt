package com.yahyafati.mnemo

import android.app.Application
import androidx.work.Configuration
import com.yahyafati.mnemo.core.data.backup.PendingRestore
import com.yahyafati.mnemo.core.data.repository.DataTransferRepository
import com.yahyafati.mnemo.core.data.repository.ReminderRepository
import com.yahyafati.mnemo.di.mnemoModules
import com.yahyafati.mnemo.widget.TodayWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import org.koin.core.context.startKoin

class MnemoApplication : Application(), Configuration.Provider {
    private val transferRepository: DataTransferRepository by inject()
    private val reminderRepository: ReminderRepository by inject()
    private val widgetUpdater: TodayWidgetUpdater by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        // A restore staged in Settings replaces the collection before Koin creates the database
        // and preferences, which happens on first use, after startKoin.
        PendingRestore.applyIfPresent(this)
        super.onCreate()
        startKoin {
            androidContext(this@MnemoApplication)
            modules(mnemoModules)
        }
        scope.launch { transferRepository.scheduleMaintenance() }
        // After an update, a restore or a time-zone change the next reminder is recomputed.
        scope.launch { reminderRepository.reschedule() }
        widgetUpdater.start(scope)
    }

    /** Workers get their dependencies from Koin (`workModule` in `:core:data`). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(KoinWorkerFactory()).build()
}
