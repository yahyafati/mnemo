package com.yahyafati.mnemo.core.data.di

import com.yahyafati.mnemo.core.ai.client.OpenAiCompatibleClient
import com.yahyafati.mnemo.core.ai.generate.CardGenerationClient
import com.yahyafati.mnemo.core.ai.generate.ChatTextRunner
import com.yahyafati.mnemo.core.ai.generate.CoAuthorClient
import com.yahyafati.mnemo.core.ai.generate.StudyAssistClient
import com.yahyafati.mnemo.core.ai.probe.ConnectionProbe
import com.yahyafati.mnemo.core.common.di.dispatcher
import com.yahyafati.mnemo.core.common.dispatchers.MnemoDispatchers
import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.common.platform.DocumentAccess
import com.yahyafati.mnemo.core.data.backup.BackupManager
import com.yahyafati.mnemo.core.data.job.BackupJob
import com.yahyafati.mnemo.core.data.job.ExportJob
import com.yahyafati.mnemo.core.data.job.ImportJob
import com.yahyafati.mnemo.core.data.repository.AiProviderRepository
import com.yahyafati.mnemo.core.data.repository.CardBrowserRepository
import com.yahyafati.mnemo.core.data.repository.CardGenerationRepository
import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.data.repository.CoAuthorRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.data.repository.DefaultAiProviderRepository
import com.yahyafati.mnemo.core.data.repository.DefaultCardGenerationRepository
import com.yahyafati.mnemo.core.data.repository.DefaultCoAuthorRepository
import com.yahyafati.mnemo.core.data.repository.DefaultSourceRepository
import com.yahyafati.mnemo.core.data.repository.DefaultStudyAssistRepository
import com.yahyafati.mnemo.core.data.repository.DefaultUserSettingsRepository
import com.yahyafati.mnemo.core.data.repository.FileMediaRepository
import com.yahyafati.mnemo.core.data.repository.MediaRepository
import com.yahyafati.mnemo.core.data.repository.OfflineCardBrowserRepository
import com.yahyafati.mnemo.core.data.repository.OfflineCardRepository
import com.yahyafati.mnemo.core.data.repository.OfflineDeckRepository
import com.yahyafati.mnemo.core.data.repository.OfflineReviewRepository
import com.yahyafati.mnemo.core.data.repository.OfflineStatsRepository
import com.yahyafati.mnemo.core.data.repository.ProviderConfigs
import com.yahyafati.mnemo.core.data.repository.ReviewRepository
import com.yahyafati.mnemo.core.data.repository.SourceRepository
import com.yahyafati.mnemo.core.data.repository.StatsRepository
import com.yahyafati.mnemo.core.data.repository.StudyAssistRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.data.scheduling.FsrsOptimization
import com.yahyafati.mnemo.core.data.transfer.AnkiExporter
import com.yahyafati.mnemo.core.data.transfer.AnkiImporter
import com.yahyafati.mnemo.core.data.transfer.JsonExporter
import com.yahyafati.mnemo.core.database.di.databaseModule
import com.yahyafati.mnemo.core.datastore.di.dataStoreModule
import com.yahyafati.mnemo.core.ingest.EpubReader
import com.yahyafati.mnemo.core.ingest.WebPageExtractor
import com.yahyafati.mnemo.core.ingest.site.WikipediaExtractor
import com.yahyafati.mnemo.core.security.di.securityModule
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Repositories, the transfer code, and the AI and ingest clients. Bindings are per request
 * (`factory`) unless the object holds a resource that should be shared. Needs the modules of
 * `:core:common`, `:core:database`, `:core:datastore` and `:core:security`, and the platform's
 * bindings (see [dataLayerModules]).
 */
val dataModule = module {
    factory { OfflineDeckRepository(get(), get(), get(), get(), get(), dispatcher(MnemoDispatchers.Default)) } bind DeckRepository::class
    factoryOf(::OfflineCardRepository) bind CardRepository::class
    factoryOf(::OfflineReviewRepository) bind ReviewRepository::class
    factoryOf(::DefaultUserSettingsRepository) bind UserSettingsRepository::class
    factory {
        FileMediaRepository(get<AppDirectories>().media, get(), get(), get(), dispatcher(MnemoDispatchers.IO), get<DocumentAccess>())
    } bind MediaRepository::class
    factoryOf(::OfflineCardBrowserRepository) bind CardBrowserRepository::class
    factory {
        DefaultAiProviderRepository(get(), get(), get(), get(), get(), dispatcher(MnemoDispatchers.Default))
    } bind AiProviderRepository::class
    factory {
        DefaultCardGenerationRepository(get(), get(), get(), dispatcher(MnemoDispatchers.IO))
    } bind CardGenerationRepository::class
    factory {
        DefaultCoAuthorRepository(get(), get(), get(), get(), get(), dispatcher(MnemoDispatchers.IO))
    } bind CoAuthorRepository::class
    factory {
        DefaultStudyAssistRepository(get(), get(), get(), get(), get(), dispatcher(MnemoDispatchers.IO))
    } bind StudyAssistRepository::class
    factory {
        DefaultSourceRepository(get<DocumentAccess>(), get(), get(), get(), get(), dispatcher(MnemoDispatchers.IO))
    } bind SourceRepository::class
    factoryOf(::OfflineStatsRepository) bind StatsRepository::class

    factoryOf(::ProviderConfigs)
    factory { FsrsOptimization(get(), get(), get(), dispatcher(MnemoDispatchers.Default)) }
    factory { BackupManager(get<AppDirectories>(), get<DocumentAccess>(), get(), get(), get(), dispatcher(MnemoDispatchers.IO)) }
    factoryOf(::ImportJob)
    factoryOf(::ExportJob)
    factoryOf(::BackupJob)
    factoryOf(::AnkiImporter)
    factoryOf(::AnkiExporter)
    factoryOf(::JsonExporter)

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
    // Shares the AI client's connection pool; it follows redirects on its own copy of the client.
    // Site extractors (ADR 0012) go in `sites`, one class each.
    factory { WebPageExtractor(get(), get(), sites = listOf(WikipediaExtractor())) }
    factory { EpubReader(cacheDir = { get<AppDirectories>().cache }) }
}

/**
 * Everything below the use cases: Room, DataStore, secrets, repositories and the platform's job
 * runner. Only `:core:data` sees the first three, so the app starts them through this list. Needs
 * `commonModule`, and on Android an `androidContext`.
 *
 * The platform part binds what differs: where files live and how the user's are opened
 * ([AppDirectories], [DocumentAccess]), the SQLite driver Anki packages are read with, PDF text
 * and dictation, and the background jobs behind [DataTransferRepository],
 * [FsrsOptimizationRepository] and [ReminderRepository] (WorkManager on Android, coroutines on
 * desktop).
 */
expect val dataLayerModules: List<Module>
