package com.yahyafati.mnemo.core.data.desktop

import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.datastore.di.DataStoreFiles
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopAppDirectoriesTest {
    private fun default(os: String, env: Map<String, String> = emptyMap(), home: String = "/home/ada") =
        DesktopAppDirectories.default(env) { key -> mapOf("os.name" to os, "user.home" to home)[key] }

    @Test
    fun macOsKeepsDataInApplicationSupport() {
        val directories = default("Mac OS X", home = "/Users/ada")

        assertEquals(File("/Users/ada/Library/Application Support/Mnemo"), directories.files)
        assertEquals(File("/Users/ada/Library/Caches/Mnemo"), directories.cache)
    }

    @Test
    fun windowsKeepsDataInLocalAppData() {
        val directories = default("Windows 11", mapOf("LOCALAPPDATA" to "C:\\Users\\ada\\AppData\\Local"), home = "C:\\Users\\ada")

        assertEquals(File("C:\\Users\\ada\\AppData\\Local", "Mnemo"), directories.files)
        assertEquals(File("C:\\Users\\ada\\AppData\\Local", "Mnemo/Cache"), directories.cache)
        // Without the variable it is where Windows puts it anyway.
        assertEquals(File("C:\\Users\\ada", "AppData/Local/Mnemo"), default("Windows 10", home = "C:\\Users\\ada").files)
    }

    @Test
    fun linuxFollowsTheXdgDirectories() {
        assertEquals(File("/home/ada/.local/share/mnemo"), default("Linux").files)
        assertEquals(File("/home/ada/.cache/mnemo"), default("Linux").cache)

        val moved = default("Linux", mapOf("XDG_DATA_HOME" to "/data", "XDG_CACHE_HOME" to "/cache"))
        assertEquals(File("/data/mnemo"), moved.files)
        assertEquals(File("/cache/mnemo"), moved.cache)
        // An empty variable counts as unset, as the XDG specification says.
        assertEquals(File("/home/ada/.local/share/mnemo"), default("Linux", mapOf("XDG_DATA_HOME" to "")).files)
    }

    @Test
    fun aVariableMovesTheWholeCollection() {
        val directories = default("Mac OS X", mapOf(DesktopAppDirectories.DATA_DIR_VARIABLE to "/portable/mnemo"))

        assertEquals(File("/portable/mnemo"), directories.files)
        assertEquals(File("/portable/mnemo/cache"), directories.cache)
    }

    @Test
    fun theLayoutIsWhatBackupsRelyOn() {
        val directories = DesktopAppDirectories(File("/data/mnemo"))

        assertEquals(File("/data/mnemo/mnemo.db"), directories.databaseFile(MnemoDatabase.NAME))
        assertEquals(File("/data/mnemo/datastore/user_preferences.preferences_pb"), directories.dataStoreFile(DataStoreFiles.USER_PREFERENCES))
        assertEquals(File("/data/mnemo/media"), directories.media)
        assertEquals(File("/data/mnemo/restore-pending"), directories.restoreStaging)
        // Secrets are in a directory of their own, which no backup reads.
        assertEquals(File("/data/mnemo/secrets"), directories.secrets)
        for (backedUp in listOf(directories.media, directories.databaseFile(MnemoDatabase.NAME), directories.dataStoreFile("x"))) {
            assertFalse(backedUp.startsWith(directories.secrets))
            assertFalse(directories.secrets.startsWith(backedUp))
        }
    }
}
