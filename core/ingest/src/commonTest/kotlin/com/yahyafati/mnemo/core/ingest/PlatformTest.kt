package com.yahyafati.mnemo.core.ingest

/**
 * Base class of the tests that run on both targets: Robolectric on Android (PdfBox-Android needs
 * a `Context` for its resources), plain JUnit on desktop.
 */
expect abstract class PlatformTest()

/** The platform's [PdfTextExtractor]: PdfBox-Android or Apache PDFBox. */
expect fun newPdfExtractor(): PdfTextExtractor

/** A PDF with one page per line of text (none: one empty page). */
expect fun makePdf(vararg lines: String): ByteArray
