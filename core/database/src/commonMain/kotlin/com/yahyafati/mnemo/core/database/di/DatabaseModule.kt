package com.yahyafati.mnemo.core.database.di

import com.yahyafati.mnemo.core.database.DatabaseSnapshot
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.RoomTransactionRunner
import com.yahyafati.mnemo.core.database.TransactionRunner
import com.yahyafati.mnemo.core.database.sync.SyncRows
import org.koin.core.module.dsl.factoryOf
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.onClose
import org.koin.dsl.module

/**
 * Room, its DAOs and the transaction runner. Where the file lives is the platform's business:
 * Android needs a `Context` (Koin's `androidContext`), desktop the `AppDirectories`.
 * Tests replace [MnemoDatabase] with an in-memory one.
 */
val databaseModule = module {
    // Closed when Koin stops (desktop restarts and tests reopen the same file).
    single { createDatabase() } onClose { it?.close() }
    factory { get<MnemoDatabase>().deckDao() }
    factory { get<MnemoDatabase>().noteDao() }
    factory { get<MnemoDatabase>().cardDao() }
    factory { get<MnemoDatabase>().reviewLogDao() }
    factory { get<MnemoDatabase>().mediaDao() }
    factory { get<MnemoDatabase>().aiProviderDao() }
    factory { get<MnemoDatabase>().statsDao() }
    factory { get<MnemoDatabase>().aiAnswerDao() }
    factory { get<MnemoDatabase>().syncDao() }
    factory { get<MnemoDatabase>().syncMergeDao() }
    factory { SyncRows(get<MnemoDatabase>()) }

    factoryOf(::RoomTransactionRunner) bind TransactionRunner::class
    factoryOf(::DatabaseSnapshot)
}

/** The database file the app uses, built on this platform's driver. */
internal expect fun Scope.createDatabase(): MnemoDatabase
