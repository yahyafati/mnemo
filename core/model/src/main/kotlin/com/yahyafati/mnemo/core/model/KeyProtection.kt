package com.yahyafati.mnemo.core.model

/**
 * Where the key that encrypts the AI providers' API keys is kept (ADR 0005, 0010). Settings says so,
 * because the answer decides how much a stolen copy of the collection's folder gives away.
 */
enum class KeyProtection {
    /** The phone's hardware-backed keystore (Android). */
    PlatformKeystore,

    /** Windows Credential Manager, macOS Keychain or the Secret Service (GNOME Keyring, KWallet). */
    OsKeychain,

    /** A file only this user can read, because the computer has no usable keychain. */
    KeyFile,
}
