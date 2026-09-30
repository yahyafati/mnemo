package com.yahyafati.mnemo.desktop

import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.data.desktop.DesktopAppDirectories
import com.yahyafati.mnemo.core.data.repository.DataTransferRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.data.repository.ReviewRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.domain.AnswerCardUseCase
import com.yahyafati.mnemo.core.domain.BuildStudyQueueUseCase
import com.yahyafati.mnemo.core.domain.UndoLastAnswerUseCase
import com.yahyafati.mnemo.core.model.ExportFormat
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.model.TransferState
import com.yahyafati.mnemo.core.security.SecretCipher
import com.yahyafati.mnemo.core.testing.security.SoftwareSecretCipher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.dsl.module
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A whole collection on the desktop (ROADMAP D4): an empty data directory, an Anki package
 * imported, cards studied through the use cases, a backup, a restore at the next start and an
 * export, all through the desktop graph (bundled SQLite, plain files, jobs on coroutines).
 */
class DesktopCollectionTest {
    private val root: File = Files.createTempDirectory("mnemo-desktop-collection").toFile()
    private val directories = DesktopAppDirectories(File(root, "data"), File(root, "cache"))
    @AfterTest
    fun deleteFiles() {
        root.deleteRecursively()
    }

    /** The desktop graph on [data] instead of this machine's data directory. */
    private fun open(data: DesktopAppDirectories = directories): CollectionSession {
        val testModule = module {
            single<AppDirectories> { data }
            // The real one would use this machine's keychain.
            single<SecretCipher> { SoftwareSecretCipher() }
        }
        return assertNotNull(openCollection(data, desktopModules + testModule), "another Mnemo holds the collection")
    }

    private suspend fun <T> Flow<TransferState<T>>.succeeded(): T =
        assertIs<TransferState.Succeeded<T>>(finished()).result

    private suspend fun <T> Flow<TransferState<T>>.finished(): TransferState<T> =
        withTimeout(60_000) { first { it is TransferState.Succeeded || it is TransferState.Failed } }

    private fun apkg(name: String) = File("../core/anki/src/test/resources/$name").also { check(it.exists()) { it.absolutePath } }

    @Test
    fun importStudyBackupRestoreAndExport() = runBlocking {
        val backup = File(root, "backup.zip")
        val export = File(root, "collection.apkg")
        val deckCount: Int
        val reviewsBeforeBackup: Int

        open().use { session ->
            val koin = session.koin
            val transfers = koin.get<DataTransferRepository>()

            // An empty data directory → an Anki package.
            assertTrue(koin.get<DeckRepository>().getDecks().isEmpty())
            transfers.startImport(apkg("modern.apkg").absolutePath)
            assertEquals(7, transfers.importState.succeeded().notes)
            deckCount = koin.get<DeckRepository>().getDecks().size
            assertTrue(deckCount > 0)
            assertTrue(File(directories.files, "mnemo.db").exists())

            // Study three cards, undo the last.
            val queue = koin.get<BuildStudyQueueUseCase>()()
            val answer = koin.get<AnswerCardUseCase>()
            var studying = queue.session
            val answers = buildList {
                repeat(3) {
                    val card = assertNotNull(studying.next(Instant.now()), "a card to study")
                    val given = queue.scheduler.answer(card.card, Rating.Good, Instant.now())
                    answer(given)
                    studying = studying.afterAnswer(given)
                    add(given)
                }
            }
            val reviews = koin.get<ReviewRepository>()
            assertEquals(3, reviews.getTodayCounts().total)
            koin.get<UndoLastAnswerUseCase>()(answers.last())
            reviewsBeforeBackup = reviews.getTodayCounts().total
            assertEquals(2, reviewsBeforeBackup)

            // A backup, then changes the restore has to undo.
            transfers.startBackup(backup.absolutePath)
            transfers.backupState.succeeded()
            assertTrue(backup.length() > 0)
            koin.get<DeckRepository>().saveDeck("Added after the backup")
            assertEquals(deckCount + 1, koin.get<DeckRepository>().getDecks().size)

            // Stage the restore; nothing changes until the next start.
            assertIs<MnemoResult.Success<*>>(transfers.stageRestore(backup.absolutePath))
            assertEquals(deckCount + 1, koin.get<DeckRepository>().getDecks().size)
        }

        // The next start applies it before the database opens.
        open().use { session ->
            val koin = session.koin
            assertEquals(deckCount, koin.get<DeckRepository>().getDecks().size)
            assertEquals(reviewsBeforeBackup, koin.get<ReviewRepository>().getTodayCounts().total)
            assertNull(koin.get<DeckRepository>().getDecks().firstOrNull { it.name == "Added after the backup" })

            // The whole collection out as an Anki package …
            val transfers = koin.get<DataTransferRepository>()
            transfers.startExport(export.absolutePath, ExportFormat.Apkg, null)
            transfers.exportState.succeeded()
            assertTrue(export.length() > 0)
        }

        // … that another collection (a second computer) imports.
        val second = DesktopAppDirectories(File(root, "second-data"), File(root, "second-cache"))
        open(second).use { session ->
            val transfers = session.koin.get<DataTransferRepository>()
            transfers.startImport(export.absolutePath)
            assertEquals(7, transfers.importState.succeeded().notes)
            assertEquals(deckCount, session.koin.get<DeckRepository>().getDecks().size)
        }
    }

    @Test
    fun maintenanceRunsAtStartAndAnAutomaticBackupFollowsTheSetting() = runBlocking {
        open().use { session ->
            val koin = session.koin
            // Starting the app schedules the weekly media cleanup, which has never run here.
            eventually("the media cleanup runs at start") { File(directories.files, "media-cleanup.stamp").exists() }

            // Turning automatic backups on makes one at once, because none was ever made.
            val folder = File(root, "backups").apply { mkdirs() }
            koin.get<DataTransferRepository>().setAutoBackup(true, folder.absolutePath)
            eventually("the automatic backup is written") {
                folder.listFiles().orEmpty().any { it.name.startsWith("mnemo-auto-backup-") && it.length() > 0 }
            }
            eventually("the settings remember when") {
                koin.get<UserSettingsRepository>().settings.first().backup.lastBackupAt != null
            }
            assertTrue(koin.get<UserSettingsRepository>().settings.first().backup.autoBackupEnabled)
        }
    }

    private suspend fun eventually(what: String, condition: suspend () -> Boolean) {
        withTimeout(30_000) { while (!condition()) delay(50) }
        assertTrue(condition(), what)
    }
}
