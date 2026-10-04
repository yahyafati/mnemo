# ADR 0014: PDF pages, scanned pages and page images

- **Status:** Proposed (2026-10-04). Written on the proposed defaults of `docs/pdf/ROADMAP.md`; the points marked
  **(owner)** are the ones to change before P1 starts. P3–P6 build on them, so a change after they land costs more.
- **Date:** 2026-10-04
- **Context for:** `docs/pdf/ROADMAP.md` (P0–P8); ADR 0005 (AI providers and secrets); ADR 0006 (AI card
  creation); ADR 0004 (media); ARCHITECTURE §5.2

## Decision

Smart Extract's **PDF** source gets three things, chosen **per import** in the PDF source and not in Settings:

1. **Which pages.** A page range (`1-10, 14, 20-25`) and, when the PDF has bookmarks, a chapter picker that
   fills the range. The 300-page limit applies to the selection, not to the first pages of the file.
2. **How the pages are read**, one of four modes:

   | Mode | What happens | AI requests before card generation |
   |---|---|---|
   | **Text** | The text layer, as today | none |
   | **Auto** (default when the Extract model reads images) | The text layer where a page has one, an AI transcription where it has none | one per page without text |
   | **Read pages with AI** | Every selected page is transcribed by a vision model | one per page |
   | **Cards from page images** | The page images *are* the source; no text box | none: the images go to card generation |

3. **Figures on cards** (images mode only): a queued card can get a crop of its page, stored as card media.

The first three modes all end in the **editable text box**, so everything after it (chunking, the review queue,
deduplication, regenerate, the book run) stays as ADR 0006 describes. Only the images mode changes the pipeline.

### Why both transcription and direct images

They suit different material, and the choice belongs to the user:

- **Transcription** keeps the box. The user sees what the model read and can fix a misread ("10⁻³" read as
  "10³") or trim what they don't want sent before any cards exist. The images are paid for once; regenerating,
  retrying or a second run costs text only. It suits text-heavy scans: book pages, typed or handwritten notes.
- **Direct images** skip the second pass and let the model see layout, diagrams, tables and arrows that a
  transcript loses. It is cheaper for one run and more expensive for every retry, because each request sends the
  images again. It suits visual material: slides, diagrams, anatomy, charts. The editing step becomes **choosing
  pages** (thumbnails), and its distinctive gain is that a card can carry the figure itself (P7).

### Rejected: stitching several pages into one image

Providers bill images by pixels (tiles or area), so four pages tiled into one image at the same legibility cost
about the same as four images. The only saving comes from lowering the resolution, which loses small print,
subscripts and exponents first. The real saving, sharing the system prompt and instructions, comes from sending
**several page images in one request**, which this ADR does. A resolution choice (**Standard** / **High**) is the
explicit token knob.

### Rejected for now: on-device OCR

ML Kit is a proprietary Play Services library (ADR 0009 rules it out). Tesseract (Apache-2.0) would work offline
but adds native libraries for four ABIs plus tens of MB of language data per language, and reads handwriting and
math poorly. Transcription uses the provider the user already configured. On-device OCR can be added later as a
fifth mode behind the same seam; nothing here prevents it.

### Rendering pages

A new seam, `PdfPageRenderer` (`:core:ingest`), renders one page to an encoded image at a given long edge:

- **Android:** the platform `android.graphics.pdf.PdfRenderer` (PDFium), in `androidMain`
  (`AndroidPdfPageRenderer`). It reads every image codec scans use (CCITT, JBIG2, JPEG 2000) and is fast. It needs
  a seekable file, so the picked PDF is **copied into `cache/pdf/`** when it is opened (it is at most
  `MAX_FILE_BYTES`, 50 MB). It renders onto a transparent bitmap: fill it white first.
- **Desktop:** Apache PDFBox's `PDFRenderer`, already in the build. JBIG2 needs
  `org.apache.pdfbox:jbig2-imageio` (Apache-2.0): a new dependency, so `NOTICE`, the licences JSON and the FOSS
  check are updated, and `javax.imageio` is already in the runtime's `java.desktop`. **JPEG 2000 is not
  supported on the desktop:** the only ImageIO plugin (`jai-imageio-jpeg2000`) carries the JJ2000 licence, which
  needs checking against GPL-3.0 and F-Droid before it is bundled. **(owner)** A JPEG 2000 page renders without its
  image; P0 decides whether to detect that and say so.
