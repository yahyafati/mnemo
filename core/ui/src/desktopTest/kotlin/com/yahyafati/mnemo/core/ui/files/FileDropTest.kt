package com.yahyafati.mnemo.core.ui.files

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Reading what a file manager hands over in a drag (desktop ROADMAP D7). */
class FileDropTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class Drag(private val flavor: DataFlavor, private val data: Any) : Transferable {
        override fun getTransferDataFlavors() = arrayOf(flavor)

        override fun isDataFlavorSupported(candidate: DataFlavor) = candidate.equals(flavor)

        override fun getTransferData(candidate: DataFlavor): Any =
            if (isDataFlavorSupported(candidate)) data else throw UnsupportedFlavorException(candidate)
    }

    @Test
    fun aFileListIsTheFiles() {
        val a = folder.newFile("deck.apkg")
        val b = folder.newFile("notes.md")
        assertEquals(listOf(a, b), Drag(DataFlavor.javaFileListFlavor, listOf(a, b)).droppedFiles())
    }

    @Test
    fun aUriListIsReadAsFiles() {
        val file = folder.newFile("deck colpkg.colpkg")
        val uris = "# dragged from a file manager\r\n${file.toURI()}\r\nhttps://example.com/not-a-file\r\n"
        val uriList = DataFlavor("text/uri-list;class=java.lang.String")
        assertEquals(listOf(file), Drag(uriList, uris).droppedFiles())
    }

    @Test
    fun textHasNoFiles() {
        assertTrue(StringSelection("just some text").droppedFiles().isEmpty())
    }
}
