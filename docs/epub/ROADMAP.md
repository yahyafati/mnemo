# Mnemo — EPUB books roadmap

This roadmap adds **EPUB support**: the user picks a book, sees its chapters, and Mnemo creates **one
deck per chapter** under a deck for the book. The decks start **empty** (option A below); cards come
from the existing AI Smart Extract, chapter by chapter, when the user asks for them. It is written
for agents: each step lists what to build, where, and how to know it is done. The product roadmap is
[../ROADMAP.md](../ROADMAP.md); the architecture rules are [../ARCHITECTURE.md](../ARCHITECTURE.md)
and `CLAUDE.md`. Read those first.

**Decisions already made (2026-10-02):**

| Question | Decision |
|---|---|
| What does importing a book do | **Option A: empty decks, generate on demand.** Import creates `Book::NN Chapter` decks and nothing else, instantly and at no cost. Cards are generated per chapter, by the user, through Smart Extract's review queue. Generating every chapter at import was rejected: a 300-page book is ~70–90 AI requests and hundreds of cards nobody asked for. |
| Cost and consent | Nothing is sent to an AI provider without the user's action. A batch run (B6) shows the number of requests first. The existing `AiDisclosureDialog` still applies. |
| Dependencies | **None new.** `java.util.zip` + jsoup (already in `:core:ingest`). No change to `NOTICE`, the FOSS check or F-Droid. |
| Where the reader lives | `:core:ingest` `commonMain`, one implementation for Android and desktop (like `WebPageExtractor`). |
| DRM | Not supported and not circumvented. A DRM-protected book fails with a clear message. |

Defaults the owner has not contradicted (change them in B0 if wanted): the book's root deck is a
**top-level deck named after the book** (editable before creating); **text only** in v1 (images and
MathML are dropped).

Owner-only tasks are marked **(owner)**.

---

## What is already in the codebase (verified 2026-10-02)

| Piece | Where | Relevance |
|---|---|---|
| Sources → text | `SourceRepository` (`:core:data`), `PdfTextExtractor`, `WebPageExtractor` (`:core:ingest`), `SourceInput`/`SourceText`/`SourceProblem`/`SourceResult` (`:core:model/Source.kt`) | EPUB is a new `SourceInput`. `WebPageExtractor.html` already turns HTML into readable text (noise removal, block breaks, list markers) and `TextCleanup` normalizes it: reuse, don't rewrite. |
| Files | `DocumentAccess` (`info`, `openInput`, `keepAccess`; failures are `IOException`), `AppDirectories.cache` (`:core:common`) | Gives an `InputStream` only. `ZipFile` needs a file (see B1). |
| Pickers and drops | `rememberFilePicker(mimeTypes)`, `Modifier.fileDropTarget` (`:core:ui/files`); `FileKind.of(location)` in `:shell` `AppCommands.kt` maps extensions to `AnkiPackage`/`Backup`/`Unknown` | Add an `Epub` kind and a MIME type. Check how the desktop picker maps MIME types to extensions. |
| Decks | `DeckRepository.saveDeck(path)` creates missing parents and **returns the existing deck when the path exists** (match is by name, **ignoring case**, under the same parent) | Re-importing a book reuses the tree; two chapters with the same title would merge into one deck, so names need a numeric prefix (B3). Deck lists sort **by name**: unpadded numbers sort "10" before "2". |
| AI creation | `GenerateCardsUseCase` (`ExtractRequest` with `parts`, `deckId`, `title`), `TextChunker` (≤ 1,200 words per request), `AcceptGeneratedCardsUseCase`, `SmartExtractViewModel`/`Screen` in `:feature:create` | Chapter text goes in as the source text, the chapter's deck as `deckId`. Smart Extract already dedupes against the deck and streams a review queue. |
| Navigation | Features never depend on features; routes are in `:core:ui/navigation/Routes.kt` | The book flow lives inside `:feature:create`, next to Smart Extract, so it needs no route between features. |

---

## Working rules

1. **Android stays shippable and desktop stays working.** Every step ends with the Android exit check
   (`./gradlew assembleDebug testDebugUnitTest testAndroidHostTest lint verifyRoborazziAndroidHostTest`),
   the JVM module tests, and `desktopTest` / `:desktop:test` for what it touches.
2. **No Android imports in shared code** (CLAUDE.md "Platform seams"); run
   `python3 scripts/desktop/check-android-imports.py`.
