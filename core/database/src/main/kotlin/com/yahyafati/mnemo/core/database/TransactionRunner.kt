package com.yahyafati.mnemo.core.database

import androidx.room.withTransaction
import javax.inject.Inject

/** Runs several DAO calls in one database transaction, without exposing the database itself. */
interface TransactionRunner {
    suspend operator fun <T> invoke(block: suspend () -> T): T
}

class RoomTransactionRunner @Inject constructor(
    private val database: MnemoDatabase,
) : TransactionRunner {
    override suspend fun <T> invoke(block: suspend () -> T): T = database.withTransaction { block() }
}
