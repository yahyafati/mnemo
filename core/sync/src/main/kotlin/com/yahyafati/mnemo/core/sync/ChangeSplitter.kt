package com.yahyafati.mnemo.core.sync

import com.yahyafati.mnemo.core.sync.format.Change
import com.yahyafati.mnemo.core.sync.format.ChangeBatch
import com.yahyafati.mnemo.core.sync.format.FileCodec
import com.yahyafati.mnemo.core.sync.format.SyncFormat

/** Cuts a list of changes into change files that stay under a size, and turns a file's contents to and from JSON. */
internal object ChangeSplitter {
    /** Changes are first grouped by their JSON size, so sizing a huge import doesn't compress it all at once. */
    private const val GROUP_JSON_CHARS = 8_000_000L

    /** What the envelope adds around the compressed JSON: header, checksum, nonce, tag, and a little slack. */
    private const val ENVELOPE_SLACK = 256

    fun encode(batch: ChangeBatch): ByteArray = SyncFormat.json.encodeToString(ChangeBatch.serializer(), batch).toByteArray()

    fun decode(payload: ByteArray): ChangeBatch = SyncFormat.json.decodeFromString(ChangeBatch.serializer(), payload.decodeToString())

    /**
     * Consecutive groups of [changes] whose files are each at most about [maxFileBytes]. A single change bigger
     * than that stays alone in its file: it can't be cut.
     */
    fun split(changes: List<Change>, maxFileBytes: Int): List<List<Change>> {
        require(maxFileBytes > ENVELOPE_SLACK) { "maxFileBytes is too small" }
        if (changes.isEmpty()) return emptyList()
        val groups = ArrayList<List<Change>>()
        var current = ArrayList<Change>()
        var size = 0L
        for (change in changes) {
            val chars = SyncFormat.json.encodeToString(Change.serializer(), change).length
            if (current.isNotEmpty() && size + chars > GROUP_JSON_CHARS) {
                groups += current
                current = ArrayList()
                size = 0
            }
            current += change
            size += chars
        }
        if (current.isNotEmpty()) groups += current
        return groups.flatMap { fit(it, maxFileBytes) }
    }

    private fun fit(group: List<Change>, maxFileBytes: Int): List<List<Change>> {
        if (group.size == 1 || compressedFileSize(group) <= maxFileBytes) return listOf(group)
        val middle = group.size / 2
        return fit(group.subList(0, middle), maxFileBytes) + fit(group.subList(middle, group.size), maxFileBytes)
    }

    private fun compressedFileSize(group: List<Change>): Int =
        FileCodec.compressedSize(encode(ChangeBatch.of("size", 0, group))) + ENVELOPE_SLACK
}
