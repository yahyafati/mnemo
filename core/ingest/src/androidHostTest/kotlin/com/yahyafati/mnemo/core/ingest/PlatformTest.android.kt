package com.yahyafati.mnemo.core.ingest

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.yahyafati.mnemo.core.ingest.android.PdfBoxAndroidTextExtractor
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
actual abstract class PlatformTest actual constructor()

private val context: Context get() = ApplicationProvider.getApplicationContext()

actual fun newPdfExtractor(): PdfTextExtractor = PdfBoxAndroidTextExtractor(context)

actual fun makePdf(vararg lines: String): ByteArray {
    PDFBoxResourceLoader.init(context)
    PDDocument().use { document ->
        if (lines.isEmpty()) document.addPage(PDPage())
        for (line in lines) {
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(72f, 700f)
                stream.showText(line)
                stream.endText()
            }
        }
        return ByteArrayOutputStream().also { document.save(it) }.toByteArray()
    }
}
