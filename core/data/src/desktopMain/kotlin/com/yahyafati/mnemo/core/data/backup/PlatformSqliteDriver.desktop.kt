package com.yahyafati.mnemo.core.data.backup

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

internal actual fun platformSqliteDriver(): SQLiteDriver = BundledSQLiteDriver()
