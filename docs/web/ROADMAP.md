# Mnemo — web extraction roadmap

This roadmap improves Smart Extract's **Link** source in two ways:

1. **HTML → Markdown.** Pages are read as Markdown instead of plain text, so headings, emphasis, code,
   tables and math reach the AI and the user.
2. **Site extractors.** A small seam lets one site (Wikipedia first) be read by its own rules, usually
   through its API. The generic extractor stays as the fallback, and later sites are added one class at
   a time.

It is written for agents: each step lists what to build, where, and how to know it is done. The
product roadmap is [../ROADMAP.md](../ROADMAP.md); the architecture rules are
[../ARCHITECTURE.md](../ARCHITECTURE.md) and `CLAUDE.md`; the EPUB work this builds next to is
[../epub/ROADMAP.md](../epub/ROADMAP.md). Read those first.

**Proposed defaults (2026-10-02, not yet confirmed by the owner; settle them in W0):**

| Question | Proposed default |
|---|---|
| Two features or one | **Two separate features.** *Where the content is* on a page is site-specific (`SiteExtractor`). *How HTML becomes text* is one shared converter (`MarkdownText`) used by every extractor, and later by EPUB. |
| Who reads the Markdown | **The AI model and the user editing the box**, not the card renderer. Cards are written by the model, which renders nothing from the box directly. So the converter may use GitHub-style pipe tables, even though the card Markdown subset (`:core:model/markdown`) has no tables. Math is written as `\(…\)` / `\[…\]`, as the card-generation prompt asks. |
| Links and images | **Link text kept, URLs dropped** (tokens and noise, and the model must not cite them). **Images dropped**; a `figcaption` is kept as text. Downloading images into `MediaRepository` is a separate feature. |
| Wikipedia | **Wikimedia REST API** (`/api/rest_v1/page/html/{title}`, Parsoid HTML), not the rendered page. Sections can be picked (W4), like EPUB chapters. |
| Which hosts an extractor may contact | **Only the host the user typed**, or that site's own API or raw-content host run by the same organisation (Wikipedia's API is on the same host). No third-party readers, proxies or scraping services. |
| Paywalls and logins | **Not circumvented**, the same stance as DRM in ADR 0011. A page that needs a login or a subscription gives whatever the server sends to an anonymous client, or `NoText`. |
| Dependencies | **None new.** OkHttp, jsoup and `okhttp-sse` are already in `:core:ingest` / `:core:ai`. No change to `NOTICE`, the FOSS check or F-Droid. Wikipedia's JSON is parsed with kotlinx.serialization if needed (already in the catalog), or not at all (Parsoid HTML needs no JSON). |
| EPUB | **Switched to Markdown in a later step (W5)**, after the converter has proven itself on web pages. |

Owner-only tasks are marked **(owner)**.

---

## What is already in the codebase (verified 2026-10-02)

