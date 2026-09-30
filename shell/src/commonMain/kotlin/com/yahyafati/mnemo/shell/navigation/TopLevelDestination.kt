package com.yahyafati.mnemo.shell.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.ui.navigation.AnalyticsRoute
import com.yahyafati.mnemo.core.ui.navigation.CreateRoute
import com.yahyafati.mnemo.core.ui.navigation.DecksRoute
import com.yahyafati.mnemo.core.ui.navigation.StudyRoute
import com.yahyafati.mnemo.shell.resources.Res
import com.yahyafati.mnemo.shell.resources.nav_analytics
import com.yahyafati.mnemo.shell.resources.nav_create
import com.yahyafati.mnemo.shell.resources.nav_decks
import com.yahyafati.mnemo.shell.resources.nav_study
import org.jetbrains.compose.resources.StringResource
import kotlin.reflect.KClass

/** The bottom-bar tabs, in display order: Decks · Study · Create · Analytics. */
enum class TopLevelDestination(
    val route: KClass<*>,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val label: StringResource,
) {
    Decks(DecksRoute::class, MnemoIcons.Decks, MnemoIcons.DecksSelected, Res.string.nav_decks),
    Study(StudyRoute::class, MnemoIcons.Study, MnemoIcons.StudySelected, Res.string.nav_study),
    Create(CreateRoute::class, MnemoIcons.Create, MnemoIcons.CreateSelected, Res.string.nav_create),
    Analytics(AnalyticsRoute::class, MnemoIcons.Analytics, MnemoIcons.AnalyticsSelected, Res.string.nav_analytics),
}
