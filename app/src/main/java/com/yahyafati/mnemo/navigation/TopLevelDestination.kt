package com.yahyafati.mnemo.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.yahyafati.mnemo.R
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.ui.navigation.AnalyticsRoute
import com.yahyafati.mnemo.core.ui.navigation.CreateRoute
import com.yahyafati.mnemo.core.ui.navigation.DecksRoute
import com.yahyafati.mnemo.core.ui.navigation.StudyRoute
import kotlin.reflect.KClass

/** The bottom-bar tabs, in display order: Decks · Study · Create · Analytics. */
enum class TopLevelDestination(
    val route: KClass<*>,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    @param:StringRes val labelRes: Int,
) {
    Decks(DecksRoute::class, MnemoIcons.Decks, MnemoIcons.DecksSelected, R.string.nav_decks),
    Study(StudyRoute::class, MnemoIcons.Study, MnemoIcons.StudySelected, R.string.nav_study),
    Create(CreateRoute::class, MnemoIcons.Create, MnemoIcons.CreateSelected, R.string.nav_create),
    Analytics(AnalyticsRoute::class, MnemoIcons.Analytics, MnemoIcons.AnalyticsSelected, R.string.nav_analytics),
}