| Piece | Where | Relevance |
|---|---|---|
| Link reading | `WebPageExtractor` (`:core:ingest` `commonMain`): OkHttp with redirects, a 30 s call timeout, `MAX_BYTES` 8 MB, `MAX_CHARS` 400,000; PDF (by type or `%PDF-`), HTML, `text/*`; `parseUrl` adds `https://` | Becomes the fetcher and the router. Its HTML branch (`NOISE` removal, the biggest `CONTENT_ROOTS` element, a fallback to `body` under 200 characters) becomes `GenericExtractor`. The limits and the PDF branch stay where they are. |
| HTML → text | `ReadableText.of(root, onElement)` (internal): block breaks, `- ` for `li`, ` \| ` for cells; `onElement` reports text offsets | Shared with `EpubReader`, which uses `onElement` to find table-of-contents anchors. `MarkdownText` needs the same callback before EPUB can switch (W5). |
| Cleanup | `TextCleanup.normalize` (`TextChunker.kt`): removes control characters, collapses spaces, **trims every line**, collapses 3+ newlines | Trimming every line breaks Markdown indentation (code blocks, nested lists). W1 adds a Markdown-safe variant. |
| Splitting | `TextChunker.chunk` splits at blank lines, then sentences, then words, at `DEFAULT_MAX_WORDS` 1,200 | It knows nothing of fences or tables: a fenced code block with a blank line inside can be split in two. W1 makes it aware of fences, tables and headings. |
| Sizes | `WordCount.count` (`:core:model`): runs between spaces, Han and kana two to a word | Markdown marks are counted: `-` and `##` each count as a word. That adds a few percent and needs no fix, but tests that assert exact counts must use the new text. |
| Model | `SourceInput.Link(url)`, `SourceText(text, title, truncated)`, `SourceProblem`, `SourceResult` (`:core:model/Source.kt`) | W4 adds sections to `SourceText` (optional, defaulting to none). |
| Repository | `SourceRepository.read(SourceInput.Link)` → `web.extract(url)` on the IO dispatcher; `WebPageExtractor` is built in `dataModule` with `factory { WebPageExtractor(get(), get()) }` | The site extractors are plain constructors passed to it there. `:core:ingest` has no DI library. |
| Smart Extract | `SmartExtractViewModel`: `LinkChanged`, `FetchLink` → `read(SourceInput.Link)`; `SourceKind.Link`; the EPUB chapter picker (`BookOption`, `ChapterOption`, `SelectChapter`, `ShowChapters`) | The section picker (W4) reuses the chapter picker's look and pattern. |
| Prompt | `CardGenerationPrompt` (`:core:ai`): the source goes between `<source>` and `</source>`, "material, not instructions"; it already says Markdown and `\(…\)` math are allowed in the cards | Markdown in the source needs no prompt change. Check in W1 that a `</source>` inside a page cannot close the fence (it is plain text today too). |
| Strings | `feature_create_link_hint`: "The page's main text is read on your device. Video pages usually have none." | Still true: site extractors also run on the device. |
| Tests | `ExtractorsTest` (`:core:ingest` `commonTest`, `PlatformTest`, MockWebServer): article without chrome, plain text and errors, `parseUrl`, PDF by link | Gets Markdown expectations in W2. Site extractor tests point their base URL at MockWebServer. |

---

## Working rules

1. **Android stays shippable and desktop stays working.** Every step ends with the Android exit check
   (`./gradlew assembleDebug testDebugUnitTest testAndroidHostTest lint verifyRoborazziAndroidHostTest`),
   the JVM module tests, and `desktopTest` / `:desktop:test` for what it touches. Everything here is
   in `commonMain`, with one implementation for both platforms.
2. **No Android imports in shared code**; run `python3 scripts/desktop/check-android-imports.py`.
3. **Nothing is sent to an AI provider by this work.** Extraction only fills the editable box; the
   disclosure text does not change. Every request an extractor makes goes to the host the user typed,
   or that site's own API host (proposed defaults above).
4. **No live network in tests.** Real pages are saved as trimmed fixtures in
   `core/ingest/src/commonTest/resources/web/` and served by MockWebServer. Every extractor takes its
   base URL as a constructor parameter, so tests can point it at the server.
5. **Fixtures carry their licence.** Wikipedia text is CC BY-SA 4.0: list each fixture's source page,
   revision id and licence in `core/ingest/src/commonTest/resources/web/README.md`. Fixtures are not
   shipped in the app.
6. **Limits stay.** `MAX_BYTES` applies to every response an extractor reads, and `MAX_CHARS` to the
   final text. A site extractor makes at most a handful of requests (`SiteExtractor` doc: say how many).
