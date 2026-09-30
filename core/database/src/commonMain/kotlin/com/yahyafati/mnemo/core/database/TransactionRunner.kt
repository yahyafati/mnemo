package com.yahyafati.mnemo.core.database

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/** Runs several DAO calls in one database transaction, without exposing the database itself. */
interface TransactionRunner {
    suspend operator fun <T> invoke(block: suspend () -> T): T
}

class RoomTransactionRunner(
    private val database: MnemoDatabase,
) : TransactionRunner {
    // DAO calls made inside the block find this connection through the coroutine context.
    override suspend fun <T> invoke(block: suspend () -> T): T =
        database.useWriterConnection { it.immediateTransaction { block() } }
}
