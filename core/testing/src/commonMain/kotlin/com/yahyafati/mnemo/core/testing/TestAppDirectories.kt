package com.yahyafati.mnemo.core.testing

import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.model.MediaRef
import java.io.File
import java.nio.file.Files

/**
 * [AppDirectories] in a temporary directory, laid out like the desktop's: the same on every
 * platform, so shared tests need no `Context`. [delete] removes everything.
 */
class TestAppDirectories(
    val root: File = Files.createTempDirectory("mnemo-test").toFile(),
) : AppDirectories {
    override val files: File get() = root

    override val cache: File get() = File(root, "cache")

    override val media: File get() = File(root, MediaRef.DIRECTORY)

    override val secrets: File get() = File(root, "secrets")

    override val restoreStaging: File get() = File(root, "restore-pending")

    override fun databaseFile(name: String): File = File(root, name)

    override fun dataStoreFile(name: String): File = File(File(root, "datastore"), "$name.preferences_pb")

    fun delete() {
        root.deleteRecursively()
    }
}
