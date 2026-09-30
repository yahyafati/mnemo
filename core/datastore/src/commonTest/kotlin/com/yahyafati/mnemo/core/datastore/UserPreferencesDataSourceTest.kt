package com.yahyafati.mnemo.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.yahyafati.mnemo.core.model.BackupSettings
import com.yahyafati.mnemo.core.model.CardFontSize
import com.yahyafati.mnemo.core.model.DarkThemeConfig
import com.yahyafati.mnemo.core.model.FsrsWeights
import com.yahyafati.mnemo.core.model.ReminderSettings
import com.yahyafati.mnemo.core.model.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserPreferencesDataSourceTest {
    private val directory: File = Files.createTempDirectory("mnemo-datastore").toFile()

    @AfterTest
    fun deleteFiles() {
        directory.deleteRecursively()
    }

    private val testScope = TestScope(UnconfinedTestDispatcher())

    private fun dataSource() = UserPreferencesDataSource(
        PreferenceDataStoreFactory.create(scope = testScope.backgroundScope) {
            File(directory, "prefs.preferences_pb")
        },
    )

    @Test
    fun defaultsWhenEmpty() = testScope.runTest {
        assertEquals(UserSettings(), dataSource().settings.first())
    }

    @Test
    fun roundTripsEveryField() = testScope.runTest {
        val source = dataSource()
        source.setDesiredRetention(0.85)
        source.setNewCardsPerDay(5)
        source.setReviewsPerDay(50)
        source.setLearningSteps(listOf(Duration.ofMinutes(5)))
        source.setRelearningSteps(emptyList())
        source.setDarkThemeConfig(DarkThemeConfig.Dark)
        source.setUseDynamicColor(true)
        source.setCardFontSize(CardFontSize.Large)
        source.setAutoBackup(enabled = true, folderUri = "content://tree/backups")
        source.setLastBackupAt(Instant.ofEpochMilli(1234))
        source.setFsrsWeights(weights)
        source.setReminder(ReminderSettings(enabled = true, time = LocalTime.of(7, 45)))
        source.setAutoPlayAudio(false)
        source.setOnboardingCompleted(true)

        assertEquals(
            UserSettings(
                desiredRetention = 0.85,
                newCardsPerDay = 5,
                reviewsPerDay = 50,
                learningSteps = listOf(Duration.ofMinutes(5)),
                relearningSteps = emptyList(),
                darkThemeConfig = DarkThemeConfig.Dark,
                useDynamicColor = true,
                cardFontSize = CardFontSize.Large,
                backup = BackupSettings(autoBackupEnabled = true, folderUri = "content://tree/backups", lastBackupAt = Instant.ofEpochMilli(1234)),
                fsrsWeights = weights,
                reminder = ReminderSettings(enabled = true, time = LocalTime.of(7, 45)),
                autoPlayAudio = false,
                onboardingCompleted = true,
            ),
            source.settings.first(),
        )
    }

    @Test
    fun resettingFsrsWeightsGoesBackToTheDefaults() = testScope.runTest {
        val source = dataSource()
        source.setFsrsWeights(weights)
        source.setFsrsWeights(null)
        assertNull(source.settings.first().fsrsWeights)
    }

    private val weights = FsrsWeights(
        values = List(21) { 0.1 + it / 10.0 },
        optimizedAt = Instant.ofEpochMilli(5678),
        trainingReviews = 900,
        previousLoss = 0.47,
        loss = 0.45,
    )
}
