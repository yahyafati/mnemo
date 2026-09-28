package com.yahyafati.mnemo.feature.analytics.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.yahyafati.mnemo.core.ui.navigation.AnalyticsRoute
import com.yahyafati.mnemo.feature.analytics.AnalyticsScreen

fun NavController.navigateToAnalytics(navOptions: NavOptions? = null) = navigate(AnalyticsRoute, navOptions)

fun NavGraphBuilder.analyticsScreen() {
    composable<AnalyticsRoute> {
        AnalyticsScreen()
    }
}
