# Mnemo — PDF roadmap

This roadmap improves Smart Extract's **PDF** source in three ways:

1. **Which pages.** A page range (`1-10, 14, 20-25`) and a chapter picker from the PDF's bookmarks, so any
   part of a long PDF can be used, not only its first 300 pages.
2. **How the pages are read.** Besides the text layer, a vision model can **transcribe** pages into the text box
   (scanned PDFs, handwriting, math), or the **page images themselves** can be the source for card generation
   (slides, diagrams).
3. **Figures on cards.** In images mode, a queued card can get a crop of its page as card media.

It is written for agents: each step lists what to build, where, and how to know it is done. The decisions and their
reasons are [ADR 0014](../adr/0014-pdf-pages-and-images.md); the product roadmap is [../ROADMAP.md](../ROADMAP.md);
the architecture rules are [../ARCHITECTURE.md](../ARCHITECTURE.md) and `CLAUDE.md`. Smart Extract's pipeline is
ADR 0006, and the web and EPUB work it sits next to is [../web/ROADMAP.md](../web/ROADMAP.md) and
[../epub/ROADMAP.md](../epub/ROADMAP.md). Read those first.

**Proposed defaults (2026-10-04, not yet confirmed by the owner; settle them in P0):**

| Question | Proposed default |
|---|---|
| Where the options live | **In the PDF source, per import**, not in Settings. The last mode and quality are remembered in `UserSettings`. |
| Page numbers | **Positions in the file, 1-based** (what the range field takes). Printed page labels (`iv`, `12`), when the PDF has them, are shown next to the page count and in the chapter picker, never parsed from the field. |
| Page limit | `MAX_PAGES` (300) applies to the **selection**. Any page of a PDF up to `MAX_FILE_BYTES` (50 MB) can be chosen. |
| Modes | **Text**, **Auto**, **Read pages with AI**, **Cards from page images** (ADR 0014's table). Default: Auto when the Extract model has `vision`, else Text. |
| Stitching pages into one image | **Not done** (ADR 0014: billed by pixels, so no saving without losing legibility). Several page images per request instead. |
| Images per request | **1** per transcription request; **3** (`PAGES_PER_REQUEST`) per card-generation request in images mode. |
| Resolution | **Standard** 1,568 px long edge, **High** 2,048 px, JPEG about 80. P0 measures and may change these. |
| Text layer in images mode | **Sent along with the images** when a page has one: it costs little and fixes spelling and numbers. |
| Routing | A new **`AiTask.ReadPages`** for transcription, defaulting to the Extract route. |
| Image disclosure | **Its own one-time acceptance per provider**, kept as a set of provider ids in `UserSettings` (no schema change). |
| PDF links | **Unchanged (text only)** in this work. |
| On-device OCR | **Not now** (ADR 0014). |
| Dependencies | **One new, desktop only:** `org.apache.pdfbox:jbig2-imageio` 3.0.5 (Apache-2.0) for JBIG2 scans. Android renders with the platform `PdfRenderer`. No JPEG 2000 plugin on the desktop until its licence is checked. |

Owner-only tasks are marked **(owner)**.

---

## What is already in the codebase (verified 2026-10-04)

| Piece | Where | Relevance |
|---|---|---|
| PDF text | `PdfTextExtractor` (`:core:ingest` `commonMain`): `extract(input, fileName)`, `MAX_FILE_BYTES` 50 MB, `MAX_PAGES` 300, `MAX_CHARS` 400,000; `pdfSourceResult` cleans up. `PdfBoxAndroidTextExtractor` (PdfBox-Android 2.0.27) and `PdfBoxTextExtractor` (Apache PDFBox 3.0.8) strip pages `1..min(pages, 300)` | Gets a page selection (P1), per-page text (P5) and an outline (P2). Text extraction stays on PdfBox on both platforms. |
| Reading a picked PDF | `DefaultSourceRepository.readPdf(uri)` (`:core:data`): `documents.info` size check, `documents.openInput`, `pdf.extract` | Becomes "open" (copy into `cache/pdf/`, P1/P3) plus "read these pages". |
| Model | `SourceInput.Pdf(uri)`, `SourceText(text, title, truncated, sections, disambiguation)`, `SourceProblem` (`NoText` is "a scanned PDF") (`:core:model/Source.kt`) | `PdfInfo`, `PageRanges` and the mode enum are new types here. |
| Smart Extract | `SmartExtractViewModel`: `PdfPicked` → `read(SourceInput.Pdf)`; desktop drops (`DroppedFile.Pdf`) the same; `SourceKind { Paste, Pdf, Epub, Link, Dictation }`; the section picker of web W4 and the chapter picker of EPUB | The page field, chapter picker and mode selector go into the PDF source. The images-mode page grid replaces the box for that mode. |
| Splitting and estimate | `GenerateCardsUseCase.split` → `TextChunker` (1,200 words); `ExtractOptions.targetCards(words)` (3–40 per request); `WordCount` | Unchanged for the text modes. Images mode batches pages instead (P6). |
| Generation | `GenerationRequest(text, options, part, parts, title, avoid, replacing)` → `CardGenerationRepository` → `CardGenerationClient` → `ChatTextRunner`; `CardGenerationPrompt` fences the source in `<source>` | Images mode adds images to the request and a prompt variant (P6). |
| Chat DTO | `ChatMessage(role, content: String?)` (`:core:ai/dto/Chat.kt`), OpenAI chat completions subset | `content` must also take a list of parts (P4), with text-only requests unchanged on the wire. |
| Capabilities | `AiCapabilities.vision` (`:core:model/AiProvider.kt`), guessed by `ModelHeuristics.supportsVision` in `ConnectionProbe`, editable by the user (`capabilitiesSetByUser`). **Nothing sends an image yet.** | Gate for the AI modes; P4 adds an image check to the probe. |
| Tasks | `AiTask { Extract, CoAuthor, Explain, Rewrite }`, `AiTaskRoute`, `AiProviderRepository.routeFor(task)` | `ReadPages` is added (P4). |
| Disclosure | `AiDisclosureDialog` (`:core:ui/ai`), accepted once per provider (`AiProvider.disclosureAcceptedAt`); Smart Extract's wording is `feature_create_disclosure_content` | Images get their own acceptance and wording (P4). |
| Report | `ReportAiButton` (`:core:ui/ai`) on every AI output | The transcription in the box needs one (P5). |
| Card queue | `GeneratedCard(id, kind, front, back, tags, chunkIndex, wrongAnswers)` (`:core:model`); `AcceptGeneratedCardsUseCase` → `CardRepository.addNotes(…, NoteSource.Ai)` | Gets an optional `page` (P6) and figures (P7). |
| Media | `MediaRepository.store(input, name)` (content-addressed, `![](media:<sha256>)`), clean-up keeps files younger than a day | Figures are ordinary media (P7). |
| Rendering | None. ARCHITECTURE's table already names Android's `PdfRenderer` for previews. PdfBox-Android is not used for rendering | `PdfPageRenderer` is new (P3). |

---

## Working rules

1. **Android stays shippable and the desktop stays working.** Every step ends with the Android exit check
   (`./gradlew assembleDebug testDebugUnitTest testAndroidHostTest lint verifyRoborazziAndroidHostTest`), the JVM
   module tests, and `desktopTest` / `:desktop:test` for what it touches.
2. **No Android imports in shared code.** `android.graphics.pdf` and `Bitmap` live in `androidMain` under an
   `android` package (`AndroidPdfPageRenderer`); run `python3 scripts/desktop/check-android-imports.py`.
3. **Nothing new is sent to a provider without the user knowing.** Images go only to a model with `vision`, only
   after the image disclosure for that provider, and only after a run's confirmation (pages, requests, provider and
   model). The Text mode sends exactly what it sends today.
