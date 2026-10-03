package com.yahyafati.mnemo.core.data.sync

import androidx.room.useReaderConnection
import com.yahyafati.mnemo.core.database.MnemoDatabase

/** The first column of every row of [sql] as text (null stays "null"), for tests that look at tables the DAOs hide. */
internal suspend fun MnemoDatabase.queryStrings(sql: String): List<String> = useReaderConnection { connection ->
    connection.usePrepared(sql) { statement ->
        buildList { while (statement.step()) add(if (statement.isNull(0)) "null" else statement.getText(0)) }
    }
}

/** Every row of the synced tables' ids, as `table/id`. */
internal suspend fun MnemoDatabase.syncedRowIds(): Set<String> =
    listOf("decks", "note_types", "notes", "cards", "review_logs", "media")
        .flatMap { table -> queryStrings("SELECT id FROM $table").map { "$table/$it" } }
        .toSet() + queryStrings("SELECT noteId || '/' || kind FROM ai_answers").map { "ai_answers/$it" }
