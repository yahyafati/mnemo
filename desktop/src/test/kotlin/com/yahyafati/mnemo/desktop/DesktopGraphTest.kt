package com.yahyafati.mnemo.desktop

import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.data.desktop.DesktopAppDirectories
import com.yahyafati.mnemo.core.security.SecretCipher
import com.yahyafati.mnemo.core.testing.security.SoftwareSecretCipher
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.check.checkModules
import java.nio.file.Files
import kotlin.test.Test

/**
 * The desktop's counterpart of the Android app's `DependencyGraphTest`: every definition of
 * [desktopModules] resolves (Koin does not check at compile time, as Hilt did). Only the data
 * directory and the cipher, which would use this machine's keychain, are replaced.
 */
class DesktopGraphTest {
    @Test
    fun everyDefinitionResolves() {
        val root = Files.createTempDirectory("mnemo-graph").toFile()
        try {
            koinApplication {
                modules(
                    desktopModules + module {
                        single<AppDirectories> { DesktopAppDirectories(root) }
                        single<SecretCipher> { SoftwareSecretCipher() }
                    },
                )
                checkModules()
            }.close()
        } finally {
            root.deleteRecursively()
        }
    }
}
