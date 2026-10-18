package com.yahyafati.mnemo.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.yahyafati.mnemo.core.datastore.UserPreferencesDataSource
import com.yahyafati.mnemo.core.model.ContextSummary
import com.yahyafati.mnemo.core.model.ExtractAdvanced
import com.yahyafati.mnemo.core.model.Overlap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The repository over a real preferences file: what Smart Extract remembers (ADR 0015, C5) is stored in it. */
class UserSettingsRepositoryTest {
    private val directory: File = Files.createTempDirectory("mnemo-settings").toFile()
    private val testScope = TestScope(UnconfinedTestDispatcher())

    @AfterTest
    fun deleteFiles() {
        directory.deleteRecursively()
    }

    private fun repository(): UserSettingsRepository = DefaultUserSettingsRepository(
        UserPreferencesDataSource(
            PreferenceDataStoreFactory.create(scope = testScope.backgroundScope) { File(directory, "prefs.preferences_pb") },
        ),
    )

    @Test
    fun theExtractAdvancedOptionsRoundTrip() = testScope.runTest {
        val repository = repository()
        assertEquals(ExtractAdvanced(), repository.settings.first().extractAdvanced)

        val chosen = ExtractAdvanced(skipIllustrations = false, contextSummary = ContextSummary.Off, overlap = Overlap.Always, language = "Spanish")
        repository.setExtractAdvanced(chosen)
        assertEquals(chosen, repository.settings.first().extractAdvanced)

        repository.setExtractAdvanced(chosen.copy(language = null))
        assertEquals(chosen.copy(language = null), repository.settings.first().extractAdvanced)
    }
}