3. **Nothing is saved or sent without a user action.** Parsing a book is read-only. Decks are created
   only on "Create decks"; cards only through the review queue.
4. **Never write an entry from the zip to disk by its name** (zip-slip). Read entries into memory by
   name, resolve references yourself (B1).
5. **Strings** go in `feature/create/src/commonMain/composeResources/values/strings.xml` (positional
   formats, plain apostrophes). **Tests** follow CLAUDE.md "Tests"; fakes are in `:core:testing`.
6. **Docs move with the code.** At the end of each step tick the boxes here and update `CLAUDE.md`
   (a short "Books (EPUB)" section once B1–B4 land), `docs/ARCHITECTURE.md` §5.2 and the ADR.
7. **Commit per step**, small diffs.

---

## Overview

| Step | Theme | Outcome | Rough effort |
|---|---|---|---|
| **B0** | Decisions, ADR, fixtures | ADR 0011; a handful of small EPUB test files | 0.5–1 day |
| **B1** | `EpubReader` | A book → title, author, chapters with text and word counts | 2–4 days |
| **B2** | Model and repository | `SourceInput.Epub`, `BookSource`, new `SourceProblem`s, `SourceRepository.readBook` | 1 day |
| **B3** | Create the decks | `CreateBookDecksUseCase`: naming, numbering, reuse | 1 day |
| **B4** | Import-a-book UI | Pick file → chapter checklist → create decks; Create tab and desktop drop | 2–3 days |
| **B5** | Generate for a chapter | Smart Extract gets an EPUB source: pick a chapter, deck preselected | 1–2 days |
| **B6** | Generate for several chapters *(optional)* | Run selected chapters in order with a cost estimate; one review per chapter | 2–3 days |
| **B7** | Polish and QA | Real books, accessibility, docs, release notes | 1–2 days |

```
B0 ──► B1 ──► B2 ──► B3 ──► B4 ──► B5 ──► B6
                                    └────► B7
```

B1 has no UI and can start before B0's ADR is final, once the fixtures exist.

---

## B0 — Decisions, ADR and fixtures

**Goal:** write down the rules that are easy to get wrong, and get test material.

- [x] **ADR 0011 "EPUB books"** ([../adr/0011-epub-books.md](../adr/0011-epub-books.md), written 2026-10-02; the owner has not reviewed it yet), recording at least:
  - Option A and why (cost, review fatigue).
  - **DRM stance** (below).
  - **Chapter definition**: the table of contents, with the spine as fallback; the heuristics of B1.
  - **No persistence of the book** in v1: no new table, no copy of the book in app storage. A deck is
    not linked to its book; generating later means picking the file again (B5), which preselects the
    deck by name. (Linking needs a schema bump, a migration, a fixture database and a decision about
    backups carrying book files: leave it for a later phase unless the owner wants it.)
  - Text only: images, MathML, footnote popups and ruby readings are dropped (readings: base text only).
  - Limits (numbers in B1).
- [x] **Test books.** Done differently from the first plan: no committed binaries and no Python
      script. `EpubBuilder` (`core/ingest/src/commonTest/.../EpubBuilder.kt`) builds each EPUB in
      memory, inside the test that reads it, so a test shows the book it uses and nothing needs
      regenerating. Covered by `EpubReaderTest` (27 tests): everything in this list except that EPUB 3
      with `nav`, EPUB 2 with `toc.ncx`, no TOC, anchors, nested parts, front matter, font
      obfuscation vs DRM, picture-only, percent-encoded and `../` hrefs, the `../evil` entry, the
      bomb, a plain zip, equal titles, `::`, and ruby are all there. Original list: EPUB 3 with `nav.xhtml`; EPUB 2 with `toc.ncx` only;
      no TOC at all (spine fallback); TOC entries pointing at **anchors inside one file**; nested TOC
      (Part → Chapter → Section); a **cover + copyright + dedication** front matter; font obfuscation
      listed in `encryption.xml` (**not** DRM); a text document listed in `encryption.xml` with a
      non-obfuscation algorithm (**DRM**); an image-only (fixed layout / comic) book; hrefs with
      percent-encoding and `../`; a zip with a `../evil` entry and a huge repeating entry (bomb);
      not an EPUB (a plain zip); two chapters with the same title; a title containing `::`; a
      Japanese book with ruby.
