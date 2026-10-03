package com.yahyafati.mnemo.core.data.backup

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver

internal actual fun platformSqliteDriver(): SQLiteDriver = AndroidSQLiteDriver()