4. **No page image in memory as a set, in `SavedStateHandle`, or in a log.** Rendered pages are files in
   `cache/pdf/<id>/`, deleted on Clear, on a new source and when stale at start. Request bodies are never logged.
5. **No live network in tests.** AI requests run against MockWebServer (configs with `isLocal = true`, as
   `:core:ai`'s tests do). Fixture PDFs are made by us (see P0), with their recipe in a README.
6. **Every `when` stays exhaustive**: a new `AiFailure`, `AiTask` or mode needs its text and its branch everywhere.
7. **Docs move with the code.** At the end of each step tick the boxes here and update `CLAUDE.md` (a "PDF
   pages" paragraph once P1 lands), `docs/ARCHITECTURE.md` (§3's `:core:ingest` row and tree, §4.3's seam table,
   §5.2) and ADR 0014 (an "As built" note per step, as ADR 0013 has).
8. **Commit per step**, small diffs.

---

## Overview

| Step | Theme | Outcome | Rough effort |
|---|---|---|---|
| **P0** | Decisions, fixtures, spike | Defaults confirmed; fixture PDFs; rendering and token numbers measured | 1–2 days |
| **P1** | Page ranges | `PageRanges`; read any pages of a PDF; the 300-page limit applies to the selection | 2 days |
| **P2** | Chapters and page labels | Bookmarks as a chapter picker that fills the range; printed page labels shown | 1–2 days |
| **P3** | Rendering pages | `PdfPageRenderer` on both platforms; the opened PDF and its pages in `cache/pdf/` | 2–3 days |
| **P4** | Images in AI requests | Multimodal `ChatMessage`; image check in the probe; `AiTask.ReadPages`; image disclosure; new failure | 2–3 days |
| **P5** | Read pages with AI | Transcribe and Auto modes fill the box; progress, cancel, cache, Report | 3–4 days |
| **P6** | Cards from page images | Page grid; batches of page images go to card generation; regenerate per batch; card `page` | 3–5 days |
| **P7** | Figures on cards | Crop a page onto a queued card; stored as media on accept | 2–3 days |
| **P8** | Polish and QA | Real scans on both platforms, three providers, docs, release notes | 1–2 days |

```
P0 ──► P1 ──► P2
        │
        ├────► P3 ──┐
        │           ├──► P5 ──► P6 ──► P7 ──► P8
        └────► P4 ──┘
```

P1 and P2 help every PDF user and need no AI work. P3 (`:core:ingest`) and P4 (`:core:ai`) don't touch each
other and can be done in either order. P5 is the smallest step that makes scanned PDFs work; P6 and P7 can wait.

---

## P0 — Decisions, fixtures and a spike

**Goal:** confirm the defaults, make test material, and replace guesses with measurements.

- [ ] **(owner)** Confirm or change the proposed defaults above and the **(owner)** points of ADR 0014, especially:
      `AiTask.ReadPages` or not, pages per request, the two resolutions, and whether images mode (P6–P7) is wanted
      in the first release or only transcription (P5).
- [x] **ADR 0014** (`docs/adr/0014-pdf-pages-and-images.md`).
- [x] **Fixtures** in `core/ingest/src/commonTest/resources/pdf/`, all made by us, each listed in a README with
      the command that made it (so they can be regenerated) and no third-party content:
  - `outline.pdf`: 12 text pages, three-level bookmarks, page labels `i`–`ii` then `1`–`10`.
  - `scanned.pdf`: three pages that are only images of text (rendered text → PNG → `img2pdf`), one with a formula.
  - `scanned-jbig2.pdf`: the same pages encoded as JBIG2 (for the desktop renderer); `scanned-ccitt.pdf` with CCITT G4.
  - `mixed.pdf`: text pages and image-only pages alternating.
  - `slides.pdf`: landscape pages with a diagram and little text.
  - `ocr-layer.pdf`: a scan with an invisible text layer (it must read as text, not as scanned).
  - Keep each file small (under about 200 KB) and the total under 1 MB.
- [ ] **Spike (throwaway branch):** render `scanned.pdf` and `slides.pdf` with Android's `PdfRenderer` and with
      PDFBox at both resolutions; check legibility by eye. *PDFBox half done (ADR 0014, "As built (P0)"): legible at
      both sizes, 47–136 KB a page. The `PdfRenderer` half needs a device and is left to P3's instrumented test.*
- [ ] **(owner)** Send the rendered pages to three providers (one hosted, one local through Ollama or LM Studio,
      one more) with a transcription prompt; note the `usage` tokens per page per resolution, the latency and the
      errors. Record the numbers at the end of this file; adjust the resolutions and `PDF_PAGE_WORDS` if they say so.
- [x] Check `jbig2-imageio`'s licence and artifact (Apache-2.0) and whether `jai-imageio-jpeg2000`'s licence is
      compatible with GPL-3.0 and F-Droid; record the answer in ADR 0014.

**Exit:** defaults confirmed (**owner**); fixtures committed; token and legibility numbers recorded.

*Status: fixtures, the desktop spike and the licence check are done and recorded in ADR 0014 ("As built (P0)").
Open: the **(owner)** confirmations, the **(owner)** provider measurements below, and Android's `PdfRenderer` in the
spike. P1 and P2 only use the page-number and page-limit defaults (the first rows of the table); the AI rows
matter from P3 on, so confirm them before then.*

## P1 — Page ranges

**Goal:** read any pages of a PDF, chosen with a range field.

- [x] `PageRanges` (`:core:model`): `parse(text, pageCount)` → the sorted, merged, 1-based pages or a
      `PageRangeError` (`Empty`, `Malformed(at)`, `OutOfRange(page)`, `Reversed(start, end)`). Accepts spaces,
      `-`, `–` and `—` as dashes, an open end (`20-` = to the last page), repeated and overlapping parts. `format`
      writes the shortest form back (`1-10, 14, 20-25`). Plus `count` and `first(n)` (the limit).
- [x] `PdfTextExtractor` grows two calls (keeping `extract` for PDF links): `inspect(input)` → `PdfInfo(pageCount,
      title)` and `extract(input, pages, fileName)`, which strips only those pages (`startPage`/`endPage` per run of
      consecutive pages) and joins runs with a blank line. Same exceptions and failures as today.
- [x] `SourceRepository`: `openPdf(uri)` → `PdfOpenResult` (a `PdfHandle(id, info)` or a `SourceProblem`): the
      size check, then a **copy into `cache/pdf/<id>.pdf`** (`AppDirectories.cache`), then `inspect`.
      `readPdf(handle, pages)` → `SourceResult` from the copy. `closePdf(handle)` deletes it; stale copies (older than
      a day) are deleted on open. The copy is there for P3's renderer and so the picked file can be re-read without a
      second permission.
- [x] `MAX_PAGES` applies to the selection: more than 300 pages selected is an error under the field ("Choose 300
      pages or fewer"), not a silent cut. `truncated` is still set when `MAX_CHARS` cuts the text.
- [x] Smart Extract: after a PDF is picked or dropped, the PDF source shows its name, page count and a **Pages**
      field (empty = all pages, if 300 or fewer; else `1-300` filled in). Applying it reads those pages into the box,
      asking first if the box was edited (the sections picker's rule: `SourceSections` / "Replace your changes?").
      The selection and the handle id live in the UI state and `SavedStateHandle` (the id only, not the file).
- [x] Tests: `PageRangesTest` (`:core:model`), `PdfTextExtractor` page selection on both targets with
      `outline.pdf`, `SourceRepositoryTest` (copy, stale clean-up, `closePdf`), `SmartExtractViewModel` tests for
      the field and the edit warning (`commonTest`), screenshots of the PDF source with the field (light, dark).

*Status: done (ADR 0014, "As built (P1)"). Open: a look at the Pages field on a real phone and on the desktop; the
handle is kept in the ViewModel, not in `SavedStateHandle`, as the ADR explains.*

**Exit:** page 450 of a 600-page PDF can be read; PDFs of 300 pages or fewer behave as before when the field is left alone.

## P2 — Chapters and page labels

**Goal:** a book-like PDF can be chosen by chapter.

- [ ] `PdfInfo` gains `outline: List<PdfOutlineItem(title, level, page)>` (bookmarks with a page destination,
      depth limited to 3, items without a page dropped) and `labels: List<String>?` (the page label of each page, or
      null when the PDF defines none or they equal the positions). PDFBox and PdfBox-Android both read both
      (`PDDocumentOutline`, `PDPageLabels`).
- [ ] A **Chapters** button next to the Pages field when the outline has two or more items: a checklist in the
      section picker's style (title, printed page, page count of the chapter = until the next item at the same or a
      higher level). Ticking chapters writes their pages into the field; editing the field by hand unticks what no
      longer matches. Default: nothing ticked (the field decides).
- [ ] Page labels: the page count line shows "612 pages (printed i–xii, 1–600)" when they differ; the chapter
      picker shows the printed page.
- [ ] Tests: outline and labels from `outline.pdf` on both targets; chapter → range mapping (nested items, last
      chapter to the end); the ViewModel's two-way sync; a screenshot of the picker.

**Exit:** a textbook PDF's chapter 7 is two taps.

## P3 — Rendering pages

**Goal:** an encoded image of any page, on both platforms, without holding many in memory.

```kotlin
/** Renders pages of a PDF file. Blocking; call it off the main thread. */
interface PdfPageRenderer {
    /** [page] is 1-based. The image is at most [longEdge] px on its long side, JPEG, on a white background. */
    fun render(file: File, page: Int, longEdge: Int): RenderedPage
}

class RenderedPage(val bytes: ByteArray, val mimeType: String, val width: Int, val height: Int)
```

- [ ] `AndroidPdfPageRenderer` (`:core:ingest` `androidMain`, `android` package): `PdfRenderer` on a
      `ParcelFileDescriptor` of the cached copy; `Bitmap` `ARGB_8888` filled white, `RENDER_MODE_FOR_DISPLAY`,
      `Bitmap.compress(JPEG, quality)`, recycled after each page. One `PdfRenderer` at a time (it is not
      thread-safe): a `Mutex` around it. `SecurityException` (a password) → `SourceProblem.Encrypted`.
- [ ] `PdfBoxPageRenderer` (`desktopMain`, `desktop` package): `PDFRenderer.renderImageWithDPI` at the DPI that
      gives `longEdge`, `ImageType.RGB`, ImageIO JPEG writer with the quality set. Add `jbig2-imageio` to
      `desktopMain` (catalog entry, `NOTICE`, `app/config` / `desktop/config` licence JSON,
      `scripts/fdroid/check-foss-deps.py` stays green, `NativeLibrariesTest`-style check that the plugin is found in
      the packaged image: run `scripts/desktop/smoke-test-app.py`).
- [ ] If an encoded page is over `MAX_IMAGE_BYTES` (1.5 MB), re-encode at a lower quality, then a smaller size.
- [ ] `SourceRepository.renderPdfPage(handle, page, quality)` → a file in `cache/pdf/<id>/p<page>-<quality>.jpg`,
      rendered once and reused; `thumbnail(handle, page)` the same at about 320 px. `closePdf` deletes the folder.
- [ ] `PdfQuality { Standard, High }` (`:core:model`) with the long edges decided in P0.
- [ ] Bound in `androidDataModule` / `desktopDataModule` as a `single` (like `PdfTextExtractor`).
- [ ] Tests: render each fixture on both targets. Robolectric does not really render PDFs, so the Android
      renderer's real check is an instrumented test (`connectedDebugAndroidTest`, on a device) or P8's QA; under
      Robolectric test only the size arithmetic, the white fill and the error mapping. Desktop: `scanned-jbig2.pdf` renders non-blank (count dark
      pixels), sizes follow `longEdge`, the cache is reused and deleted.

**Exit:** any page of the fixtures becomes a legible JPEG file on both platforms.

## P4 — Images in AI requests

**Goal:** `:core:ai` can send images, and the app knows which models take them.

- [ ] `ChatMessage.content` becomes `MessageContent` (sealed: `Text(String)`, `Parts(List<ContentPart>)`;
      `ContentPart.Text(text)`, `ContentPart.Image(dataUrl)`) with a custom serializer: `Text` is written as a JSON
      string, `Parts` as the OpenAI array (`{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,…"}}`).
      `ChatMessage.system/user(String)` keep working; `ChatMessage.user(text, images)` is new. A **golden test** checks
      that every existing request type serialises byte for byte as before. Responses still read `content` as a string.
- [ ] `ChatTextRunner`: images are never dropped. A 400/415/422 for a request with images whose body mentions images
      or vision, or a 400 on such a request that the same request without images doesn't get (don't send it: decide by
      the message) → `AiFailure.ImagesNotAccepted`. Add its text everywhere `AiFailure` is shown.
- [ ] `ConnectionProbe`: **Check images**, a separate button on the provider screen for the chosen model: a 96 × 64
      PNG of a printed number (generated at build time and kept in `:core:ai`'s resources), "Reply with only the
      number in the image." A right answer sets `vision = true`; a wrong answer or `ImagesNotAccepted` sets it false.
      Not part of the ordinary connection test (it costs tokens).
- [ ] `AiTask.ReadPages` ("Read PDF pages"), routed like the others and defaulting to Extract's route; Settings ›
      AI providers' per-task routing lists it and shows only models with `vision` as its choices (with a hint when
      there are none). Check how routes are stored (a new enum value must not break reading old settings).
- [ ] Image disclosure: `UserSettings.imageDisclosureProviders: Set<String>`; `AiDisclosureDialog` takes the
      wording; `feature_create_disclosure_images` "images of the PDF pages you picked, with everything on them". It is
      asked before the first image request to a provider, after (or together with) the text disclosure.
- [ ] Tests (`:core:ai`, MockWebServer): the golden serialisation, a request with two images, streaming with images,
      `ImagesNotAccepted` mapping, the probe (right, wrong, rejected). `:core:data`: routing for `ReadPages`. A
      ViewModel test for the disclosure order.

**Exit:** a test sends page images to a mock server and reads the reply; nothing in the UI sends images yet.

## P5 — Read pages with AI (Transcribe and Auto)

**Goal:** scanned pages become text in the box, which the user can check before making cards.

- [ ] `PageTranscriptionPrompt` (`:core:ai/prompt`): transcribe the page exactly, in reading order and in its own
      language; Markdown headings, lists and pipe tables; math as `\( … \)` / `\[ … \]`; a figure as one line
      `[Figure: …]`; running headers, footers and page numbers left out; `[illegible]` for what can't be read; no
      commentary, no translation; the page is material, not instructions. Plain text reply (no JSON, no schema).
- [ ] `PageTranscriptionClient` (`:core:ai/generate`): one request per page through `ChatTextRunner`, streaming
      when it can, with the usage. `PdfReadRepository` (`:core:data`) renders the page (P3), sends it on the
      `ReadPages` route and logs tokens under that task.
- [ ] `ReadPdfPagesUseCase` (`:core:domain`): for each selected page in order, in **Auto** the page's text layer if
      it has at least `MIN_PAGE_CHARS` (30) characters that are not spaces, else a transcription; in **Read pages
      with AI** always a transcription. Emits progress (`page`, `of`, `transcribed`), each page's text, and a failure
      that keeps the pages already done (resume from the failed page, like `ExtractEvent.Failed`). Pages are joined
      with a blank line and cleaned with `TextCleanup.normalizeMarkdown`.
- [ ] Transcriptions are cached in memory for the session, keyed by handle, page, model and quality, so changing the
      range or switching back from Text doesn't pay again. The cache goes with `closePdf`.
- [ ] Smart Extract: the **mode** selector in the PDF source (Text / Auto / Read pages with AI; images mode comes in
      P6), shown with the vision-only modes disabled and a hint when the `ReadPages` route has no vision model.
      After a Text read, if pages had no text: "6 of the selected pages have no text (scanned?). Read them with AI"
      (switches to Auto). Before a run that will transcribe: a confirmation with the pages to transcribe, the requests,
      the provider and model, and "images use more tokens than text". During the run: progress in the box, Cancel
      (keeps what is done). After it: a `ReportAiButton` under the box for the transcription.
- [ ] Remember the last mode and quality in `UserSettings` (`pdfReadMode`, `pdfQuality`).
- [ ] Tests: the use case with a fake repository (Auto picks per page, resume, cancel), the prompt (text and
      fences), the ViewModel (no-vision hint, the "no text" offer, confirmation, cache hit, edit warning), a
      `:core:data` test with MockWebServer and `scanned.pdf` on the desktop target, screenshots of the mode selector
      and the progress state.

**Exit:** `scanned.pdf` and `mixed.pdf` fill the box on both platforms with a vision model, and the run after it
is the ordinary text pipeline.

## P6 — Cards from page images

**Goal:** the pages themselves are the source; the user chooses pages instead of editing text.

- [ ] Mode **Cards from page images** in the selector (vision only). The box is replaced by a **page grid**:
      thumbnails (P3) with a checkbox each, the selected count, and the Pages field kept in step with the ticks.
      Tapping a thumbnail opens the page large (zoom on Android, scroll wheel on the desktop, `Esc` to close).
- [ ] `GenerationRequest` gains `images: List<PageImage(page, file)>` (empty for text) and `pageNumbers`;
      `CardGenerationPrompt` gets an images variant: "The source is the attached page images (pages 14, 15 and 16, in
      that order)", the text layer of those pages inside `<source>` when they have one (default on, ADR 0014), and the
      same rules and output format. `CardGenerationClient` builds the message with `ChatMessage.user(text, images)`.
