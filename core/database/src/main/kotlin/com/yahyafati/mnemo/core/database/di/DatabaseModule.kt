package com.yahyafati.mnemo.core.database.di

import com.yahyafati.mnemo.core.database.DatabaseSnapshot
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.RoomTransactionRunner
import com.yahyafati.mnemo.core.database.TransactionRunner
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Room, its DAOs and the transaction runner. Needs a `Context` (Koin's `androidContext`).
 * Tests replace [MnemoDatabase] with an in-memory one.
 */
val databaseModule = module {
    single { MnemoDatabase.build(get()) }
    factory { get<MnemoDatabase>().deckDao() }
    factory { get<MnemoDatabase>().noteDao() }
    factory { get<MnemoDatabase>().cardDao() }
    factory { get<MnemoDatabase>().reviewLogDao() }
    factory { get<MnemoDatabase>().mediaDao() }
    factory { get<MnemoDatabase>().aiProviderDao() }
    factory { get<MnemoDatabase>().statsDao() }

    factoryOf(::RoomTransactionRunner) bind TransactionRunner::class
    factoryOf(::DatabaseSnapshot)
}
