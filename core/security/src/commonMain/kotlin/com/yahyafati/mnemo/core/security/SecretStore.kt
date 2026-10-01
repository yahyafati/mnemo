package com.yahyafati.mnemo.core.security

import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.model.KeyProtection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.MessageDigest

/** What [SecretStore.get] found. */
sealed interface StoredSecret {
    data object Missing : StoredSecret

    data class Present(val value: String) : StoredSecret

    /** Stored, but it can't be decrypted (the Keystore key is gone, or the file is damaged). */
    data object Unreadable : StoredSecret
}

/**
 * Secrets by id (an API key per provider id). Values are encrypted with [SecretCipher] at rest and
 * only decrypted by [get], in memory, for the request that needs them.
 */
interface SecretStore {
    /** Where the key that encrypts these secrets is kept. */
    val protection: KeyProtection get() = KeyProtection.PlatformKeystore

    suspend fun put(id: String, secret: String)

    suspend fun get(id: String): StoredSecret

    suspend fun contains(id: String): Boolean

    suspend fun remove(id: String)

    /** Deletes every secret whose id isn't in [ids] (e.g. left behind by a restore). */
    suspend fun retainOnly(ids: Set<String>)
}

/**
 * One encrypted file per secret in [AppDirectories.secrets], named by the SHA-256 of the id. That
 * directory is outside the database, the preferences and the media folder, so no Mnemo backup or
 * export can contain it (on Android it is in `noBackupFilesDir`, which Auto Backup never copies).
 */
class FileSecretStore internal constructor(
    private val directory: () -> File,
    private val cipher: SecretCipher,
    private val ioDispatcher: CoroutineDispatcher,
) : SecretStore {
    constructor(
        directories: AppDirectories,
        cipher: SecretCipher,
        ioDispatcher: CoroutineDispatcher,
    ) : this({ directories.secrets }, cipher, ioDispatcher)

    override val protection: KeyProtection get() = cipher.protection

    override suspend fun put(id: String, secret: String): Unit = withContext(ioDispatcher) {
        val dir = directory().apply { mkdirs() }
        val sealed = cipher.encrypt(secret.toByteArray(Charsets.UTF_8), id.toByteArray(Charsets.UTF_8))
        val temp = File(dir, name(id) + ".tmp")
        temp.writeBytes(sealed)
        try {
            // `File.renameTo` won't replace an existing file on Windows, so saving a key twice failed there.
            Files.move(temp.toPath(), File(dir, name(id)).toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            temp.delete()
            throw IOException("Can't save the secret", e)
        }
    }

    override suspend fun get(id: String): StoredSecret = withContext(ioDispatcher) {
        val file = File(directory(), name(id))
        if (!file.exists()) return@withContext StoredSecret.Missing
        try {
            StoredSecret.Present(cipher.decrypt(file.readBytes(), id.toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8))
        } catch (e: GeneralSecurityException) {
            StoredSecret.Unreadable
        } catch (e: IOException) {
            StoredSecret.Unreadable
        }
    }

    override suspend fun contains(id: String): Boolean = withContext(ioDispatcher) { File(directory(), name(id)).exists() }

    override suspend fun remove(id: String) {
        withContext(ioDispatcher) { File(directory(), name(id)).delete() }
    }

    override suspend fun retainOnly(ids: Set<String>) {
        withContext(ioDispatcher) {
            val keep = ids.mapTo(HashSet(), ::name)
            directory().listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
        }
    }

    private fun name(id: String): String =
        MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