7. **Docs move with the code.** At the end of each step tick the boxes here and update `CLAUDE.md`
   (a short "Web extraction" paragraph once W2 lands), `docs/ARCHITECTURE.md` (the `WebPageExtractor`
   line in §3 and §5.2's step 1, which says "plain text") and the ADR.
8. **Commit per step**, small diffs.

---

## Overview

| Step | Theme | Outcome | Rough effort |
|---|---|---|---|
| **W0** | Decisions, ADR, fixtures | ADR 0012; saved pages for tests | 0.5–1 day |
| **W1** | `MarkdownText` | HTML → Markdown converter; Markdown-safe cleanup; a chunker that keeps fences and tables whole | 2–3 days |
| **W2** | `SiteExtractor` seam | `WebPageExtractor` routes to site extractors, falling back to `GenericExtractor`, which now outputs Markdown | 1–2 days |
| **W3** | Wikipedia | `WikipediaExtractor` through the REST API: clean article, math, sections | 2–3 days |
| **W4** | Sections in Smart Extract | Pick which sections fill the box; a `#fragment` link preselects one | 2 days |
| **W5** | EPUB in Markdown | `EpubReader` uses `MarkdownText`, keeping its anchor offsets | 1–2 days |
| **W6** | Better generic extraction (optional) | Readability-style content scoring for sites without an extractor | 2–4 days |
| **W7** | Polish and QA | Real pages, docs, release notes | 1 day |

```
W0 ──► W1 ──► W2 ──► W3 ──► W4
              │      └────► W7
              ├────► W5
              └────► W6
```

W1 has no UI and can start before W0's ADR is final, once a few fixtures exist. W5 and W6 are
independent of Wikipedia and can be done in any order after W2.

---

## W0 — Decisions, ADR and fixtures

**Goal:** confirm the defaults and get test material.

- [ ] **(owner)** Confirm or change the proposed defaults above, especially: pipe tables in the
      source, URLs dropped, EPUB switching in W5, and whether sections (W4) are wanted in v1. *Not
      answered yet: ADR 0012 is written on the proposed defaults and marks each point to change.*
- [x] **ADR 0012 "Web extraction"** (`docs/adr/0012-web-extraction.md`), recording at least:
  - The two axes (where the content is / how it becomes text), and why the converter's output is
    for the model and the box, not the card renderer.
  - The `SiteExtractor` contract (W2): when it applies, the fallback, which hosts it may contact, and
    the limits.
  - The paywall and login stance.
  - The Markdown mapping table (W1), including what is dropped.
  - How to add a site (the checklist under "Adding a site extractor" below).
- [x] **Fixtures** in `core/ingest/src/commonTest/resources/web/`, trimmed by hand to what the tests
      need, each listed in the README with its source URL, date, revision and licence:
  - Wikipedia, Parsoid HTML from the REST API: an article with sections, an infobox, references, a
    navbox, a hatnote, a table and `<math>` (e.g. *Amygdala* for the plain case, *Fourier transform*
    for math); a disambiguation page; a non-English article (e.g. `ja.wikipedia.org`, for
    `WordCount` and section titles); a redirect.
  - Generic pages: a news-style `<article>`, a blog with `.entry-content`, a documentation page with
    `<pre><code>` and nested lists, and a page with no semantic markup at all (`div` soup).

**Done 2026-10-02** except the owner's confirmation. Fixtures and findings that shape W3 are in
`core/ingest/src/commonTest/resources/web/README.md`; ADR 0012 is `docs/adr/0012-web-extraction.md`. What the
saved pages changed in the plan:

- *Neural network* is an article with a hatnote, not a disambiguation page; the disambiguation fixture is
  *Mercury*. A page is a disambiguation page by `.dmbox-disambig` or a `Disambiguation_pages` category link.
- A formula is in the HTML twice (hidden `<math alttext>` and a fallback `<img alt>`); W1 must emit one.
- The REST API answers a redirect with a 307 to a relative `/w/rest.php/v1/page/<Target>/html?redirect=no`
  (the client follows it), and serves `Talk:`-style namespaces, so W3's `handles` must refuse them itself.
- Section ids are only stable within one revision: W4 identifies sections by position and title.
- The redirect fixture is `wikipedia-redirect.txt` (headers only); the generic fixtures are hand-written.

**Exit:** ADR written (**owner: review it**); fixtures in place.

## W1 — `MarkdownText` (`:core:ingest`, `commonMain`)

**Goal:** turn an HTML element into Markdown that a model and a person can both read.

API (suggested), a sibling of `ReadableText`, with the same callback so EPUB can use it later:

```kotlin
internal object MarkdownText {
    fun of(root: Element, onElement: (Element, Int) -> Unit = { _, _ -> }): String
}
```

Mapping (record the final version in the ADR):

| HTML | Markdown |
|---|---|
| `h1`–`h6` | `#`–`######` + text, on their own line, blank line around |
| `p`, `div`, `section`, … (the block set of `ReadableText`) | paragraphs separated by one blank line |
| `strong`, `b` / `em`, `i` / `s`, `del` | `**…**` / `*…*` / `~~…~~`; empty or whitespace-only emphasis dropped |
| `code` (inline) | `` `…` ``, with a longer backtick run when the code contains backticks |
| `pre` (optionally with `code class="language-x"`) | fenced block, language from the class; content exactly as written, not trimmed |
| `ul` / `ol` / `li`, nested | `- ` / `1. ` (honouring `start`), two spaces of indent per level |
| `blockquote` | `> ` on each line |
| `table` | GitHub pipe table (first row, or `thead`, as the header; `\|` escaped in cells; a cell's line breaks become spaces); a layout table (one column, or nested tables) is read as paragraphs |
| `a` | its text only |
| `img`, `svg`, `video`, `audio`, `picture` | dropped |
| `figcaption` | a paragraph |
| `math` with `alttext`, or an `img` with class `mwe-math-fallback-image-*` and TeX in `alt` | `\(…\)` inline, `\[…\]` when `display="block"` |
| `sup` / `sub` | text kept with no marker (`x2` is better than `x^2` guesses) — revisit if fixtures say otherwise |
| `br` | a line break inside the paragraph |
| `hr` | dropped (a paragraph break) |

Escaping: characters that would start Markdown by accident (`*`, `_`, `` ` ``, a leading `#`, `-`,
`>` or `1.` at the start of a text line) are escaped only where they would change the meaning,
so ordinary text stays readable.

- [x] `MarkdownText` with the mapping above, and `onElement` offsets that match the final text (the
      same contract `ReadableText` has today).
- [x] **Markdown-safe cleanup**: `TextCleanup.normalizeMarkdown` (or a flag on `normalize`) that leaves
      lines inside fences alone and keeps leading indentation; everything else as `normalize`.
- [x] **`TextChunker` keeps blocks whole**: never splits inside a fenced block or a pipe table (a block
      longer than a part is split by lines, the fence reopened in the next part, a table's header
      repeated); prefers to break **before a heading**, so a part starts with its section's title.
      Plain text (PDF, paste, dictation) chunks exactly as before: existing `TextChunkerTest` cases
      pass unchanged.
- [x] A `</source>` (or `<source>`) in page text cannot end the prompt's fence: check
      `CardGenerationPrompt` and escape it there if needed (this applies to plain text today too).
- [x] `MarkdownTextTest` (`commonTest`): one test per row of the mapping, nested lists, code with
      backticks, a table with a pipe in a cell, a layout table, math in both forms, escaping, offsets.

**Done 2026-10-02.** `MarkdownText.kt`, `TextCleanup.normalizeMarkdown` and the block-aware `TextChunker`
(`:core:ingest`), `CardGenerationPrompt.fenced` (`:core:ai`); tests in `MarkdownTextTest`, `TextChunkerTest`,
`CardGenerationPromptTest`. Choices the mapping left open:

- An ordered item's continuation lines indent by its marker's width (`1. ` is three spaces), a bullet's by two,
  so nested lists stay valid Markdown. An item that ends in a paragraph does not loosen the list (no blank line
  before the next item); two paragraphs *inside* an item are still separated by a blank line.
- Elements inside a table are all reported at the table's offset (the table is built cell by cell first).
  Elements the converter drops or reads whole (`img`, `pre`, `code`, math) are still reported, in document order.
- A pipe table is recognised by its `| --- |` delimiter row; a plain paragraph that starts with `|` is not one.
  A fence or table longer than a part is cut by lines, the fence reopened (the header repeated) in the next part.
  A part is cut before a heading only if that leaves at least a quarter of a part before it, or if the heading
  would otherwise end the part alone.
- `normalizeMarkdown` keeps a line's leading spaces; it does not turn a table row's empty cell into two spaces
  (`| 1 | |`). `<source>` / `</source>` in a source is written `&lt;source>` in the prompt.
- Zero-width characters (U+2060, U+200B …) Wikipedia puts around formulas are dropped.

**Exit:** the converter and the chunker are tested; nothing uses them yet.

## W2 — The `SiteExtractor` seam and `GenericExtractor`

**Goal:** one place where a site's own rules plug in, with today's behaviour (in Markdown) as the fallback.

```kotlin
/** A site with its own way of being read. Plain class, built in `:core:data`'s `dataModule`. */
interface SiteExtractor {
    /** Whether this extractor reads [url]. Decided from the URL alone, before anything is fetched. */
    fun handles(url: HttpUrl): Boolean

    /**
     * The page's text, fetched with [fetcher] (which applies the limits and the User-Agent).
     * Null means "not after all" (e.g. the API has no such page type), and the generic extractor runs.
     */
    fun extract(url: HttpUrl, fetcher: PageFetcher): SourceResult?
}
```

- [x] `PageFetcher` (public, because `SiteExtractor` takes it): the fetching half of today's `WebPageExtractor` (client with
      redirects, timeout, `MAX_BYTES` check, User-Agent, `Accept`). It returns the bytes, content type
      and final URL, or a `SourceResult.Failure` (`HttpError`, `Unreachable`, `TooLarge`).
- [x] `GenericExtractor`: today's `html()` branch, with `MarkdownText` and the Markdown-safe cleanup
      instead of `ReadableText` and `normalize`. The title logic (`og:title`, then `<title>`) is unchanged.
- [x] `WebPageExtractor(client, pdf, sites: List<SiteExtractor>)`: `parseUrl` → the first site whose
      `handles` is true → its result, or the generic path (PDF / HTML / text, as today) when there is none
      or it returns null. A site's **failure** is returned as is, not retried generically (decide in W0
      whether `NoText` from a site should fall back; the default is no, so a broken extractor is noticed).
