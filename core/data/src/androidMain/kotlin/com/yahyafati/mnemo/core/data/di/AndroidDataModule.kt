package com.yahyafati.mnemo.core.data.di

import android.content.Context
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.work.WorkManager
import com.yahyafati.mnemo.core.common.di.dispatcher
import com.yahyafati.mnemo.core.common.dispatchers.MnemoDispatchers
import com.yahyafati.mnemo.core.data.android.AndroidDeviceDescriber
import com.yahyafati.mnemo.core.data.android.platformModule
import com.yahyafati.mnemo.core.data.repository.WorkManagerSyncBackgroundWork
import com.yahyafati.mnemo.core.data.sync.DeviceDescriber
import com.yahyafati.mnemo.core.data.sync.SyncBackgroundWork
import com.yahyafati.mnemo.core.data.work.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.dsl.onClose
import com.yahyafati.mnemo.core.data.repository.DataTransferRepository
import com.yahyafati.mnemo.core.data.repository.FsrsOptimizationRepository
import com.yahyafati.mnemo.core.data.repository.ReminderRepository
import com.yahyafati.mnemo.core.data.repository.WorkManagerDataTransferRepository
import com.yahyafati.mnemo.core.data.repository.WorkManagerFsrsOptimizationRepository
import com.yahyafati.mnemo.core.data.repository.WorkManagerReminderRepository
import com.yahyafati.mnemo.core.data.work.BackupWorker
import com.yahyafati.mnemo.core.data.work.ExportWorker
import com.yahyafati.mnemo.core.data.work.ImportWorker
import com.yahyafati.mnemo.core.data.work.MediaCleanupWorker
import com.yahyafati.mnemo.core.data.work.OptimizeFsrsWorker
import com.yahyafati.mnemo.core.data.work.ReminderWorker
import com.yahyafati.mnemo.core.database.di.databaseModule
import com.yahyafati.mnemo.core.datastore.di.dataStoreModule
import com.yahyafati.mnemo.core.ingest.PdfTextExtractor
import com.yahyafati.mnemo.core.ingest.SpeechTranscriber
import com.yahyafati.mnemo.core.ingest.android.AndroidSpeechTranscriber
import com.yahyafati.mnemo.core.ingest.android.PdfBoxAndroidTextExtractor
import com.yahyafati.mnemo.core.security.di.securityModule
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** The Android bindings of the data layer: WorkManager and the jobs on it, PDF text, dictation. */
val androidDataModule = module {
    factoryOf(::WorkManagerDataTransferRepository) bind DataTransferRepository::class
    // Also injected as itself: ReminderWorker reschedules through it.
    factoryOf(::WorkManagerReminderRepository) bind ReminderRepository::class
    factoryOf(::WorkManagerFsrsOptimizationRepository) bind FsrsOptimizationRepository::class

    // Replaced in app tests, which initialize a test WorkManager.
    single { WorkManager.getInstance(get<Context>()) }

    single<CoroutineScope>(SyncScope) { CoroutineScope(SupervisorJob() + dispatcher(MnemoDispatchers.Default)) } onClose { it?.cancel() }
    factoryOf(::WorkManagerSyncBackgroundWork) bind SyncBackgroundWork::class
    factory<DeviceDescriber> { AndroidDeviceDescriber(get<Context>()) }

    // Anki packages are read and written with Android's own SQLite.
    factory<SQLiteDriver> { AndroidSQLiteDriver() }

    single<PdfTextExtractor> { PdfBoxAndroidTextExtractor(get<Context>()) }
    factory<SpeechTranscriber> { AndroidSpeechTranscriber(get<Context>()) }
}

/** The WorkManager workers: created by Koin's `KoinWorkerFactory`, which `MnemoApplication` installs. */
val workModule = module {
    workerOf(::ImportWorker)
    workerOf(::ExportWorker)
    workerOf(::BackupWorker)
    workerOf(::MediaCleanupWorker)
    workerOf(::OptimizeFsrsWorker)
    workerOf(::ReminderWorker)
    workerOf(::SyncWorker)
}

actual val dataLayerModules = listOf(platformModule, databaseModule, dataStoreModule, securityModule, dataModule, androidDataModule, workModule)
