package com.yahyafati.mnemo.core.sync.format

import com.yahyafati.mnemo.core.sync.SyncCorruptException
import com.yahyafati.mnemo.core.sync.SyncUnsupportedVersionException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * `sync.json`, written once by the device that creates the location and never encrypted (a new device needs it to
 * learn how to unlock the rest). [encryption] is null for an unencrypted location.
 */
@Serializable
data class SyncManifest(
    val formatVersion: Int = SyncFormat.VERSION,
    val collectionId: String,
    /** Epoch milliseconds of the creating device's wall clock; informative. */
    val createdAt: Long,
    val encryption: EncryptionParams? = null,
) {
    fun encode(): ByteArray = prettyJson.encodeToString(serializer(), this).toByteArray()

    companion object {
        private val prettyJson = Json(from = SyncFormat.json) { prettyPrint = true }

        /** Reads `sync.json`: [SyncUnsupportedVersionException] for a newer format, [SyncCorruptException] for junk. */
        fun decode(bytes: ByteArray): SyncManifest {
            val root: JsonObject = try {
                SyncFormat.json.parseToJsonElement(bytes.decodeToString()).jsonObject
            } catch (e: Exception) {
                throw SyncCorruptException("sync.json isn't readable: ${e.message}", maybeIncomplete = true)
            }
            // The version first: a newer format may look different in every other way.
            val version = try {
                root["formatVersion"]?.jsonPrimitive?.int
            } catch (e: Exception) {
                null
            } ?: throw SyncCorruptException("sync.json has no format version")
            if (version > SyncFormat.VERSION) throw SyncUnsupportedVersionException(version, SyncFormat.VERSION)
            val manifest = try {
                SyncFormat.json.decodeFromJsonElement(serializer(), root)
            } catch (e: Exception) {
                throw SyncCorruptException("sync.json isn't readable: ${e.message}")
            }
            manifest.encryption?.validate()
            return manifest
        }
    }
}