- [ ] `GeneratedCardsSchema` and the parser: an optional integer `page` per card (the page it came from), kept only
      when it is one of the request's pages. `GeneratedCard` gains `page: Int?` (queue only, not saved on the note).
- [ ] `GenerateCardsUseCase` / `ExtractRequest`: a source is either text parts or **page batches**
      (`PAGES_PER_REQUEST`, 3), one request per batch, in order; `chunkIndex` is the batch, so **Regenerate** resends
      that batch's images; `targetCards` counts `PDF_PAGE_WORDS` (300) per page, or the page's text-layer words if
      more. Dedupe and validation are unchanged.
- [ ] Review queue: a "p. 14" label on a card with a page; tapping it opens that page.
- [ ] Confirmation before the run (pages, requests, provider and model, the token note), the image disclosure, and
      progress per batch, as in P5.
- [ ] Tests: the prompt with images (`:core:ai`, the message has the parts in order and the text layer), the schema
      and parser with and without `page` (add replies to `core/ai/src/test/resources/replies`), the use case's
      batching, resume and regenerate (fakes), the ViewModel's grid ↔ field sync, screenshots of the grid.

**Exit:** `slides.pdf` gives cards from its images; regenerate works per batch; text modes are untouched.

## P7 — Figures on cards

**Goal:** a card about a diagram can show the diagram.

