package com.yahyafati.mnemo.core.database.android

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.yahyafati.mnemo.core.database.MnemoDatabase

/**
 * Builds the database in the app's database directory, with the framework's SQLite (no native
 * library of our own). [name] null gives an in-memory database (tests).
 */
fun MnemoDatabase.Companion.build(context: Context, name: String? = NAME): MnemoDatabase {
    val builder = if (name == null) {
        Room.inMemoryDatabaseBuilder<MnemoDatabase>(context)
    } else {
        Room.databaseBuilder<MnemoDatabase>(context, context.getDatabasePath(name).absolutePath)
    }
    return configure(builder, AndroidSQLiteDriver())
}
