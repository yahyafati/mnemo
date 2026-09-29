package com.yahyafati.mnemo.core.model

import java.time.Instant

/**
 * A stored media file (image or sound). Files are content-addressed: [id] is the SHA-256 of the
 * bytes, so the same image imported twice is stored once and has the same id on every device.
 *
 * @property name the original file name, e.g. "cell.png". Used when exporting to Anki, whose
 *   fields refer to media by name.
 */
data class Media(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val createdAt: Instant,
)

/**
 * How card Markdown refers to media: `media:<sha256>`, as in `![cell](media:ab12…)` or
 * `[sound:media:ab12…]`. Referring by hash rather than name keeps references unambiguous when
 * decks from different sources use the same file names.
 */
object MediaRef {
    const val SCHEME = "media:"

    /** Subdirectory of the app's files directory that holds media, one file per hash. */
    const val DIRECTORY = "media"

    private val HASH = Regex("[0-9a-f]{64}")
    private val REFERENCE = Regex("""media:([0-9a-f]{64})""")

    fun of(hash: String): String {
        require(HASH.matches(hash)) { "Not a SHA-256 hex digest: $hash" }
        return SCHEME + hash
    }

    /** The hash in [src], or null if [src] is not a media reference. */
    fun hashOf(src: String): String? =
        src.removePrefix(SCHEME).takeIf { src.startsWith(SCHEME) && HASH.matches(it) }

    /** Every media hash referenced anywhere in [text] (a note field). */
    fun referencedHashes(text: String): Set<String> =
        if (SCHEME in text) REFERENCE.findAll(text).map { it.groupValues[1] }.toSet() else emptySet()

    /** A MIME type for [fileName] from its extension, for the few types cards use. */
    fun mimeTypeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "bmp" -> "image/bmp"
        "mp3" -> "audio/mpeg"
        "ogg", "oga" -> "audio/ogg"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        else -> "application/octet-stream"
    }
}
