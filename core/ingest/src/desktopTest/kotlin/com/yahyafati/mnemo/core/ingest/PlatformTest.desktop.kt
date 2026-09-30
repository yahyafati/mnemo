package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.ingest.desktop.PdfBoxTextExtractor
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream

actual abstract class PlatformTest actual constructor()

actual fun newPdfExtractor(): PdfTextExtractor = PdfBoxTextExtractor()

actual fun makePdf(vararg lines: String): ByteArray {
    PDDocument().use { document ->
        if (lines.isEmpty()) document.addPage(PDPage())
        for (line in lines) {
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                stream.newLineAtOffset(72f, 700f)
                stream.showText(line)
                stream.endText()
            }
        }
        return ByteArrayOutputStream().also { document.save(it) }.toByteArray()
    }
}
