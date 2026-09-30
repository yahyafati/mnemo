package com.yahyafati.mnemo.core.database

/**
 * Base class of the tests that run on both targets: Robolectric on Android (the framework SQLite
 * needs a `Context`), plain JUnit on desktop.
 */
expect abstract class PlatformTest()

/** A fresh in-memory database, built the way the platform builds the real one. */
expect fun inMemoryDatabase(): MnemoDatabase

/** SQLite's `EXPLAIN QUERY PLAN` rows (their `detail` column) for [sql] on this schema. */
expect suspend fun MnemoDatabase.queryPlan(sql: String): List<String>
