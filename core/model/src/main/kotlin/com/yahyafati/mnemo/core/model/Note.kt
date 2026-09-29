package com.yahyafati.mnemo.core.model

import java.time.Instant

enum class NoteSource {
    Manual,
    Ai,
    Import,
}

/** The content a user writes. Its cards are derived from it by [NoteKind]. */
data class Note(
    val id: String,
    val deckId: String,
    val noteTypeId: String,
    /** One value per [NoteType.fields] entry, in the same order. */
    val fields: List<String>,
    val tags: List<String> = emptyList(),
    val source: NoteSource = NoteSource.Manual,
    val createdAt: Instant,
    val updatedAt: Instant,
    /**
     * Anki's globally unique note id, for notes that came from (or went to) an Anki package.
     * Importing the same package twice skips notes whose guid is already in the collection.
     */
    val guid: String? = null,
) {
    fun field(index: Int): String = fields.getOrElse(index) { "" }
}
