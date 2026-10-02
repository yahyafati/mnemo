package com.yahyafati.mnemo.feature.create

import com.yahyafati.mnemo.core.model.BookSource
import com.yahyafati.mnemo.feature.create.resources.Res
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_chapter_default
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_untitled
import org.jetbrains.compose.resources.getString

/*
 * What the book import and Smart Extract's EPUB source share, so that both name a chapter's deck the same way
 * (a chapter's deck is found again by computing its name, ADR 0011).
 */

/** Chapters the file gave no title get "Chapter N", in the user's language, so lists and deck names agree. */
internal suspend fun BookSource.withDefaultTitles(): BookSource = copy(
    chapters = chapters.map { chapter ->
        if (chapter.title.isNotBlank()) chapter else chapter.copy(title = getString(Res.string.feature_create_book_chapter_default, chapter.id + 1))
    },
)

/** What the book's deck is called when the user hasn't typed a name: its title, or "Untitled book". */
internal suspend fun BookSource.defaultName(): String = title.ifBlank { getString(Res.string.feature_create_book_untitled) }

/** The number of decks the book's chapters are numbered for: ids are positions, so a gap still pads the same. */
internal fun BookSource.deckCount(): Int = maxOf(chapters.size, (chapters.maxOfOrNull { it.id } ?: -1) + 1)
