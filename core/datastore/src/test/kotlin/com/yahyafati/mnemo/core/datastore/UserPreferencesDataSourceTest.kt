package com.yahyafati.mnemo.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.yahyafati.mnemo.core.model.CardFontSize
import com.yahyafati.mnemo.core.model.DarkThemeConfig
import com.yahyafati.mnemo.core.model.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Duration
import kotlin.test.assertEquals

class UserPreferencesDataSourceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val testScope = TestScope(UnconfinedTestDispatcher())

    private fun dataSource() = UserPreferencesDataSource(
        PreferenceDataStoreFactory.create(scope = testScope.backgroundScope) {
            File(tmp.root, "prefs.preferences_pb")
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
            ),
            source.settings.first(),
        )
    }
}
