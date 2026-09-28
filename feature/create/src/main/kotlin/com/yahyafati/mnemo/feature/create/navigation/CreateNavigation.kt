package com.yahyafati.mnemo.feature.create.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.yahyafati.mnemo.core.ui.navigation.CreateRoute
import com.yahyafati.mnemo.feature.create.CreateScreen

fun NavController.navigateToCreate(navOptions: NavOptions? = null) = navigate(CreateRoute, navOptions)

fun NavGraphBuilder.createScreen() {
    composable<CreateRoute> {
        CreateScreen()
    }
}