- [x] The User-Agent names the app and a contact URL from `ProjectLinks` (Wikimedia's policy asks for
      that, and it is polite everywhere), e.g. `Mnemo/<version> (+<repo URL>)`. Keep a browser-like
      prefix only if W7 shows that sites refuse the plain one.
- [x] `dataModule`: `factory { WebPageExtractor(get(), get(), sites = listOf(...)) }`; the list is empty
      until W3.
- [x] `ExtractorsTest` updated to Markdown expectations (headings and lists from the fixtures), plus a
      test that a site returning null falls through and one that a site's failure is returned.
- [x] `SourceRepositoryTest` still passes.

**Done 2026-10-02.** `PageFetcher`, `SiteExtractor`, `GenericExtractor` (+ `PageText`, the shared "tidy, limit,
`NoText`" step) and the rewired `WebPageExtractor` (`:core:ingest`), `dataModule`'s `sites = emptyList()`; tests in
`ExtractorsTest` (the generic fixtures as Markdown, a fake site: handled, null falls through, failure returned,
fetching through the shared fetcher, the User-Agent). Choices the step left open:

- A site's `NoText` is returned, not retried generically (the default; ADR 0012 still asks the owner).
- The User-Agent is `Mnemo (+<repo URL>)` without a version: `:core:ingest` can't see the app's version, and a
  hard-coded one would go stale. `WebPageExtractor` takes a `userAgent` parameter if a host wants to add it. The
  old browser-like prefix is gone; bring it back only if W7 shows sites refusing this one.
- `text/*` files (not HTML) still go through the plain `TextCleanup.normalize`, as before.
- `WebPageExtractor.MAX_BYTES` is now `PageFetcher.MAX_BYTES`, kept under the old name.

**Exit:** links read as Markdown on both platforms; adding a site is adding one class to a list.

## W3 — `WikipediaExtractor`

**Goal:** a clean Wikipedia article: text, sections and math, without chrome, references or navboxes.

- [ ] `handles`: hosts `<lang>.wikipedia.org` and `<lang>.m.wikipedia.org`, with paths `/wiki/<Title>`
      and `/w/index.php?title=<Title>`. Not special pages (`Special:`, `Talk:`, `File:`, `Category:` …:
      the namespace prefix in the title; these return null and are read generically). The `#fragment`
      is kept for W4.
- [ ] Fetch `https://<lang>.wikipedia.org/api/rest_v1/page/html/<Title>` (title percent-encoded,
      spaces as `_`, mobile host mapped to the desktop one). The API follows redirects by default; the
      title shown is the target's. The base URL is a constructor parameter for tests.
- [ ] Remove before converting: `sup.reference`, `.mw-ref`, `ol.references`, `.reflist`, infoboxes
      (`table.infobox`), navboxes (`.navbox`, `.vertical-navbox`), hatnotes (`.hatnote`), `.metadata`,
      `.ambox`, `.mw-editsection`, `.noprint`, `style`/`link` elements, coordinates (`#coordinates`),
      `.thumb` images (keeping their captions is optional; decide with fixtures). Drop whole sections
      titled *References*, *Notes*, *Citations*, *Sources*, *Further reading*, *External links*, *See
      also* — by the section's `data-mw-section-id` and heading **in English and by structure** (a
      section that is only a reference list or a link list), because titles differ by language.
