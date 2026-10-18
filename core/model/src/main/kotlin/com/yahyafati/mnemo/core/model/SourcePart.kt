package com.yahyafati.mnemo.core.model

/**
 * One part of a Smart Extract source, sent in one request (ADR 0015, C2). [path] says where [text] is: the source's
 * root (the book and the chapter, the page's or the document's title; nothing for pasted text), then the headings
 * that hold all of it, outermost first. [continued]: the part goes on with a section an earlier part started.
 * [overlap] is the end of the part before, sent as context only (ADR 0015, C3); empty when the setting or the part's
 * place gives it none. It is not part of [text], so it doesn't count against the part's size.
 *
 * The queue keeps each card's part, so Regenerate asks with the same text and path as the first request.
 */
data class SourcePart(
    val text: String,
    val path: List<String> = emptyList(),
    val continued: Boolean = false,
    val overlap: String = "",
)