- [ ] **Add figure** on a queued card that has a `page` (images mode): the page opens large with a crop rectangle
      (drag the corners or the whole box; on the desktop with the mouse and arrow keys), and a choice of **Front** or
      **Back**. The crop is rendered from the page at High quality and kept in `cache/pdf/<id>/` until accept. A
      queued card shows its figure; **Remove figure** undoes it.
- [ ] Accept: for each accepted card with a figure, `MediaRepository.store` the crop (JPEG, or PNG when that is
      smaller: diagrams), then append `\n\n![](media:<sha256>)` to the chosen side, then `addNotes` as today. A
      failed accept leaves orphan media that the daily clean-up collects.
- [ ] The card preview in the queue renders the image through `MediaImage` like a saved card.
- [ ] Tests: crop maths (rectangle in page coordinates → pixels at any scale), accept stores media and writes the
      reference to the right field (fake `MediaRepository`), a figure on a rejected card stores nothing, a screenshot
      of the crop screen. Exported `.apkg` of such a card carries the image (existing exporter, one test).

**Exit:** an anatomy slide becomes a card with the figure on its front.

## P8 — Polish and QA

- [ ] **(owner)** Real material on a phone and on the desktop: a scanned book chapter, handwritten notes, a
      maths-heavy scan, a lecture-slides PDF, a 600-page textbook by chapter, a PDF with an OCR layer, an Internet
      Archive scan (often JPEG 2000: check the desktop's behaviour), a password-protected PDF. With one hosted and one
      local vision model. Note the results, token use and timing in the log below.
- [ ] Error and empty states: no vision model, `ImagesNotAccepted`, a page that renders blank, a selection over 300
      pages, a cancelled run, a provider timeout on a slow local model (suggest raising the provider timeout).
- [ ] Accessibility: thumbnails and the crop box have content descriptions; the grid works with the keyboard on the
      desktop (`Shortcuts`: Space to tick, arrows to move).
- [ ] Docs: `CLAUDE.md`, `docs/ARCHITECTURE.md` (§3 `:core:ingest` row and tree, §4.3 seam table: PDF rendering,
      §5.2), ADR 0014's "As built" notes; the F-Droid changelog for the release's versionCode; `docs/desktop/install.md`
      if JPEG 2000 stays unsupported on the desktop.
- [ ] Optional, if the owner wants it now: PDF links get the same options by handing the downloaded bytes to
      `openPdf` instead of reading them as text.

**Exit:** QA log written (**owner**); docs current.

### Measurements (P0, owner)

| Date | Provider / model | Page | Quality | Tokens in | Latency | Legible? | Notes |
|---|---|---|---|---|---|---|---|
| | | | | | | | |

### QA log (P8, owner)

| Date | PDF | Device | Mode | Result | Notes |
|---|---|---|---|---|---|
| | | | | | |
