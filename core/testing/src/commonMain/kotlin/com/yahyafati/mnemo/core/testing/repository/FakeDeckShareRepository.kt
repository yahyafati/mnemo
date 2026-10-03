package com.yahyafati.mnemo.core.testing.repository

import com.yahyafati.mnemo.core.data.repository.DeckShareRepository
import com.yahyafati.mnemo.core.data.repository.SharedDeck
import java.io.IOException

/** Records what was prepared; set [failure] to make the next ones fail. */
class FakeDeckShareRepository : DeckShareRepository {
    val prepared = mutableListOf<Pair<String, String>>()
    var failure: IOException? = null

    override suspend fun prepare(deckId: String, name: String, onProgress: suspend (Float) -> Unit): SharedDeck {
        prepared += deckId to name
        failure?.let { throw it }
        onProgress(1f)
        return SharedDeck("/cache/share/$name.apkg", "$name.apkg")
    }
}
