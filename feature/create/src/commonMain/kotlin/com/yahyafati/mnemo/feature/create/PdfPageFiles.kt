package com.yahyafati.mnemo.feature.create

import java.io.File

/**
 * Where the screen gets pictures of the open PDF's pages (Cards from page images, docs/pdf/ROADMAP.md, P6). Both are files the
 * page renderer made in the cache, rendered once; null when the page has nothing on it, can't be drawn, or the PDF is closed.
 * Blocking work stays behind these two calls, so the screen can ask for the pages it shows as it scrolls.
 */
interface PdfPageFiles {
    /** A small picture of [page] (1-based) for the grid. */
    suspend fun thumbnail(page: Int): File?

    /** [page] at the quality its images are sent at: what the model sees. */
    suspend fun page(page: Int): File?

    companion object {
        /** Shows nothing: previews, and tests that don't draw pages. */
        val None: PdfPageFiles = object : PdfPageFiles {
            override suspend fun thumbnail(page: Int): File? = null

            override suspend fun page(page: Int): File? = null
        }
    }
}
