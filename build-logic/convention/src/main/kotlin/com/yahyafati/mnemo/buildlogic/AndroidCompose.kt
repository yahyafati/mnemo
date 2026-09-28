package com.yahyafati.mnemo.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Enables Compose and adds the BOM-managed baseline every Compose module needs. */
internal fun Project.configureAndroidCompose(commonExtension: CommonExtension) {
    pluginManager.apply(libs.pluginId("kotlin-compose"))

    commonExtension.apply {
        buildFeatures.compose = true
        // Robolectric-based Compose and screenshot tests need merged resources (fonts, strings).
        testOptions.unitTests.isIncludeAndroidResources = true
    }

    dependencies {
        val bom = libs.library("androidx-compose-bom")
        add("implementation", platform(bom))
        add("testImplementation", platform(bom))
        add("androidTestImplementation", platform(bom))
        add("implementation", libs.library("androidx-compose-ui-tooling-preview"))
        add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
    }
}
