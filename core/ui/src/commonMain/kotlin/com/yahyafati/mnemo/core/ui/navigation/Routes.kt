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

/**
 * Import a book (EPUB) into one deck per chapter. Reads the book at [location] on arrival (a file that was
 * dropped or opened with Mnemo); without one, the screen asks for a file. Property names double as
 * `SavedStateHandle` keys.
 */
@Serializable
data class BookImportRoute(val location: String? = null)

/**
 * The card browser, filtered to [deckId] and its subdecks when given. [focusSearch] puts the cursor
 * in the search box on arrival (Find, on a computer).
 */
@Serializable
data class BrowseRoute(val deckId: String? = null, val focusSearch: Boolean = false)

/** Settings › About › Open-source licenses. */
@Serializable
data object LicensesRoute

/** Settings › AI providers: the provider list, task routing and token usage. */
@Serializable
data object AiProvidersRoute

/**
 * The provider editor: edits [providerId], or adds a new provider (starting from [presetId], if
 * given) when [providerId] is null. Property names double as `SavedStateHandle` keys.
 */
@Serializable
data class AiProviderEditorRoute(val providerId: String? = null, val presetId: String? = null)

/** Settings › Sync: set up, status, devices and leaving (docs/sync/ROADMAP.md S5). */
@Serializable
data object SyncRoute
