package com.yahyafati.mnemo.core.anki

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ApkgReaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun readsEveryFormatAnkiWrites() {
        for ((name, format) in listOf(
            Fixtures.MODERN to AnkiFormat.Latest,
            Fixtures.LEGACY to AnkiFormat.Legacy2,
            Fixtures.COLLECTION to AnkiFormat.Latest,
        )) {
            Fixtures.open(name, tmp).use { pkg ->
                assertEquals(format, pkg.format, name)
                assertTrue(setOf("Biology", "Languages", "Languages::Japanese").all { deck -> pkg.decks.any { it.name == deck } }, name)

                val vocab = pkg.notetypes.single { it.name == "Vocab" }
                assertEquals(listOf("Word", "Reading", "Meaning"), vocab.fields, name)
                assertEquals(listOf("{{Word}}", "{{Meaning}}"), vocab.templates.map { it.front }, name)
                assertTrue(pkg.notetypes.single { it.name == "Cloze" }.isCloze, name)
                assertTrue(!pkg.notetypes.single { it.name == "Basic" }.isCloze, name)

                assertEquals(7, pkg.noteCount, name)
                assertEquals(10, pkg.cardCount, name)
                val notes = pkg.notes(batchSize = 3).toList()
                assertEquals(listOf(3, 3, 1), notes.map { it.size }, name)
                val first = notes.flatten().first()
                assertEquals("What do <b>mitochondria</b> make?", first.fields[0], name)
                assertEquals(listOf("bio::cells", "marked"), first.tags, name)

                val cards = pkg.cardsForNotes(notes.flatten().map { it.id })
                assertEquals(10, cards.size, name)
                assertEquals(4, pkg.revlogForCards(cards.map { it.id }).size, name)
                val fsrsCard = cards.single { it.data.contains("\"s\"") }
                assertEquals(5.1, AnkiCardData.decode(fsrsCard.data).stability, name)

                assertEquals(setOf("cell.png", "konnichiwa.mp3"), pkg.media.map { it.name }.toSet(), name)
                val png = pkg.openMedia(pkg.media.single { it.name == "cell.png" }).use { it.readBytes() }
                assertEquals(listOf(0x89, 'P'.code, 'N'.code, 'G'.code), png.take(4).map { it.toInt() and 0xFF }, name)
            }
        }
    }

    @Test
    fun packageWithoutScheduling() {
        Fixtures.open(Fixtures.NO_SCHEDULING, tmp).use { pkg ->
            val cards = pkg.cardsForNotes(pkg.notes().flatten().map { it.id }.toList())
            assertTrue(cards.all { it.type == 0 && it.queue == 0 })
            assertTrue(pkg.revlogForCards(cards.map { it.id }).isEmpty())
        }
    }

    @Test
    fun closingDeletesTheWorkDirectory() {
        val work = tmp.newFolder()
        ApkgReader(Fixtures.driver).open(Fixtures.file(Fixtures.MODERN), work).close()
        assertTrue(!work.exists())
    }

    @Test
    fun rejectsFilesThatAreNotPackages() {
        val text = tmp.newFile("notes.txt").apply { writeText("not a zip") }
        assertFailsWith<AnkiFormatException> { ApkgReader(Fixtures.driver).open(text, tmp.newFolder()) }

        val emptyZip = tmp.newFile("empty.apkg").apply { zip(this, "readme" to "hi".toByteArray()) }
        assertFailsWith<AnkiFormatException> { ApkgReader(Fixtures.driver).open(emptyZip, tmp.newFolder()) }

        val notSqlite = tmp.newFile("bad.apkg").apply { zip(this, "collection.anki2" to "garbage".toByteArray()) }
        assertFailsWith<AnkiFormatException> { ApkgReader(Fixtures.driver).open(notSqlite, tmp.newFolder()) }
    }

    private fun zip(file: File, vararg entries: Pair<String, ByteArray>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
