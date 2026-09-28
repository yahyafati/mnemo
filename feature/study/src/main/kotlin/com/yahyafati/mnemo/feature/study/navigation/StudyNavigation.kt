package com.yahyafati.mnemo.feature.study.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.yahyafati.mnemo.core.ui.navigation.StudyRoute
import com.yahyafati.mnemo.feature.study.StudyScreen

fun NavController.navigateToStudy(navOptions: NavOptions? = null) = navigate(StudyRoute, navOptions)

fun NavGraphBuilder.studyScreen() {
    composable<StudyRoute> {
        StudyScreen()
    }
}
