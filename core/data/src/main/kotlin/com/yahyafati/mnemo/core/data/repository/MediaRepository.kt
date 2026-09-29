package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.model.Media
import java.io.File
import java.io.InputStream

/** Images and sounds used on cards, stored by content hash (ARCHITECTURE §6). */
interface MediaRepository {
    /** The directory holding one file per hash. */
    val directory: File

    /**
     * Copies [input] into storage and returns its record. Storing the same bytes again returns the
     * same media (and keeps the first name). The caller closes [input].
     */
    suspend fun store(input: InputStream, name: String): Media

    suspend fun getAll(): List<Media>

    /** The file for media [hash]; it may not exist. */
    fun file(hash: String): File

    /**
     * Deletes media that no live note refers to, and stray files with no record. Media younger than
     * a day is kept, so an import or an edit in progress never loses a file. Returns how many
     * files were removed.
     */
    suspend fun collectGarbage(): Int
}
