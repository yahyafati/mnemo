package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.ingest.android.AndroidPdfPageRenderer
import com.yahyafati.mnemo.core.model.SourceProblem
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * What Robolectric can check of the Android renderer (docs/pdf/ROADMAP.md, P3). It can't run the platform's
 * `PdfRenderer` at all (its native class needs a JDK internal this JVM lacks), so whether a real page comes out
 * legible is checked on a device: `app/src/androidTest/.../pdf/AndroidPdfPageRendererTest`.
 */
class AndroidPdfPageRendererTest : PlatformTest() {
    @Test
    fun aFileThatIsGoneFailsAsUnsupported() {
        val folder = createTempDirectory("render").toFile()
        try {
            val failure = assertFailsWith<PdfRenderException> { AndroidPdfPageRenderer().render(File(folder, "gone.pdf"), 1, 800) }
            assertEquals(SourceProblem.Unsupported, failure.problem)
        } finally {
            folder.deleteRecursively()
        }
    }
}