- [ ] Math: Parsoid puts TeX in `<math alttext>` / the fallback image's `alt`; `MarkdownText`'s math
      row covers it. Check `{\displaystyle …}` wrappers and strip them.
- [ ] Title from the page's `<title>` / `<h1>` (Parsoid has it in the head), with underscores as spaces.
- [ ] **Disambiguation pages** (`.mw-disambig` or the page property): fail with `NoText` and a detail
      saying it is a disambiguation page, or return the list as text; decide with the fixture (the
      default is to return the list: the user may still want it).
- [ ] Sections recorded for W4: each top-level `<section>`'s heading, level and text range.
- [ ] `WikipediaExtractorTest` with the W0 fixtures: no references or navbox text, math as `\(…\)`,
      headings kept, mobile and `index.php` URLs, special pages fall through, Japanese article, redirect.
- [ ] Added to the `sites` list in `dataModule`.

**Exit:** a Wikipedia link fills the box with a clean Markdown article, on both platforms.

## W4 — Sections in Smart Extract

**Goal:** a long article can be sent in parts the user chooses, like a book's chapters.

- [ ] Model: `SourceText.sections: List<SourceSection> = emptyList()`, with
      `SourceSection(id, title, level, start, end)` as offsets into `text`. Only extractors that know
      sections fill it (Wikipedia; later possibly the generic one, from headings).
