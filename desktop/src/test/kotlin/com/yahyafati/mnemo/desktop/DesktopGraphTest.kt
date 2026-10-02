package com.yahyafati.mnemo.desktop

import androidx.lifecycle.SavedStateHandle
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
                checkModules {
                    // ViewModels read their navigation arguments from the handle.
                    for (name in VIEW_MODELS_WITH_ARGUMENTS) withParameter(Class.forName(name).kotlin) { SavedStateHandle() }
                }
            }.close()
        } finally {
            root.deleteRecursively()
        }
    }

    private companion object {
        val VIEW_MODELS_WITH_ARGUMENTS = listOf(
            "com.yahyafati.mnemo.feature.study.StudyViewModel",
            "com.yahyafati.mnemo.feature.create.NoteEditorViewModel",
            "com.yahyafati.mnemo.feature.create.BookImportViewModel",
            "com.yahyafati.mnemo.feature.create.coauthor.CoAuthorViewModel",
            "com.yahyafati.mnemo.feature.browse.BrowseViewModel",
            "com.yahyafati.mnemo.feature.settings.ai.ProviderEditorViewModel",
        )
    }
}
