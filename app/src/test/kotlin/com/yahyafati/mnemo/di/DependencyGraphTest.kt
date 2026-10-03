package com.yahyafati.mnemo.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.yahyafati.mnemo.core.security.FileSecretStore
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.TypeQualifier
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.check.checkModules
import org.koin.test.verify.ParameterTypeInjection
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.koin.core.annotation.KoinExperimentalAPI

/**
 * Hilt failed the build on a missing binding; Koin finds out at run time. These tests are that
 * check (ADR 0010, finding 5): [verifyAll] reads the constructor references, and `checkModules`
 * builds every definition, which also catches a missing binding inside a lambda.
 */
@OptIn(KoinExperimentalAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DependencyGraphTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyConstructorReferenceHasABinding() {
        // What Android or WorkManager hands over at run time rather than the graph.
        module { includes(mnemoModules) }.verify(
            extraTypes = listOf(Context::class, Application::class, SavedStateHandle::class, WorkerParameters::class),
            // These are built through a secondary constructor that takes a Context; the primary one
            // takes the pieces the secondary derives from it, which verify() would look for.
            injections = injectedParameters(
                ParameterTypeInjection(Class.forName("com.yahyafati.mnemo.core.data.repository.FileMediaRepository").kotlin, listOf(File::class)),
                ParameterTypeInjection(FileSecretStore::class, listOf(Function0::class)),
                // Likewise: the cache directory comes from AppDirectories through a lambda, the limits are defaults.
                ParameterTypeInjection(
                    Class.forName("com.yahyafati.mnemo.core.ingest.EpubReader").kotlin,
                    listOf(Function0::class, Class.forName("com.yahyafati.mnemo.core.ingest.EpubLimits").kotlin),
                ),
            ),
        )
    }

    @Test
    fun everyDefinitionCanBeBuilt() {
        val workerParameters = workerParameters()
        koinApplication {
            androidContext(context)
            modules(mnemoModules + testStorageModule)
        }.checkModules {
            // ViewModels read their navigation arguments from the handle.
            for (name in VIEW_MODELS_WITH_ARGUMENTS) withParameter(Class.forName(name).kotlin) { SavedStateHandle() }
            // Workers get their WorkerParameters from WorkManager.
            // (Koin registers a worker under its own class as the qualifier.)
            for (name in WORKERS) {
                val worker = Class.forName(name).kotlin
                withParameter(worker, TypeQualifier(worker)) { workerParameters }
            }
        }
    }

    /** WorkManager builds real [WorkerParameters] only for a worker; this one keeps them. */
    private fun workerParameters(): WorkerParameters {
        TestListenableWorkerBuilder<ParametersKeeper>(context).build()
        return checkNotNull(ParametersKeeper.parameters)
    }

    class ParametersKeeper(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
        init {
            Companion.parameters = parameters
        }

        override suspend fun doWork(): Result = Result.success()

        companion object {
            var parameters: WorkerParameters? = null
        }
    }

    private companion object {
        val VIEW_MODELS_WITH_ARGUMENTS = listOf(
            "com.yahyafati.mnemo.feature.study.StudyViewModel",
            "com.yahyafati.mnemo.feature.create.NoteEditorViewModel",
            "com.yahyafati.mnemo.feature.create.BookImportViewModel",
            "com.yahyafati.mnemo.feature.create.coauthor.CoAuthorViewModel",
            "com.yahyafati.mnemo.feature.browse.BrowseViewModel",
            "com.yahyafati.mnemo.feature.settings.ai.ProviderEditorViewModel",
        )

        val WORKERS = listOf(
            "ImportWorker",
            "ExportWorker",
            "BackupWorker",
            "MediaCleanupWorker",
            "OptimizeFsrsWorker",
            "ReminderWorker",
            "SyncWorker",
        ).map { "com.yahyafati.mnemo.core.data.work.$it" }
    }
}
