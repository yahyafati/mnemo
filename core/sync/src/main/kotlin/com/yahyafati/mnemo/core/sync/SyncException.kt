package com.yahyafati.mnemo.core.sync

import java.io.IOException

/**
 * Everything that can go wrong while talking to a sync location. A store reports the first five (the four the
 * roadmap names, plus a file that exists already); the format layer reports the others. All are [IOException]s,
 * so a caller that doesn't care which can catch one type, and a caller that does can `when` over the sealed
 * class.
 */
sealed class SyncException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** No network, or the server can't be reached. Retry later. */
class SyncOfflineException(message: String, cause: Throwable? = null) : SyncException(message, cause)

/** The location refuses us: a revoked folder permission, an expired token, a wrong password. Ask the user. */
class SyncAuthException(message: String, cause: Throwable? = null) : SyncException(message, cause)

/** There is no room left on the location (a full disk or a full Drive). */
class SyncQuotaException(message: String, cause: Throwable? = null) : SyncException(message, cause)

/** The file, or the location itself, isn't there. */
class SyncNotFoundException(message: String, cause: Throwable? = null) : SyncException(message, cause)

/** [SyncStore.write] was asked to create a file that exists. A name always means one content, so this is final. */
class SyncAlreadyExistsException(message: String) : SyncException(message)

/** Any other failure to read or write. */
class SyncIoException(message: String, cause: Throwable? = null) : SyncException(message, cause)

/**
 * A file that can't be trusted: too short, the wrong checksum, not what its name says, or a downgraded
 * (unencrypted) file in an encrypted location. A reader **ignores** such a file and never applies it.
 *
 * [maybeIncomplete] is true when the file is shorter than its header says, which is what a write that is still
 * in progress, or that a crash cut off, looks like: it may be whole the next time. The other cases won't heal.
 */
class SyncCorruptException(message: String, val maybeIncomplete: Boolean = false) : SyncException(message)

/**
 * The file was written by a newer version of Mnemo. Stop syncing with "Update Mnemo on this device to keep
 * syncing", and rewrite nothing this version can't read.
 */
class SyncUnsupportedVersionException(val found: Int, val supported: Int) :
    SyncException("Sync data is in format $found; this version of Mnemo reads up to $supported")

/** The location is encrypted and the passphrase is missing or wrong. */
class SyncPassphraseException(val required: Boolean) :
    SyncException(if (required) "This sync location is encrypted: enter its passphrase" else "That passphrase is wrong")
