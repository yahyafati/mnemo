package com.yahyafati.mnemo.core.database

import androidx.room.useReaderConnection
import com.yahyafati.mnemo.core.database.desktop.build

actual abstract class PlatformTest actual constructor()

actual fun inMemoryDatabase(): MnemoDatabase = MnemoDatabase.build(file = null)

actual suspend fun MnemoDatabase.queryPlan(sql: String): List<String> = useReaderConnection { connection ->
    connection.usePrepared("EXPLAIN QUERY PLAN $sql") { statement ->
        // Columns: id, parent, notused, detail.
        buildList { while (statement.step()) add(statement.getText(3)) }
    }
}
