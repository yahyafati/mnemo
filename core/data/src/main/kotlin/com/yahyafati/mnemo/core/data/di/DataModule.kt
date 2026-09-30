package com.yahyafati.mnemo.core.data.di

import android.content.Context
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.work.WorkManager
import com.yahyafati.mnemo.core.ai.client.OpenAiCompatibleClient
import com.yahyafati.mnemo.core.ai.generate.CardGenerationClient
import com.yahyafati.mnemo.core.ai.generate.ChatTextRunner
import com.yahyafati.mnemo.core.ai.generate.CoAuthorClient
import com.yahyafati.mnemo.core.ai.generate.StudyAssistClient
import com.yahyafati.mnemo.core.ai.probe.ConnectionProbe
import com.yahyafati.mnemo.core.common.di.dispatcher
import com.yahyafati.mnemo.core.common.dispatchers.MnemoDispatchers
import com.yahyafati.mnemo.core.data.backup.BackupManager
import com.yahyafati.mnemo.core.data.repository.AiProviderRepository
import com.yahyafati.mnemo.core.data.repository.CardBrowserRepository
import com.yahyafati.mnemo.core.data.repository.CardGenerationRepository
import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.data.repository.CoAuthorRepository
import com.yahyafati.mnemo.core.data.repository.DataTransferRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.data.repository.DefaultAiProviderRepository
import com.yahyafati.mnemo.core.data.repository.DefaultCardGenerationRepository
import com.yahyafati.mnemo.core.data.repository.DefaultCoAuthorRepository
import com.yahyafati.mnemo.core.data.repository.DefaultSourceRepository
import com.yahyafati.mnemo.core.data.repository.DefaultStudyAssistRepository
import com.yahyafati.mnemo.core.data.repository.DefaultUserSettingsRepository
import com.yahyafati.mnemo.core.data.repository.FileMediaRepository
import com.yahyafati.mnemo.core.data.repository.FsrsOptimizationRepository
import com.yahyafati.mnemo.core.data.repository.MediaRepository
import com.yahyafati.mnemo.core.data.repository.OfflineCardBrowserRepository
import com.yahyafati.mnemo.core.data.repository.OfflineCardRepository
import com.yahyafati.mnemo.core.data.repository.OfflineDeckRepository
import com.yahyafati.mnemo.core.data.repository.OfflineReviewRepository
import com.yahyafati.mnemo.core.data.repository.OfflineStatsRepository
import com.yahyafati.mnemo.core.data.repository.ProviderConfigs
import com.yahyafati.mnemo.core.data.repository.ReminderRepository
import com.yahyafati.mnemo.core.data.repository.ReviewRepository
import com.yahyafati.mnemo.core.data.repository.SourceRepository
import com.yahyafati.mnemo.core.data.repository.StatsRepository
import com.yahyafati.mnemo.core.data.repository.StudyAssistRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.data.repository.WorkManagerDataTransferRepository
import com.yahyafati.mnemo.core.data.repository.WorkManagerFsrsOptimizationRepository
import com.yahyafati.mnemo.core.data.repository.WorkManagerReminderRepository
import com.yahyafati.mnemo.core.data.scheduling.FsrsOptimization
import com.yahyafati.mnemo.core.data.transfer.AnkiExporter
import com.yahyafati.mnemo.core.data.transfer.AnkiImporter
import com.yahyafati.mnemo.core.data.transfer.JsonExporter
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
import com.yahyafati.mnemo.core.ingest.WebPageExtractor
import com.yahyafati.mnemo.core.security.di.securityModule
import okhttp3.OkHttpClient
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Repositories, the transfer code, and the AI and ingest clients. Bindings are per request
 * (`factory`) unless the object holds a resource that should be shared. Needs the modules of
 * `:core:common`, `:core:database`, `:core:datastore` and `:core:security`.
 */