- Text extraction stays on PdfBox(-Android); only rendering moves. The text layer and the image of a page come from
  different libraries, which is acceptable: they are never compared pixel by pixel.

Images are JPEG (quality about 80), at most 1,568 px on the long edge for **Standard** and 2,048 px for **High**,
re-encoded smaller if one image exceeds 1.5 MB. **(owner)** P0 measures tokens and legibility on three providers and
may change these numbers. Rendered pages are files in `cache/pdf/<id>/`, never kept in memory as a set, never in
`SavedStateHandle`, and deleted when the source is cleared, replaced, or found stale at start.

### Images in AI requests

`ChatMessage.content` becomes either a string (unchanged on the wire) or a list of parts in the OpenAI format
(`{"type": "text"}`, `{"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,…"}}`), which hosted
OpenAI-compatible APIs, Ollama, LM Studio, llama.cpp and vLLM accept. A text-only request must serialise
**byte for byte as today**, so servers without vision never see the new shape.

- A request with images goes only to a model whose `AiCapabilities.vision` is true. `ModelHeuristics` guesses
  that from the model's name, so the provider screen gets a **Check images** test (a small generated image with a
  number to read), and the user can still set the capability by hand.
- `ChatTextRunner` drops features a server rejects (schema, `stream_options`, streaming). Images can't be dropped:
  a rejection is a new `AiFailure` ("this model doesn't accept images") with its text everywhere `AiFailure` is
  shown, never a silent text-only retry.
- **A new task, `AiTask.ReadPages`**, routes transcription. It defaults to the Extract route, so nothing needs
  setting up, but a user can send pages to a cheap vision model and cards to a strong text one. Its tokens are
  logged under its own task. **(owner)** The alternative is no new task: transcription uses the Extract route.
- Request bodies are never logged. Keep it that way: an image is the whole page.

### Privacy and the disclosure

The current disclosure says Smart Extract sends "the source text you entered (and the fronts of cards already in
the review queue)". A page image sends **everything on the page**: margin notes, stamps, names, photos. So:

- Sending images asks once per provider with its own wording ("images of the PDF pages you picked, with
  everything on them"), separately from the text disclosure. The acceptance is kept as a set of provider ids in
  `UserSettings` (DataStore), not as a new column, to avoid a schema change for one flag. **(owner)**
- A run that sends images first confirms the page count, the number of requests and the provider and model, as
  the book run does (EPUB B6), and says that images use more tokens than text.
- The disclosure for transcription and for direct images names the images. Text mode's wording does not change.

### The model's output is still untrusted

A transcription is AI output that fills the box. It shows a **Report** button (`ReportAiButton`, as every AI
surface must) and goes through `TextCleanup.normalizeMarkdown` like any source. The transcription prompt treats the
page as material, not instructions, as `CardGenerationPrompt` treats `<source>`. A page that says "ignore your
instructions" is transcribed as text.

### Sizes and estimates

The card estimate is `WordCount` of the text for the first three modes. For direct images there is no text, so a
page counts as **300 words** (`PDF_PAGE_WORDS`) for `ExtractOptions.targetCards`, unless its text layer is longer.
Batches are `PAGES_PER_REQUEST` pages (default 3) in images mode and one page per transcription request. **(owner)**

### Figures on cards (P7)

The model is not asked for bounding boxes: they are unreliable. Instead, each generated card may name the page it
came from (an optional `page` in `GeneratedCardsSchema`, ignored if out of range), and the review queue offers
**Add figure**: the page opens with a crop rectangle, and the crop is stored with `MediaRepository.store` on accept
and added to the front or back as `![](media:<sha256>)` (ADR 0004). Media is stored before `addNotes`; if the
accept fails, the orphan is collected by the usual media clean-up after a day. Nothing is stored for a card that is
rejected.

### What does not change

- PDFs behind a link stay text-only in this work (the bytes are in `WebPageExtractor`; handing them to the same
  handle is a later step). **(owner)**
- Password-protected PDFs still fail as `Encrypted`; DRM-like protections are not circumvented.
- EPUB, links, paste and dictation are untouched.
- No change to the database schema: `GeneratedCard` (queue only) gets an optional `page`, and figures are ordinary
  media.

## Consequences

- Scanned PDFs, which fail today with "no text", become readable with any vision model the user has configured.
- Large PDFs become usable: any 300 pages, chosen by range or chapter, instead of the first 300.
- Images cost several times more tokens than the same page's text. The mode, the resolution, the confirmation and
  the per-task usage keep that visible; the default for a model without vision stays Text.
- Two rendering implementations behave slightly differently (PDFium against PDFBox), and the desktop can't render
  JPEG 2000 images. P8's QA covers real scans on both.
- A new desktop dependency (`jbig2-imageio`) and a new kind of data sent to providers, each recorded where the
  rules ask (`NOTICE`, the disclosure).
- Images mode is a second path through card generation: the request, regenerate and the queue learn about page
  batches. Keeping the other three modes on the text box limits that to one mode.

## As built (P0, 2026-10-04)

P0 made the fixtures and ran the desktop half of the rendering spike. Nothing in the decision above changed; what
it found:

- **Fixtures** are in `core/ingest/src/commonTest/resources/pdf/` (README there), made by
  `core/ingest/fixtures/make_pdf_fixtures.py` and pinned by `PdfFixturesTest`. `scanned-jbig2.pdf` is JBIG2 with
  **MMR** coding, not the arithmetic coding `jbig2enc` writes; see the README's limits.
- **Without `jbig2-imageio`, PDFBox renders a JBIG2 page as blank white and throws nothing.** P3 must treat a
  rendered page with no dark pixels as "could not be read" (an error state, not an empty page), whichever platform
  rendered it. With the plugin, the JBIG2 and CCITT fixtures decode to the same pixels.
- **`org.apache.pdfbox:jbig2-imageio` is 3.0.5** (Maven Central, May 2026; the roadmap said 3.0.4). Its POM has
  only test dependencies, the jar registers itself through `META-INF/services/javax.imageio.spi.ImageReaderSpi`
  (so plain `ImageIO` finds it on the classpath, with nothing to call), and it ships Apache-2.0 `LICENSE` and
  `NOTICE` files (the copyright line to put in `NOTICE` is "PDFBox JBIG2 ImageIO plugin, Copyright 2026 The Apache
  Software Foundation"). It is 151 KB. Apache-2.0 is compatible with GPL-3.0-or-later.
- **JPEG 2000 stays unsupported on the desktop.** `com.github.jai-imageio:jai-imageio-jpeg2000` 1.4.0 is under
  two licences: Sun's BSD-3-clause with a "not for nuclear facilities" acknowledgement, and **JJ2000**, which grants
  its copyright covenant only for products "claiming conformance to the JPEG 2000 Standard", says "no license or
  right to this software module is granted for non JPEG 2000 Standard conforming products", warns that use may
  infringe patents, and lets the partners "inhibit third parties" from other uses. Our reading (not legal advice)
  is that this is a field-of-use restriction GPL-3.0 does not allow on code we distribute under it, and F-Droid
  would likely flag it, so it is not bundled. A JPEG 2000 page then renders without its image (blank where the scan
  is): the same blank-page check as above catches it, and P8 documents it in `docs/desktop/install.md`.
- **Desktop rendering numbers** (PDFBox 3.0.8, JDK 21, macOS arm64, JPEG quality 0.80, white background, the
  fixtures above at 150 dpi source): Standard (1,568 px long edge) gave 47–85 KB per page, High (2,048 px) 70–136
  KB, far below the 1.5 MB limit. The first render of a page took roughly 70–130 ms and a re-render at another size
  a few ms (PDFBox keeps the decoded images); 1-bit scans cost no more than gray ones. Both sizes were legible by
  eye on the fixtures, including exponents, the radical and the fraction bar of `scanned.pdf` page 2 and the labels
  on `slides.pdf`, so **the 1,568 / 2,048 numbers stay as proposed**: these fixtures are too clean and too small
  to argue for different ones, and the tokens-per-page figures need the owner's provider runs (below).

Not done in P0, and still open:

- **Android's `PdfRenderer` comparison.** No device or emulator was available. P3's instrumented test (and the
  owner's P8 pass) will render the same fixtures with PDFium and compare size and legibility; what the ADR says
  about `PdfRenderer` (every scan codec, a transparent bitmap to fill white) is from its documentation, not from a run.
- **Tokens, latency and errors per provider** (ROADMAP P0, **(owner)**), and the **(owner)** confirmations of the
  proposed defaults, which keep this ADR at *Proposed*.

## As built (P1, 2026-10-04)

- **`PageRanges`** (`:core:model`) parses and writes the Pages field: sorted runs that don't touch, `-`/`–`/`—`, an
  open end (`20-`), spaces anywhere, errors `Empty`, `Malformed(at)`, `OutOfRange(page)`, `Reversed(start, end)`.
  A run of two pages is written as a range (`3-4`). The limit is `PdfInfo.MAX_PAGES` (300); `PdfTextExtractor.MAX_PAGES`
  is the same constant.
- **`PdfTextExtractor`** gained `inspect(input, fileName)` (`PdfInfoResult`) and `extract(input, pages, fileName)`;
  `extract(input, fileName)` stays for PDF links and means "the first 300 pages". Pages the file doesn't have are
  ignored, runs of consecutive pages are stripped together and joined with a blank line, and `truncated` is set when
  the limit cut chosen pages or `MAX_CHARS` cut the text.
- **`SourceRepository.openPdf / readPdf / closePdf`**: `openPdf` checks the size, copies the file to
  `cache/pdf/<uuid>.pdf` (reading at most `MAX_FILE_BYTES + 1` bytes, so a file that misreports its size is still
  refused), inspects the copy and deletes it again if that fails; copies and folders older than a day are removed
  on every open. A handle's id is filtered to letters, digits and `-` before it names a file, so a forged handle
  can't reach outside `cache/pdf/`. `closePdf` also deletes `cache/pdf/<id>/` (where P3's rendered pages go).
  `read(SourceInput.Pdf)` is unchanged and unused by Smart Extract now.
- **Smart Extract**: the PDF source shows the title, the page count and the Pages field with a Read pages button
  (IME Done also applies). The field is validated as it is typed; the box changes only on apply, after "Replace your
  changes?" if the text differs from what the last read gave it (a blank box is replaced without asking). A PDF
  of 300 pages or fewer opens with an empty field (all pages); a longer one with `1-300`, and an empty field there
  is the "choose 300 pages or fewer" error, not a silent cut.
- **Deviation from the roadmap:** the open PDF's handle is **not** in `SavedStateHandle`. Nothing else of the
  box (text, queue, book, sections) survives process death, so restoring only the handle would give a Pages field
  over an empty box. It lives in the ViewModel and is closed when another source replaces it (a link only once it
  was read) or the text is cleared; a copy left by a closed screen is removed by the one-day clean-up.

## As built (P2, 2026-10-04)

- **`PdfInfo`** gained `outline: List<PdfOutlineItem(title, level, page)>` and `labels: List<String>?`. Both readers
  walk `PDDocumentOutline` in document order (pre-order, so the list is the bookmarks as the user would scan them),
  find each page with `findDestinationPage` and read `PDPageLabels`; the shared rules are in `:core:ingest`
  (`pdfOutline`, `pdfLabels`): a bookmark with no title or no page the PDF has is dropped (the ones under it keep
  their own level), levels deeper than `PdfOutlineItem.MAX_LEVEL` (3) are not walked, at most `MAX_ITEMS` (2,000)
  are read (a guard against a looping or hostile outline), and labels are null when they are missing, don't cover
  every page, are all blank, or are just the positions. A PDF whose outline or labels throw is opened without them:
  they are a convenience and never fail `inspect`.
- **`PdfInfo.chapterPages(index)`** is the chapter rule: from the bookmark's page up to the page before the next
  bookmark **in the list** at the same or a higher level, to the last page for the last one, and never fewer than
  its own page (an outline that isn't in page order gives a one-page chapter, not a backwards range).
- **`PageLabels`** (`:core:model`) turns labels into the count line's `i–xii, 1–600`: a label continues a run when it
  is the next number after the same prefix (`A-1`, `A-2`) or the next well-formed Roman numeral in the same case;
  blank labels are left out; at most four runs are shown, then `…`.
- **The picker** is a dialog in the section picker's style. The Pages field stays the one thing that decides: the
  ticks are *derived* from it (a chapter is ticked when all its pages are in the field; an empty or unreadable field
  ticks nothing, because empty means every page), so there is no second selection to keep in step, and the dialog
  being modal means the field can't be edited by hand while it is open. Ticking adds a chapter's pages
  to the field (`PageRanges.plus`), unticking takes them out (`minus`), so unticking a sub-chapter of a ticked part
  leaves the part unticked. A selection over 300 pages shows the same "choose 300 pages or fewer" error. The
  dialog's **Read pages** button closes it and reads the field (with the same "Replace your changes?" question);
  **Done** closes it and only keeps the field. The Chapters button is shown for two or more bookmarks.

## As built (P3, 2026-10-04)

- **`PdfPageRenderer`** (`:core:ingest`) is `render(file, page, longEdge): RenderedPage(bytes, mimeType, width, height,
  blank)`. A failure is a `PdfRenderException(problem)` carrying a `SourceProblem` (`Encrypted` for a password,
  `Unsupported` for a file or page it can't read, `TooLarge` for running out of memory). The size rules are shared
  and tested without a PDF library: `fitLongEdge`, `encodeWithin` (JPEG 80 first; over `MAX_IMAGE_BYTES`, 1.5 MB, then
  quality 60, then 75 %, 50 % and 35 % of the edge, never under 400 px, so a 320 px thumbnail is never re-encoded) and
  `InkCounter`.
- **Blank pages.** Each renderer counts dark pixels (luma under 160) while it has the bitmap and sets `blank` when
  there are fewer than 12, or fewer than one in 20,000. `SourceRepository.renderPdfPage` / `pdfThumbnail` turn that
  into the new `SourceProblem.BlankPage` ("This page came out blank. It may be empty, or hold a picture Mnemo can't
  draw") and save no file. **A page that is really empty fails the same way**, which the ADR accepted for the JBIG2 /
  JPEG 2000 case; if a page grid (P6) needs to show truly empty pages, the repository is the place to tell them apart
  (e.g. from the page's text layer and image list), because only it can see both libraries.
- **Android:** `PdfRenderer` on a `ParcelFileDescriptor` of the cached copy, ARGB_8888 bitmap filled white,
  `RENDER_MODE_FOR_DISPLAY`, recycled after each page, all behind one `synchronized` lock (the renderer is not
  thread-safe even across documents; the interface is blocking, so a lock, not the roadmap's `Mutex`). A
  `SecurityException` is `Encrypted`. **Robolectric cannot run `PdfRenderer` at all** (its native class calls a JDK
  internal the test JVM lacks), so the host test covers only the file-gone error; the real check is
  `app/src/androidTest/.../pdf/AndroidPdfPageRendererTest` (three scan encodings at both qualities: JPEG, size, white
  corner, not blank; slides' shape; a missing page; a non-PDF), which is compiled by `assembleDebugAndroidTest` with
  the `:core:ingest` fixtures added as that test's assets, **but has not been run: no device was available.**
  Until it runs, what this ADR says about PDFium reading every scan codec is still from its documentation.
- **Desktop:** `PDFRenderer.renderImage(page, edge / longSide, ImageType.RGB)` (RGB is filled white), the JPEG
  written through ImageIO with the quality set and a memory-cache stream. `org.apache.pdfbox:jbig2-imageio` 3.0.5 is
  in `desktopMain` (catalog `apache-pdfbox-jbig2`, `NOTICE`, AboutLibraries picks it up as Apache-2.0).
  `NativeLibrariesTest` checks that ImageIO finds the JBIG2 reader and a JPEG writer, and the packaged
  `Mnemo.app` contains the jar. The tests render every fixture: `scanned-jbig2.pdf` is not blank and darkens within 10 %
  as many pixels as `scanned-ccitt.pdf`, an empty page is `blank`, a password gives `Encrypted`.
- **Cache.** `renderPdfPage(handle, page, quality)` writes `cache/pdf/<id>/p<page>-standard|high.jpg` and
  `pdfThumbnail(handle, page)` `p<page>-thumb.jpg` (`PdfQuality.THUMBNAIL_EDGE`, 320 px), each rendered once (a
  temporary file in the folder is renamed over the target, so a reader never sees half a file) and reused until
  `closePdf` or the day-old clean-up. A page outside the handle's page count fails as `Unsupported` without calling
  the renderer. The result is `PdfPageResult.Success(file)` / `Failure(problem)`.
- **A P1 fix on the way:** `closePdf` built the page folder from the raw handle id; it now uses the same
  letters-digits-and-`-` filter as the copy, so a forged handle can't delete outside `cache/pdf/` (a test pins it).

