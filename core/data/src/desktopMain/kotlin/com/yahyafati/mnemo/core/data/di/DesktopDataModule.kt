package com.yahyafati.mnemo.core.data.di

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.yahyafati.mnemo.core.common.di.dispatcher
import com.yahyafati.mnemo.core.common.dispatchers.MnemoDispatchers
import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.common.platform.DocumentAccess
import com.yahyafati.mnemo.core.data.desktop.AppRestarter
import com.yahyafati.mnemo.core.data.desktop.DesktopAppDirectories
import com.yahyafati.mnemo.core.data.desktop.DesktopDataTransferRepository
import com.yahyafati.mnemo.core.data.desktop.DesktopDocumentAccess
import com.yahyafati.mnemo.core.data.desktop.DesktopFsrsOptimizationRepository
import com.yahyafati.mnemo.core.data.desktop.DesktopReminderRepository
import com.yahyafati.mnemo.core.data.desktop.ProcessAppRestarter
import com.yahyafati.mnemo.core.data.repository.DataTransferRepository
import com.yahyafati.mnemo.core.data.repository.FsrsOptimizationRepository
import com.yahyafati.mnemo.core.data.repository.ReminderRepository
import com.yahyafati.mnemo.core.database.di.databaseModule
import com.yahyafati.mnemo.core.datastore.di.dataStoreModule
import com.yahyafati.mnemo.core.ingest.PdfTextExtractor
import com.yahyafati.mnemo.core.ingest.SpeechTranscriber
import com.yahyafati.mnemo.core.ingest.desktop.NoSpeechTranscriber
import com.yahyafati.mnemo.core.ingest.desktop.PdfBoxTextExtractor
import com.yahyafati.mnemo.core.security.di.securityModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose

/** The qualifier of the scope the desktop's background jobs run in. */
private val JobScope = named("jobs")

/**
 * The desktop bindings of the data layer: where the collection lives, plain files as documents,
 * the bundled SQLite for Anki packages, PDFBox for PDF text, and the jobs on coroutines. The
 * launcher binds [AppRestarter] if it needs more than starting the process again.
 */
val desktopDataModule = module {
    single<AppDirectories> { DesktopAppDirectories.default() }
    single<DocumentAccess> { DesktopDocumentAccess() }
    single<AppRestarter> { ProcessAppRestarter }

    // Jobs run while the app is open; a failing one never takes the others down.
    single<CoroutineScope>(JobScope) { CoroutineScope(SupervisorJob() + dispatcher(MnemoDispatchers.Default)) } onClose { it?.cancel() }
    // Single: its queues hold the state screens observe.
    single<DataTransferRepository> {
        DesktopDataTransferRepository(get(JobScope), get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    single<FsrsOptimizationRepository> { DesktopFsrsOptimizationRepository(get(JobScope), get()) }
    factoryOf(::DesktopReminderRepository) bind ReminderRepository::class

    factory<SQLiteDriver> { BundledSQLiteDriver() }

    single<PdfTextExtractor> { PdfBoxTextExtractor() }
    factory<SpeechTranscriber> { NoSpeechTranscriber }
}

actual val dataLayerModules = listOf(databaseModule, dataStoreModule, securityModule, dataModule, desktopDataModule)