- [ ] `SmartExtractViewModel`: when a link has sections, all are selected except the dropped kinds
      (already removed in W3) and the lead is included; the box holds the selected ones in order. A
      `#Fragment` in the link preselects only that section. Changing the selection rewrites the box
      (warn first if the user has edited it, like changing the chapter does).
- [ ] UI: a "Sections" button next to the link field when sections exist, opening a checklist in the
      chapter picker's style (title, word count); strings in `feature/create` `strings.xml`.
- [ ] Tests: ViewModel tests (`commonTest`) for the selection, the fragment and the edit warning; a
      screenshot of the picker (`androidHostTest`).

**Exit:** a Wikipedia article's sections can be picked; other links are unchanged.

## W5 — EPUB chapters in Markdown

**Goal:** books get the same structure as web pages.

- [ ] `EpubReader` uses `MarkdownText` and the Markdown-safe cleanup instead of `ReadableText`; the
      `onElement` offsets for table-of-contents anchors still land at the same places (the existing
      `EpubReaderTest` anchor tests are the check).
- [ ] The stub and part thresholds (30 words / 200 characters; 100 words / 600 characters) are counted
      on text **without** Markdown marks, so a chapter does not change kind because of a `#`.
- [ ] `ReadableText` is deleted if nothing uses it any more.
- [ ] `EpubReaderTest` and the book flow tests updated; word counts in tests re-read from the new text.

