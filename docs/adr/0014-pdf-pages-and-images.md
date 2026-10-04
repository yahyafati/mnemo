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
