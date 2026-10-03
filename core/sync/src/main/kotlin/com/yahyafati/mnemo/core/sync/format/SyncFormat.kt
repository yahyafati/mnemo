package com.yahyafati.mnemo.core.sync.format

import kotlinx.serialization.json.Json

/** The format this version reads and writes. A device refuses a higher number and never rewrites such files. */
object SyncFormat {
    const val VERSION = 1

    /** About how big a change file may get, compressed and encrypted: a big Anki import becomes several files. */
    const val MAX_CHANGE_FILE_BYTES = 2_000_000

    /** What a file may decompress to; a zip bomb in a shared folder must not take the app's memory. */
    const val MAX_PAYLOAD_BYTES = 256L * 1024 * 1024

    /** JSON for every file. Unknown keys are ignored, so a later version can add fields without a new format. */
    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }
}