**Exit:** a book's chapters keep headings, emphasis, lists and tables.

## W6 — Better generic extraction (optional)

**Goal:** sites with no extractor of their own read better, without per-site code.

- [ ] Score candidate blocks Readability-style (text length, comma count, link density, class and id
      hints such as `content`/`article` vs `comment`/`sidebar`) instead of "the longest
      `CONTENT_ROOTS` element", keeping today's rule as a tie-breaker. No new dependency: a port of the
      idea, not of a library (if a library is ever wanted, check its licence against GPL-3.0 and add
      it to `NOTICE`).
- [ ] Headings in the generic result become sections (W4) when there are at least three.
- [ ] Each W0 generic fixture keeps its article and loses its chrome; no fixture gets worse.

**Exit:** the generic fixtures read at least as well as before, `div` soup noticeably better.

## W7 — Polish and QA

- [ ] **(owner)** Try about 20 real links on a phone and on the desktop: Wikipedia in three languages,
      a math-heavy article, news sites, blogs, documentation (MDN, Kotlin docs), a PDF link, a page with
      cookie walls. Note in a short log at the end of this file what read well and what didn't.
- [ ] Error texts: a disambiguation page and a special page say something useful.
- [ ] `CLAUDE.md`, `docs/ARCHITECTURE.md` (§3 tree line, §5.2 "plain text" → "Markdown"), ADR 0012
      updated; `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and
      `docs/release/release-notes.md` mention cleaner links and Wikipedia sections.

**Exit:** QA log written; docs current.

---

## Adding a site extractor (after W2)

A new site is one class in `core/ingest/src/commonMain/.../ingest/site/` plus fixtures and a test.
Before writing one, check:

1. **Is the generic extractor really not good enough?** Try the page with W6's scoring first.
2. **Public content only.** No login, no paywall workaround, no API key the user would have to supply
   (an API key would be a new secret and belongs behind `SecretStore`: a separate decision).
3. **Same organisation's hosts only** (the host typed, or that site's own API / raw host). Say which in
   the class's doc comment.
4. **The site's terms allow automated reading** of that content, and its API's User-Agent or rate
   rules are followed.
5. **Fixtures with licence notes**, a test per URL shape, and a case where `handles` is true but the page
   is not one it reads (it returns null).
6. Add it to the `sites` list in `dataModule`, the table below, and the ADR's list.

| Site | Status | Notes |
|---|---|---|
| Wikipedia (all languages) | W3 | REST API on the same host |
| Other MediaWiki wikis (Wiktionary, Wikibooks, Fandom, …) | idea | Same REST API on Wikimedia hosts; others by `<meta name="generator" content="MediaWiki">`, which needs a fetch before `handles` can say yes — would need a post-fetch hook on `SiteExtractor` |
| arXiv | idea | `arxiv.org/abs/<id>` → the HTML version (`arxiv.org/html/<id>`) when there is one, else the PDF; keeps LaTeX |
| GitHub | idea | A repository or a `blob/…/*.md` link → the raw Markdown from `raw.githubusercontent.com` (GitHub's own host) |
| Stack Exchange | idea | The public API (no key needed at low volume): question and accepted / top answers |
| YouTube and other video sites | **not planned** | Transcripts need an unofficial endpoint or an API key; their terms don't allow it |
| Paywalled news | **not planned** | Not circumvented (proposed defaults) |
