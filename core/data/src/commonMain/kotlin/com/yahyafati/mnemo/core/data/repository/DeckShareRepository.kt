package com.yahyafati.mnemo.core.data.repository

/**
 * Prepares a deck to be handed to another app through the system's share sheet. The package is the
 * same Anki `.apkg` that Export writes (ADR 0003), so whoever receives it can open it in Mnemo or
 * Anki. Nothing leaves the device here: the file stays in the app's cache until the user picks a
 * recipient in the share sheet.
 */
interface DeckShareRepository {
    /**
     * Writes [deckId] and its subdecks to a file in the app's cache, named after [name], and
     * returns it. [onProgress] gets 0–1. Files from earlier shares older than a day are removed.
     * Throws `IOException` when the file can't be written.
     */
    suspend fun prepare(deckId: String, name: String, onProgress: suspend (Float) -> Unit = {}): SharedDeck
}

/** A deck package ready to share: [location] is the absolute path of the file, [fileName] its name. */
data class SharedDeck(val location: String, val fileName: String)
