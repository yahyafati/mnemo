package com.yahyafati.mnemo.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A stored media file. Unlike other tables the id is not a random UUID but the SHA-256 of the
 * file's bytes: media is content-addressed (`filesDir/media/<id>`), so the same file has the same
 * id everywhere, which is just as safe for a future sync.
 */
@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey val id: String,
    /** Original file name, used when exporting to Anki. */
    val name: String,
    val mimeType: String,
    val size: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
