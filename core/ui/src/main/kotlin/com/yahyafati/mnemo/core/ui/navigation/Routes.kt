package com.yahyafati.mnemo.core.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes. They live here, not in the features, so one feature can
// navigate to another without depending on it (ARCHITECTURE §3, rule 1).

/** Top-level tab: deck library and today's summary. */
@Serializable
data object DecksRoute

/** Top-level tab: the Daily Mix study session across all decks. */
@Serializable
data object StudyRoute

/** Top-level tab: manual (and later AI) card creation. */
@Serializable
data object CreateRoute

/** Top-level tab: retention analytics. */
@Serializable
data object AnalyticsRoute

/** Settings, opened from the top-bar avatar. */
@Serializable
data object SettingsRoute

/** A full-screen study session for one deck and its subdecks. */
@Serializable
data class StudySessionRoute(val deckId: String)

/**
 * The full-screen note editor: edits [noteId], or adds notes to [deckId] when [noteId] is null.
 * Property names double as `SavedStateHandle` keys.
 */
@Serializable
data class NoteEditorRoute(val noteId: String? = null, val deckId: String? = null)
