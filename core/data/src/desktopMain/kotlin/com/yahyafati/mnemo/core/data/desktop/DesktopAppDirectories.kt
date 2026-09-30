package com.yahyafati.mnemo.core.data.desktop

import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.model.MediaRef
import java.io.File

/**
 * [AppDirectories] on a computer (ADR 0010). [files] holds the collection:
 *
 * ```
 * mnemo.db (+ -wal, -shm)      the Room database, the same file format as on Android
 * datastore/user_preferences.preferences_pb
 * media/<sha256>
 * secrets/                      encrypted API keys, never read by a backup or an export
 * restore-pending/              a restore staged for the next start
 * ```
 *
 * [cache] is scratch space for imports, exports and backups.
 */
class DesktopAppDirectories(
    override val files: File,
    override val cache: File = File(files, "cache"),
) : AppDirectories {
    override val media: File get() = File(files, MediaRef.DIRECTORY)

    override val secrets: File get() = File(files, "secrets")

    override val restoreStaging: File get() = File(files, "restore-pending")

    override fun databaseFile(name: String): File = File(files, name)

    override fun dataStoreFile(name: String): File = File(File(files, "datastore"), "$name.preferences_pb")

    companion object {
        /** Environment variable that moves the whole collection elsewhere (development, portable use). */
        const val DATA_DIR_VARIABLE = "MNEMO_DATA_DIR"

        /**
         * Where this OS keeps application data: `~/Library/Application Support/Mnemo` on macOS,
         * `%LOCALAPPDATA%\Mnemo` on Windows, and `$XDG_DATA_HOME/mnemo` (`~/.local/share/mnemo`) on
         * Linux and other Unix systems. [environment] and [properties] are the process's, unless a test
         * passes its own.
         */
        fun default(
            environment: Map<String, String> = System.getenv(),
            properties: (String) -> String? = System::getProperty,
        ): DesktopAppDirectories {
            val home = File(properties("user.home") ?: ".")
            environment[DATA_DIR_VARIABLE]?.takeIf { it.isNotBlank() }?.let { return DesktopAppDirectories(File(it)) }
            val os = properties("os.name").orEmpty().lowercase()
            return when {
                "mac" in os || "darwin" in os -> DesktopAppDirectories(
                    files = File(home, "Library/Application Support/Mnemo"),
                    cache = File(home, "Library/Caches/Mnemo"),
                )
                "win" in os -> {
                    val local = environment["LOCALAPPDATA"]?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, "AppData/Local")
                    DesktopAppDirectories(files = File(local, "Mnemo"), cache = File(local, "Mnemo/Cache"))
                }
                else -> {
                    val data = environment["XDG_DATA_HOME"]?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, ".local/share")
                    val cache = environment["XDG_CACHE_HOME"]?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, ".cache")
                    DesktopAppDirectories(files = File(data, "mnemo"), cache = File(cache, "mnemo"))
                }
            }
        }
    }
}
