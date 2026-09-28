package com.yahyafati.mnemo.core.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes. They live here, not in the features, so one feature can
// navigate to another without depending on it (ARCHITECTURE §3, rule 1).

/** Top-level tab: deck library and today's summary. */
@Serializable
data object DecksRoute

/** Top-level tab: study session. Phase 1 adds the deck selection. */
@Serializable
data object StudyRoute

/** Top-level tab: manual and AI card creation. */
@Serializable
data object CreateRoute

/** Top-level tab: retention analytics. */
@Serializable
data object AnalyticsRoute

/** Settings, opened from the top-bar avatar. */
@Serializable
data object SettingsRoute
