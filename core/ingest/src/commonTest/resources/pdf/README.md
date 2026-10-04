# PDF fixtures

Test PDFs for Smart Extract's PDF source (docs/pdf/ROADMAP.md, ADR 0014). All of them are **made by us** from
text written for the purpose, with the PDF base-14 fonts only: there is no third-party content, and nothing
here is shipped in the app. Together they are about 370 KB.

Regenerate them with `core/ingest/fixtures/make_pdf_fixtures.py` (the commands are in its header: PyMuPDF,
Pillow and libtiff's `tiffcp`). A rerun gives equivalent files, not identical bytes, so tests check structure and
words and never hashes. `PdfFixturesTest` (`:core:ingest` `desktopTest`) pins the properties below; if you change
the script, run it.

| File | Pages | What it is | Used by |
|---|---|---|---|
| `outline.pdf` | 12, text | Bookmarks on **four** levels (the reader keeps three: P2 drops `1.2.1 Mitochondria`) and one bookmark with no destination (`Errata (no destination)`, dropped). Page labels `i`, `ii`, then `1`–`10`. Page *n* says "This is page *n* of the outline fixture, printed page *label*." so a test can tell which pages were read. | P1 page selection, P2 outline and labels |
| `scanned.pdf` | 3, image only | The pages in `scanned-page-N.expected.md` as 8-bit gray images at 150 dpi, Flate. Page 2 holds three formulas (a fraction, a square root, exponents). No text layer. | P3 rendering, P5 transcription, the "no text" state |
| `scanned-ccitt.pdf` | 3, image only | The same pages as 1-bit images, CCITT Group 4 (`/CCITTFaxDecode`, `/K -1`). | P3 rendering |
| `scanned-jbig2.pdf` | 3, image only | The same pages as embedded JBIG2 (`/JBIG2Decode`): a page-information segment and one immediate **MMR** generic region (the G4 data of `scanned-ccitt.pdf` inside JBIG2 segments). | P3: the desktop renderer needs `jbig2-imageio` |
| `mixed.pdf` | 6 | Text, image-only, text, image-only, text, image-only (JPEG scans of the three scan pages). Text pages say "This is page *n* of the mixed fixture and it has a text layer." | P5 Auto mode (the per-page choice) |
| `slides.pdf` | 4, landscape 960 × 540 | Little text and mostly figures: a cycle of four boxes, a block diagram, a bar chart (all vector) and a food chain as a raster picture. | P6 images mode, P7 figures |
| `ocr-layer.pdf` | 1 | A scan (JPEG) with an invisible text layer (render mode 3), as OCR software leaves it. It must read as text, not as scanned. | P5 Auto mode, the "no text" check |

`scanned-page-1.expected.md` … `-3.expected.md` are what the scanned pages say, as the transcription prompt should
return them (Markdown, math as `\( … \)` / `\[ … \]`). They are for comparing a transcription by eye or by word
overlap; no test needs an exact match.

## Known limits

- `scanned-jbig2.pdf` uses JBIG2's **MMR** coding, which is valid JBIG2 but not what `jbig2enc` (the usual
  encoder) writes: that is arithmetic-coded generic regions and, with `-s`, symbol dictionaries. The fixture
  checks that the plugin is found and decodes a page; it does not cover those codings. If a real scan fails on the
  desktop, add one made by `jbig2enc` (`brew install jbig2enc`; `jbig2 -p -s`, then `pdf.py`).
- Poppler (`pdftoppm`) draws vertical streaks over `scanned-jbig2.pdf`'s MMR region; MuPDF and PDFBox with
  `jbig2-imageio` 3.0.5 decode it cleanly and the same as `scanned-ccitt.pdf`. Android's PDFium has not been tried
  on it yet (P3's instrumented test does).
- There is no JPEG 2000 fixture: the desktop does not decode it (ADR 0014, "P0 findings").
- The scanned pages are rendered at 150 dpi; real scans are often 300 dpi or more, so token and legibility numbers
  from these files say little about the *High* quality setting. The owner's measurements (ROADMAP P0) should use
  real material as well.