val dataModule = module {
    factory { OfflineDeckRepository(get(), get(), get(), get(), get(), dispatcher(MnemoDispatchers.Default)) } bind DeckRepository::class
    factoryOf(::OfflineCardRepository) bind CardRepository::class
    factoryOf(::OfflineReviewRepository) bind ReviewRepository::class
    factoryOf(::DefaultUserSettingsRepository) bind UserSettingsRepository::class
    factory { FileMediaRepository(get<Context>(), get(), get(), get(), dispatcher(MnemoDispatchers.IO)) } bind MediaRepository::class
    factoryOf(::OfflineCardBrowserRepository) bind CardBrowserRepository::class
    factoryOf(::WorkManagerDataTransferRepository) bind DataTransferRepository::class
    factory {
        DefaultAiProviderRepository(get(), get(), get(), get(), get(), dispatcher(MnemoDispatchers.Default))
    } bind AiProviderRepository::class
    factory {
        DefaultCardGenerationRepository(get(), get(), get(), dispatcher(MnemoDispatchers.IO))
    } bind CardGenerationRepository::class
    factory {
        DefaultCoAuthorRepository(get(), get(), get(), get(), get(), dispatcher(MnemoDispatchers.IO))
    } bind CoAuthorRepository::class
    factory { DefaultStudyAssistRepository(get(), get(), get(), dispatcher(MnemoDispatchers.IO)) } bind StudyAssistRepository::class
    factory {
        DefaultSourceRepository(get(), get(), get(), get(), dispatcher(MnemoDispatchers.IO))
    } bind SourceRepository::class
    factoryOf(::OfflineStatsRepository) bind StatsRepository::class
    // Also injected as itself: ReminderWorker reschedules through it.
    factoryOf(::WorkManagerReminderRepository) bind ReminderRepository::class
    factoryOf(::WorkManagerFsrsOptimizationRepository) bind FsrsOptimizationRepository::class

    factoryOf(::ProviderConfigs)
    factory { FsrsOptimization(get(), get(), get(), dispatcher(MnemoDispatchers.Default)) }
    factory { BackupManager(get<Context>(), get(), get(), get(), dispatcher(MnemoDispatchers.IO)) }
    factoryOf(::AnkiImporter)
    factoryOf(::AnkiExporter)
    factoryOf(::JsonExporter)

    // Replaced in app tests, which initialize a test WorkManager.
    single { WorkManager.getInstance(get<Context>()) }

    // Anki packages are read and written with Android's own SQLite.
    factory<SQLiteDriver> { AndroidSQLiteDriver() }

    /*
     * One connection pool for every provider; per-request timeouts come from the provider.
     * Redirects are not followed: an API never needs them, and following one could send the key
     * (or custom auth headers) somewhere else, or downgrade HTTPS to HTTP. No cache, no cookies,
     * and no logging interceptor: requests carry API keys and card content.
     */
    single {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
    factory { OpenAiCompatibleClient(get()) }
    factory { ConnectionProbe(get()) }
    factory { ChatTextRunner(get()) }
    factory { CardGenerationClient(get()) }
    factory { StudyAssistClient(get()) }
    factory { CoAuthorClient(get()) }

    single { PdfTextExtractor(get<Context>()) }
    // Shares the AI client's connection pool; it follows redirects on its own copy of the client.
    factory { WebPageExtractor(get(), get()) }
    factory { SpeechTranscriber(get<Context>()) }
}

/** The WorkManager workers: created by Koin's `KoinWorkerFactory`, which `MnemoApplication` installs. */
val workModule = module {
    workerOf(::ImportWorker)
    workerOf(::ExportWorker)
    workerOf(::BackupWorker)
    workerOf(::MediaCleanupWorker)
    workerOf(::OptimizeFsrsWorker)
    workerOf(::ReminderWorker)
}

/**
 * Everything below the use cases: Room, DataStore, secrets, repositories and workers. Only
 * `:core:data` sees the first three, so the app starts them through this list. Needs
 * `commonModule` and an `androidContext`.
 */
val dataLayerModules = listOf(databaseModule, dataStoreModule, securityModule, dataModule, workModule)
