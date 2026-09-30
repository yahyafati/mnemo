package com.yahyafati.mnemo.core.database.desktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.yahyafati.mnemo.core.database.MnemoDatabase
import java.io.File

/**
 * Builds the database in [file], with the bundled SQLite (the same engine version on every OS).
 * [file] null gives an in-memory database (tests).
 */
fun MnemoDatabase.Companion.build(file: File?): MnemoDatabase {
    val builder = if (file == null) {
        Room.inMemoryDatabaseBuilder<MnemoDatabase>()
    } else {
        file.absoluteFile.parentFile?.mkdirs()
        Room.databaseBuilder<MnemoDatabase>(file.absolutePath)
    }
    return configure(builder, BundledSQLiteDriver())
}
