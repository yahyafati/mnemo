package com.yahyafati.mnemo.core.security

import com.github.javakeyring.Keyring
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64
import java.util.UUID

/**
 * The OS keychain through java-keyring: Windows Credential Manager, macOS Keychain, or the Secret
 * Service on Linux. The key is one entry ([service], [account]), stored as Base64 text.
 */
class KeyringKeyStorage(
    private val service: String = SERVICE,
    private val account: String = ACCOUNT,
) : KeyStorage {
    override fun load(): ByteArray? = withKeyring { keyring ->
        Base64.getDecoder().decode(keyring.getPassword(service, account))
    }

    override fun works(): Boolean {
        val probe = "probe-${UUID.randomUUID()}"
        return withKeyring { keyring ->
            keyring.setPassword(service, probe, probe)
            val readBack = keyring.getPassword(service, probe)
            runCatching { keyring.deletePassword(service, probe) }
            readBack == probe
        } ?: false
    }

    override fun save(key: ByteArray) {
        val saved = withKeyring { keyring ->
            keyring.setPassword(service, account, Base64.getEncoder().encodeToString(key))
            true
        }
        if (saved != true) throw IOException("The keychain didn't take the key")
    }

    /** Removes the entry (tests). */
    fun delete() {
        withKeyring { it.deletePassword(service, account) }
    }

    /** Runs [block] on a keyring, or returns null if there is none or [block] fails. */
    private fun <T> withKeyring(block: (Keyring) -> T): T? = try {
        Keyring.create().use(block)
    } catch (e: Exception) {
        null
    } catch (e: LinkageError) {
        // A native library or D-Bus class that isn't there on this machine.
        null
    }

    companion object {
        const val SERVICE = "Mnemo"
        const val ACCOUNT = "secrets-key"
    }
}

/**
 * The key in a file. Meant for computers without a keychain, so its protection is the file's
 * permissions: readable by the owner only where the file system has POSIX permissions, and in the
 * user's own profile on Windows.
 */
class KeyFileStorage(private val file: File) : KeyStorage {
    override fun load(): ByteArray? = try {
        file.takeIf { it.isFile }?.readBytes()?.takeIf { it.size == KEY_BYTES }
    } catch (e: IOException) {
        null
    }

    override fun works(): Boolean = true

    override fun save(key: ByteArray) {
        val directory = file.absoluteFile.parentFile ?: throw IOException("No directory for ${file.path}")
        directory.mkdirs()
        val temp = File(directory, file.name + ".tmp")
        temp.delete()
        try {
            // Created readable by the owner alone (not chmod-ed after the fact, so no window where it is open).
            Files.createFile(temp.toPath(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        } catch (e: UnsupportedOperationException) {
            Files.createFile(temp.toPath())
        }
        try {
            temp.writeBytes(key)
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            temp.delete()
            throw e
        }
    }

    private companion object {
        const val KEY_BYTES = 32
    }
}
