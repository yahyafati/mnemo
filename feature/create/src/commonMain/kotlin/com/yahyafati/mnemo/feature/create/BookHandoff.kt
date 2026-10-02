package com.yahyafati.mnemo.feature.create

import com.yahyafati.mnemo.core.model.BookSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A book on its way from the book import to Smart Extract (docs/epub/ROADMAP.md, B5): the import has
 * already read the file, so "Generate cards" hands over the parsed book instead of asking for the file
 * again. In memory only: nothing about a book is stored, and chapter text never travels as a navigation
 * argument. Smart Extract takes the offer as soon as it sees it, which empties this.
 */
class BookHandoff {
    /** A book to generate cards from. [bookName] is the name of its deck, as the user chose it. */
    class Offer(val book: BookSource, val bookName: String, val chapterId: Int? = null)

    private val _offer = MutableStateFlow<Offer?>(null)
    val offer: StateFlow<Offer?> = _offer.asStateFlow()

    fun offer(book: BookSource, bookName: String, chapterId: Int? = null) {
        _offer.value = Offer(book, bookName, chapterId)
    }

    /** Smart Extract has [offer]; a newer one stays. */
    fun take(offer: Offer) {
        _offer.compareAndSet(offer, null)
    }
}