- [ ] A real public-domain EPUB or two (Project Gutenberg) for the manual check in B7 only; do **not**
      commit them. Partly done (2026-10-02, a throwaway test, deleted): on *The Communist Manifesto*
      (Gutenberg: 4 chapters, the contents page as front matter, the license as back matter, 2.6 s
      cold), *The Motorcycle Diaries* (53 chapters, titles and sizes right; its preface, biography and
      chronology count as content, which the picker lets the user uncheck). A OneDrive file that was
      never downloaded (all zero bytes) correctly fails as `Unsupported`. **Still untried: a book with
      real DRM** (the `sinf.xml`/`rights.xml`/`encryption.xml` paths are tested only on made-up files),
      a Part → Chapter textbook, and a Japanese book.

**Exit:** ADR written (**owner: review it**); test books in place.

## B1 — `EpubReader` (`:core:ingest`, `commonMain`)

**Goal:** read an EPUB into chapters, safely, on both platforms.

API (suggested; keep it a plain class built in `:core:data`'s `dataModule` like `WebPageExtractor`,
and it blocks, so callers use the IO dispatcher):

```kotlin
class EpubReader(private val cacheDir: () -> File /* from AppDirectories */) {
    fun read(input: InputStream, fileName: String? = null): BookResult
}
```

Returning model types that B1 needed and so added to `:core:model/Source.kt` (they are B2's first
item, ticked there): `BookSource`, `BookChapter`, `ChapterKind`, `BookResult`, `SourceProblem.Drm`.
The reader also takes an `EpubLimits` (all the numbers below, with these defaults) so tests can use
small ones.

**Status: built and tested on the desktop target and the Android host target (2026-10-02), with these
differences from the plan below:**

- Chapter and book titles the file doesn't give are left **blank**, not "Section N": the screens
  and B3 write "Chapter N" in the user's language.
- Content documents are parsed with jsoup's **HTML** parser after empty elements (`<span/>`) are written
  out. Without that, jsoup keeps a non-void tag open and swallows the rest of the page (this lost the
  text after a page-break marker). Only package files (OPF, NCX, container, encryption) use the XML parser.
  Namespaced elements are matched by local name, not with `dc|title` selectors.
