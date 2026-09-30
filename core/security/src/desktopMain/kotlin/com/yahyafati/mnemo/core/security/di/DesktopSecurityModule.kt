package com.yahyafati.mnemo.core.security.di

import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.security.DesktopSecretCipher
import com.yahyafati.mnemo.core.security.KeyFileStorage
import com.yahyafati.mnemo.core.security.KeyringKeyStorage
import com.yahyafati.mnemo.core.security.SecretCipher
import org.koin.core.scope.Scope
import java.io.File

/**
 * The OS keychain holds the key. Without one (some Linux setups), the key is a file next to the
 * secrets directory, not in it: the store deletes anything in there it doesn't know.
 */
internal actual fun Scope.createSecretCipher(): SecretCipher {
    val secrets = get<AppDirectories>().secrets
    return DesktopSecretCipher(
        keychain = KeyringKeyStorage(),
        keyFile = KeyFileStorage(File(secrets.absoluteFile.parentFile, "secrets.key")),
    )
}
