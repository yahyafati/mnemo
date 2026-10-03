package com.yahyafati.mnemo.core.sync.format

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * One change to one row. [clock] is the hybrid logical clock value the change was stamped with when it was packed
 * (S3); the rows' table names and the meaning of the fields are the merge engine's, not this module's.
 */
@Serializable
sealed class Change {
    abstract val table: String

    /** The row's key; a composite key joins its parts with `/` (S1). */
    abstract val id: String
    abstract val clock: Long

    /** [fields] are the columns that changed (all of them for a new row), by name, as JSON values. */
    @Serializable
    @SerialName("put")
    data class Put(
        override val table: String,
        override val id: String,
        override val clock: Long,
        val fields: Map<String, JsonElement>,
    ) : Change()

    /** The row is gone, for good. (Soft deletes are a `deletedAt` field in a [Put].) */
    @Serializable
    @SerialName("delete")
    data class Delete(
        override val table: String,
        override val id: String,
        override val clock: Long,
    ) : Change()
}

/**
 * A change file's contents: the changes of one device, in the order it made them. [seq] numbers a device's files
 * 1, 2, 3 …; [clockFrom]..[clockTo] is the range of its changes' clocks, so a reader can order files without
 * opening every change. The device id and the seq are also in the file's name, and a file where they differ is
 * refused.
 */
@Serializable
data class ChangeBatch(
    val deviceId: String,
    val seq: Long,
    val clockFrom: Long,
    val clockTo: Long,
    val changes: List<Change>,
) {
    companion object {
        fun of(deviceId: String, seq: Long, changes: List<Change>): ChangeBatch {
            require(changes.isNotEmpty()) { "A change file holds at least one change" }
            return ChangeBatch(deviceId, seq, changes.minOf { it.clock }, changes.maxOf { it.clock }, changes)
        }
    }
}