- **Parts**: an entry named *Part/Book/Volume/Unit/Act/Division …*, with entries under it and a nearly
  empty page of its own, is not a chapter; its children are, and its page text is dropped (as a
  boundary, so it doesn't trail the chapter before it). Other entries with children are chapters that
  contain their sections. See the ADR.
- **Stubs** (merged into the next chapter) are chapters of **30 words or fewer and 200 characters or
  fewer**, not 100 words: a 60-word poem is a chapter. The 100-word / 600-character threshold is only
  used to tell a part's title page from a chapter.
- The tests use no `PlatformTest` (nothing here needs Android classes) and `EpubBuilder`, not fixture files.
- Not done: the **shared constants for `DocumentAccess`/`AppDirectories`** are wired in B2 (the reader
  takes a `cacheDir` lambda), and the Gutenberg check (B0).

- [x] **Spool, then `ZipFile`.** `DocumentAccess` only gives a stream, a sequential `ZipInputStream`
      can't find the OPF before it has passed other entries, and a book can be 100 MB of images. Copy
      the stream to a temp file in `AppDirectories.cache` (cap `MAX_FILE_BYTES`, suggested 100 MB;
      fail `TooLarge` while copying, not after), open `ZipFile`, **always delete the temp file**
      (`finally`). Do not extract anything to disk.
- [x] **Locate the package**: `META-INF/container.xml` → `rootfile@full-path` → OPF. Missing or not a
      zip → `SourceProblem.Unsupported`. Parse OPF, NCX and nav with `Jsoup.parse(…, Parser.xmlParser())`;
      namespaced selectors need jsoup's `dc|title` form (not `dc:title`).
- [x] **Metadata**: title (prefer `title-type=main` / `refines`, else the first `dc:title`), author
      (`dc:creator`), language (`dc:language`). Fall back to the file name.
- [x] **Resolve hrefs** relative to the OPF/nav/ncx file's folder: percent-decode, collapse `./` and
      `../`, strip the `#fragment` for the lookup, and reject any path that escapes the zip root.
      Look entries up by exact name, then case-insensitively (real books have mismatches).
- [x] **Chapters from the TOC.** EPUB 3: the `<nav epub:type="toc">` (the manifest item with
      `properties~="nav"`). EPUB 2: `toc.ncx` `navMap`. Neither: one chapter per **linear** spine item,
      titled from the first heading in the file, else "Section N". Top-level entries are chapters;
      deeper levels are folded into their parent (an option for later, not v1).
- [x] **Entries that point into the middle of a file.** Several TOC entries can share one XHTML file
      (`ch1.xhtml#a`, `ch1.xhtml#b`). Cut the text between consecutive anchors in document order (walk the
      DOM, start collecting at the element with that `id`, stop at the next anchor of the same file).
      A chapter is also everything from its file up to the next entry's file, including spine files
      between them that the TOC doesn't mention (a chapter split over two files).
- [x] **Text**: reuse the readable-text logic of `WebPageExtractor` (extract the shared part into
      an `internal` helper in `:core:ingest`; don't copy it) and `TextCleanup`. Drop `script`, `style`,
      `nav`, images, `epub:type` `pagebreak` and `noteref` (page numbers like "[12]", footnote marks),
      `rp` and `rt`. Keep headings as paragraph breaks. Use jsoup's HTML parser for chapter files (more
      forgiving than XML); the document's declared encoding wins over a guess, and a BOM is removed.
- [x] **Classify** every chapter as `Content`, `FrontMatter` or `BackMatter`: use the `epub:type`/`guide`
      references (`cover`, `copyright-page`, `dedication`, `titlepage`, `toc`, `colophon`, `index`,
      `bibliography`, `acknowledgements`, `endnotes`), then title keywords as a weak second signal
      ("Copyright", "Contents", "Acknowledgments", "Also by", "About the author", "Index", "Notes").
      The picker (B4) shows non-`Content` chapters **unchecked**, never hidden.
- [x] **Merge and split.** A chapter with under ~100 words that is not the only content (a section
      title page, "Part I") merges into the next chapter, keeping its title in the text. A chapter over
      the per-chapter cap is kept whole and flagged `truncated` (decks are cheap; silently splitting a
      chapter surprises people). Keep the original TOC order.
- [x] **DRM and encryption.** Parse `META-INF/encryption.xml`. Font obfuscation
      (`http://www.idpf.org/2008/embedding`, `http://ns.adobe.com/pdf/enc#RC`) is **not** DRM and is
      extremely common: ignore it. If any XHTML/HTML/text document of the spine is listed with another
      algorithm, or `META-INF/rights.xml` exists, fail with `SourceProblem.Drm`. Do not attempt to read
      or decrypt such a book.
- [x] **No text** (comics, fixed-layout scans, an image per page): `SourceProblem.NoText`.
- [x] **Limits** (constants in a companion, like `PdfTextExtractor`): file 100 MB; at most 5,000 zip
      entries; each XML/XHTML entry read to at most 8 MB of **uncompressed** data (count bytes while
      reading: don't trust the entry's declared size, this is the zip-bomb guard); at most 400
      chapters; at most 4,000,000 characters of text in total; 200,000 characters per chapter. When
      a limit hits, return what was read with `truncated = true`, except the file and entry caps,
      which fail with `TooLarge`.
- [x] Never log book text or titles.
- [x] Tests (`commonTest`, extends `PlatformTest`): one test per fixture of B0 asserting titles, order,
      word counts (approximate), classification, the DRM vs font-obfuscation distinction, that the
      `../evil` entry is never read and no temp file is left behind, that the bomb stops at the cap
      without reading it all, and that a non-EPUB zip is `Unsupported`.

**Exit:** `./gradlew :core:ingest:desktopTest :core:ingest:testAndroidHostTest` green (done); the
real Gutenberg books from B0 produce sensible chapter lists in a scratch test, not committed
(**not done**).

## B2 — Model and repository wiring

- [x] `:core:model/Source.kt` (`BookSource`, `BookChapter` (with `wordCount`), `ChapterKind`, `BookResult` and
      `SourceProblem.Drm` came with B1, with the text in Smart Extract's `sourceProblemText`; B2 added
      `SourceInput.Epub(val uri: String)`). The other `when`s over `SourceInput` are only in
      `DefaultSourceRepository`; no screen switches on it.
- [x] `SourceRepository.readBook(source: SourceInput.Epub): BookResult`, implemented in
      `DefaultSourceRepository` the way `readPdf` is: size check from `documents.info` against
      `EpubReader.maxFileBytes` (new, the reader's own limit), `FileUnavailable` on `IOException`,
      `ioDispatcher`, the file's name passed on as the title fallback. `read()` of a `SourceInput.Epub`
      returns `Unsupported`. `FakeSourceRepository` has `books` (a map per `SourceInput.Epub`, else
      `FileUnavailable`); `SourceRepositoryTest` covers a book read into chapters with the cache left
      empty, `read()` refusing a book, and a non-EPUB, a too-large and a missing file.
- [x] `EpubReader` is registered in `dataModule` (`cacheDir = { AppDirectories.cache }`). It does take a
      lambda and an `EpubLimits`, which Koin's `verify()` looks for, so `DependencyGraphTest` has a
      `ParameterTypeInjection` for it, like `FileSecretStore`'s.
- [x] The word "Encrypted" in `SourceProblem` means a password-protected PDF; keep `Drm` separate, with
      its own user-facing text ("This book is protected by DRM. Mnemo can only read DRM-free books").

**Exit:** Android and desktop unit tests green.

## B3 — `CreateBookDecksUseCase` (`:core:domain`)

**Goal:** the rules for turning chapters into deck paths, tested without UI.

**Status: built and tested on both targets (2026-10-02), with these differences from the plan below:**

- The naming rules are in a public `BookDeckNames` (`book(name)`, `chapter(position, chapterCount, title)`,
  `path(...)`), so B5 computes a chapter's deck name the same way. `CreateBookDecksUseCase` takes an extra
  `chapterCount` (the whole book's) and returns `BookDecks(rootDeckId, rootCreated, chapterDeckIds,
  createdChapterIds)`, so B4 can say how many decks were new.
- **Existing decks are looked up first and not saved again.** `OfflineDeckRepository.saveDeck` on an existing
  path overwrites the deck's description and category with the given ones, so "call `saveDeck`, it is
  idempotent" would wipe what the user wrote after a first import. Only missing decks go through `saveDeck`.
- **Numbers pad to the width of the whole book**, not of the created chapters (every deck of a 120-chapter
  book is `001 …`…`120 …`, whichever chapters one import picks, so a later import slots in and names match).
  Callers that import a subset pass `chapterCount = book.chapters.size`.
- A colon at either end of a name is dropped (`Notes:` + `::` would split the path in the wrong place) and a
  `::` inside becomes " – ". A name cut at the limit ends with "…" and stays within it.
- **No description** on the decks: the use case has no strings, and English text on every deck of a
  non-English book is worse than none. The same goes for "Chapter N", used for a blank title: B4 fills in
  its own, localized, before calling.

- [x] `suspend operator fun invoke(bookName: String, chapters: List<ChapterDeckRequest>): BookDecks`
      (`ChapterDeckRequest(chapterId, title)`; result maps `chapterId` → `deckId` plus the root deck id),
      using `DeckRepository.saveDeck`. All-or-nothing is not required (`saveDeck` is idempotent by path);
      a retry after a failure just reuses what exists.
- [x] **Names**: replace `::` in the book and chapter titles (for example with " – "), collapse
      whitespace, strip control characters, trim, and cut to a sane length (suggested 60 for the book,
      80 for a chapter) at a word boundary; an empty name becomes "Chapter N".
- [x] **Numbering**: prefix each chapter with its **zero-padded position among the created chapters**
      (width = digits of the highest number, minimum 2: `01 Introduction`, `02 …`). This keeps the deck
      list in book order (it sorts by name) and keeps equal chapter titles from merging (matching is
      case-insensitive by name). Use the chapter's position in the book (its `id`), not among the checked
      ones, so a later import of more chapters slots in correctly.
- [x] **Re-import**: the same book name reuses its root and chapter decks (that is what `saveDeck` does
      for an existing path). The picker (B4) tells the user which chapters already have a deck.
- [x] Nothing else is stored; the description of a chapter deck may carry the book title and the
      chapter's position ("Chapter 3 of *Book*") if it helps; keep it plain text.
- [x] Tests with `FakeDeckRepository`: naming edge cases, `::` in titles, numbering width, equal titles,
      a re-run creating nothing new, a retry after a failure half-way.

**Exit:** `:core:domain` tests green on both targets.

## B4 — Import-a-book UI (`:feature:create`)

**Goal:** the user picks an EPUB, chooses chapters, and gets the decks.

- [ ] `BookImportViewModel` + `BookImportScreen` (+ `BookImportUiState`/`Action`) following the Route/Screen
      split (ARCHITECTURE §4.1) and the shape of `SmartExtractViewModel`. State: reading, problem,
      the `BookSource`, per-chapter checked state, book name (editable), existing decks (to mark
      chapters that already have one), creating, result.
- [ ] **Screen**: title and author, an editable book (root) deck name, a chapter list with a checkbox,
      title and word count each, **Select all / none / content only**, a summary ("12 of 14 chapters,
      ~38,000 words"), and the primary action "Create decks". Front/back matter unchecked but visible.
      No AI is involved on this screen. After creating: a message and, per chapter, a way to go on to
      B5 ("Generate cards"). Handle `Drm`, `NoText`, `Unsupported`, `TooLarge`, `FileUnavailable` with
      specific texts. Large lists use `LazyColumn`; a 400-chapter book must stay smooth.
- [ ] **Entry points**: an "Import a book (EPUB)" action in the Create tab (next to Smart Extract; don't
      add a fourth mode if an action fits), using `rememberFilePicker(listOf("application/epub+zip"))`;
      check that the **desktop** picker maps that MIME type to `.epub` (extend its mapping if not).
      On desktop add `FileKind.Epub` (`"epub"`) to `FileKind.of` and handle it in `MnemoRoot`'s drop
      target and `AppCommand.OpenFile` so a dropped or "opened with" `.epub` goes to the import screen
      (update `DesktopIntegration`/`OpenRequests` only if that path filters by extension). Android
      "Open with Mnemo" for `.epub` (a `VIEW` intent filter) is optional: leave it out unless cheap, and if
      added, mind CLAUDE.md's release rules (no new permissions).
- [ ] Keyboard (desktop): Space toggles the focused chapter, Ctrl/⌘+A selects all; add to `Shortcuts`
      and the `?` sheet only if a new shortcut is introduced (CLAUDE.md "Desktop experience").
- [ ] Strings, `@Preview`, ViewModel tests in `commonTest` (fakes: `FakeSourceRepository`,
      `FakeDeckRepository`), Compose tests in `androidHostTest`, Roborazzi screenshots (record on
      Android; desktop only if there is a desktop-specific layout), accessibility labels on every
      checkbox row (title + word count).
- [ ] `DesktopAppTest`: drop an `.epub` fixture, pick chapters, create, see the decks.

**Exit:** on a device/emulator and on desktop, importing a real book gives the numbered chapter decks
in book order, re-importing creates nothing new, and the Android exit check is green.

## B5 — Generate cards for a chapter

**Goal:** cards for an existing chapter deck, through the review queue that already exists.

- [ ] Smart Extract gets an **EPUB source** (`SourceKind.Epub`; the source row today is
      Paste / PDF / Link / Dictation). Choosing it opens the file picker, then a **chapter chooser**
      (single chapter; one book, one chapter at a time keeps the review queue small). The chapter's
      text fills the text box like a PDF's does (editable, with its word count and the existing
      estimated-cards line), `title` = "Book — Chapter" for the prompt context.
- [ ] **Deck preselection**: if a deck exists at `Book::NN Chapter` (compute it with B3's naming, not
      by string guessing), select it as the destination; otherwise leave the user's choice. That is how
      "generate later" works without storing a link between deck and book.
- [ ] From B4's result screen, "Generate cards" for a chapter opens Smart Extract with that chapter's
      text and deck already set (same module: pass the chapter through a shared holder/`SavedStateHandle`
      of the Create destination; don't put chapter text in navigation arguments, and don't add a
      cross-feature route). Keep the parsed book in memory only while the Create flow is open.
- [ ] **Languages without spaces.** `TextChunker` and `SourceText.countWords` count whitespace-separated
      words, so a Japanese or Chinese chapter is a handful of "words": it would be sent as one huge
      request and the cost estimate would read ~0. Add a character-aware measure (for example, count a
      CJK character as half a word) to the chunker and the estimate before this ships; this affects
      Smart Extract with a pasted text too. The EPUB reader already avoids the problem in its own
      thresholds (ADR 0011).
- [ ] Chapters over `TextChunker`'s limit are split into several requests as today. Show the request
      count next to the cost-relevant numbers, and keep the existing `AiDisclosureDialog` before the first
      request.
- [ ] AI surfaces need `ReportAiButton` (already present on Smart Extract output) — no new AI output
      is added, only a new source, but confirm the screen still shows the button.
- [ ] Tests: `SmartExtractViewModelTest` for the EPUB source (chapter text lands in the box, deck is
      preselected, `Drm` message), screenshots of the chapter chooser.

**Exit:** with a mock AI server (`scripts/qa/device-checks.sh`'s or `MockWebServer`), a chapter's
deck receives accepted cards and nothing is saved before Accept.

## B6 — Generate for several chapters *(optional; do after B5 ships)*

**Goal:** "Create decks **and generate cards**" for the chapters checked in B4.

- [ ] On the B4 screen, a second action: "Create decks and generate". Before anything is sent show:
      number of chapters, words, **number of AI requests** (sum of `TextChunker` parts), and the
      routed provider/model (a local model costs nothing but time; say so from `AiRoute`). Block with
      `AiSetupPrompt` when `routeFor(task)` is null.
- [ ] Chapters run **in order, one review queue at a time**: generate chapter N, the user reviews and
      accepts (or skips), then chapter N+1. Never one giant queue. Progress "Chapter 3 of 8", the
      ability to stop, skip a chapter, and resume after a failure (reuse `GenerationState.Failed` /
      `fromPart`).
- [ ] The queue state across chapters survives configuration changes and process death the same way
      Smart Extract's does today (check how; if it doesn't, a batch run that is lost on rotation is
      unacceptable and this step must handle it).
- [ ] Rate limits and errors: the existing runner never retries auth/429; a batch must pause on them,
      not skip ahead.
- [ ] Tests: ViewModel tests for ordering, skipping, stopping, resuming; a test that nothing is sent
      before the confirmation.

## B7 — Polish and QA

- [ ] Manual pass on real books (Gutenberg novels, a textbook with parts and sections, an EPUB 2 book,
      a Japanese book): chapter count, order, titles, front/back matter classification, word counts,
      time to open a large book (a 5 MB text should open in a couple of seconds on a mid-range phone),
      memory (no OOM on a 100 MB illustrated book). Record results in a "Results" table at the end of
      this file.
- [ ] A DRM-protected book (a purchased one the owner has) shows the DRM message and no crash **(owner)**.
- [ ] TalkBack/keyboard pass over the chapter list; large font; dark theme; tablet and desktop widths
      (`Modifier.readingWidth()`).
- [ ] Docs: `CLAUDE.md` (a "Books (EPUB)" section: the reader's limits, the DRM rule, deck naming and
      numbering, "no book persistence"), `docs/ARCHITECTURE.md`, `docs/release/release-notes.md` and the
      F-Droid `changelogs/<versionCode>.txt` for the release that ships it, store listing text if it
      lists supported sources, and the privacy policy if it names the file types the app reads
      (`docs/release/privacy-policy.md`: books are read on the device; their text is sent to the user's
      AI provider only when they generate cards, like any source).
- [ ] The AI disclosure text still matches what is sent (the chapter text and the queue's fronts;
      the deck's notes stay local).
- [ ] `./gradlew assembleRelease` (R8): nothing reflective was added; if a keep rule is needed it
      goes in `app/src/main/keepRules`.

---

## Later (not in this roadmap)

- Link a chapter deck to its book (a schema bump + migration + fixture; decide what backups do with the
  book file) so "Generate cards" works without re-picking the file.
- Sections as sub-decks (`Book::Chapter::Section`), user-chosen TOC depth.
- Images from the book as image cards through `MediaRepository`; MathML to the existing math renderers.
- Highlights → cards (select text while reading).
- Other book formats (`.mobi`, `.azw3` are mostly DRM'd; `.fb2`, `.txt` with chapter markers are cheap).

## Open questions for the owner

1. Root deck: always a new top-level `Book` deck, or also "add the chapters under an existing deck"? (Default: new top-level, name editable; a parent can be chosen later by renaming the deck to `Parent::Book`.)
2. Is a text-only v1 acceptable for textbooks with formulas and diagrams?
3. Should B6 (batch generation) be built at all, or is per-chapter generation enough?
4. Keep the book file linked to its decks (see "Later") — worth a schema change in this phase?

## Results

_Filled in by B7._
