package com.yahyafati.mnemo.core.data.backup

import androidx.sqlite.SQLiteDriver

/** The SQLite driver that reads the collection's file before Room has opened it: the framework's on Android, the bundled one on desktop. */
internal expect fun platformSqliteDriver(): SQLiteDriver
