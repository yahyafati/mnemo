# ADR 0012: Web extraction

- **Status:** Accepted on the proposed defaults of `docs/web/ROADMAP.md` (2026-10-02). The owner has not
  confirmed them yet; the points marked **(owner)** below are the ones to change if they disagree. W1 and W2
  build on this, so a change after they land costs more.
- **Date:** 2026-10-02
- **Context for:** `docs/web/ROADMAP.md` (W0–W7); ADR 0006 (AI card creation); ADR 0011 (EPUB books);
  ARCHITECTURE §5.2

## Decision

Smart Extract's **Link** source gets better in two independent ways:

1. **How HTML becomes text.** One shared converter, `MarkdownText`, turns an HTML element into Markdown.
   Every extractor uses it, and EPUB does later (W5).
2. **Where the content is on a page.** A small seam, `SiteExtractor`, lets one site be read by its own rules,
   usually through its API (Wikipedia first). The generic extractor stays as the fallback for every other link.

They are separate on purpose. *Where* the article is depends on the site (a REST API, an `<article>`, a
`.entry-content`, a table of contents); *how* markup becomes text does not. Mixing them would put a copy of the
converter into every site.

### The Markdown is for the model and the box, not the card renderer

The text goes into the editable box and then to the AI provider as the source. Cards are written by the model;
nothing renders the box. So the converter is free to use constructs that the card Markdown subset
(`:core:model/markdown`, ADR 0002) does not have, notably GitHub-style pipe tables. What the model should
*write* in a card is still decided by `CardGenerationPrompt`, which already allows Markdown and `\(…\)` math.
**(owner)** Pipe tables in the source: the alternative is tables as lines of ` | `-separated cells, as today,
which loses the header row.

### The mapping (W1)

| HTML | Markdown |
|---|---|
| `h1`–`h6` | `#`–`######` on their own line, blank line around |
| block elements (`p`, `div`, `section`, …; `ReadableText`'s set) | paragraphs, one blank line between |
| `strong`, `b` / `em`, `i` / `s`, `del` | `**…**` / `*…*` / `~~…~~`; empty or whitespace-only emphasis dropped |
| inline `code` | `` `…` ``, with a longer backtick run when the code contains backticks |
| `pre` (+ `code class="language-x"`) | fenced block with the language; content exactly as written |
| `ul` / `ol` / `li`, nested | `- ` / `1. ` (honouring `start`), two spaces per level |
| `blockquote` | `> ` on each line |
| `table` | pipe table (first row or `thead` is the header; `\|` escaped; line breaks in a cell become spaces). A layout table (one column, nested tables, or `role="presentation"`) is read as paragraphs |
| `a` | its text only, **the URL is dropped** |
| `img`, `svg`, `video`, `audio`, `picture` | dropped |
| `figcaption` | a paragraph |
| MathML `math[alttext]`, or `img.mwe-math-fallback-image-*` with TeX in `alt` | `\(…\)`, or `\[…\]` for a block formula; the `{\displaystyle …}` wrapper is removed; **one** of the two copies Parsoid emits |
| `sup` / `sub` | text kept without a marker |
| `br` | a line break inside the paragraph |
| `hr` | dropped |

Characters that would start Markdown by accident (`*`, `_`, `` ` ``, a leading `#`, `-`, `>` or `1.`) are
escaped only where they would change the meaning.

**Dropped on purpose.** Link URLs cost tokens, are noise to a card, and the model must not cite them; images
cannot be sent as text, and saving them into `MediaRepository` is a separate feature. **(owner)** Both are easy to
reverse in one place if a link target or an image caption turns out to matter.

Sizes stay `WordCount`'s: Markdown marks (`-`, `##`) count as words. That adds a few percent and is not
corrected. Cleanup must not trim indentation or touch fenced blocks (a Markdown-safe variant of
`TextCleanup.normalize`), and `TextChunker` must not split inside a fence or a table and should prefer to break
before a heading; plain text chunks exactly as before.

### `SiteExtractor` (W2)

```kotlin
interface SiteExtractor {
    fun handles(url: HttpUrl): Boolean                              // from the URL alone
    fun extract(url: HttpUrl, fetcher: PageFetcher): SourceResult?  // null = "not after all"
}
```

- `WebPageExtractor` parses the link, asks the sites in order, and uses the first whose `handles` is true.
  If its `extract` returns **null**, or no site matches, the generic path runs (PDF / HTML / text as today).
- A site's **failure** is returned as is, not retried generically, so a broken extractor is noticed rather than
  hidden. **(owner)** Say so if `NoText` from a site should fall back instead.
- The site gets a `PageFetcher`, which applies the limits and the User-Agent; it does not build its own client.
  The User-Agent names the app and the repository (`Mnemo (+<repo URL>)`, from `ProjectLinks`; no version, which
  `:core:ingest` can't see), as Wikimedia's policy asks.
- **Which hosts a site may contact.** Only the host the user typed, or that site's own API or raw-content host
  run by the same organisation (Wikipedia's API is on the same host). No third-party readers, proxies,
  "reader mode" or scraping services: the page's text would reach a party the user never chose.
- **Limits.** `MAX_BYTES` (8 MB) applies to every response, `MAX_CHARS` (400,000) to the final text, the
  30 s call timeout to the whole extraction. A site makes at most a handful of requests and says how many in
  its doc comment. The client never sends cookies or credentials.
- Extractors are plain classes (no DI library in `:core:ingest`), built in `:core:data`'s `dataModule` and
  passed to `WebPageExtractor` as a list. Every extractor takes its base URL as a constructor parameter so tests
  can point it at MockWebServer.
- Nothing leaves the device for an AI provider because of any of this: extraction only fills the box, and the
  disclosure text does not change.

### Paywalls and logins

Not circumvented, the same stance as DRM in ADR 0011. A page that needs a login or a subscription gives what
the server sends to an anonymous client, or `NoText`. No cookies, no account, no archive-site detour, no
changed User-Agent to look like a search crawler.

### Wikipedia (W3) — from the saved pages

The extractor uses the **Wikimedia REST API** (`/api/rest_v1/page/html/<title>`, Parsoid HTML) rather than the
rendered page, whose chrome (navigation, tools, banners) is large and changes. What the fixtures
(`core/ingest/src/commonTest/resources/web/`, listed in their README) showed:

- A redirect title answers **307** with a relative `Location` to `/w/rest.php/v1/page/<Target>/html?redirect=no`
  on the same host. The client follows it, and the title comes from the page, not from the link.
- `Talk:`, `User:` and other namespaces are served too (`Special:` and unknown titles are 404), so `handles`
  must refuse a namespace prefix itself and let those links be read generically (returning null).
- Disambiguation pages are recognised by `.dmbox-disambig` or a `Disambiguation_pages` category link. A
  hatnote "for other uses…" does not make a page one (`mw-disambig` is the class of links *to* such pages).
  W3 returns the list (the user may still want it) and drops no section of such a page, since a disambiguation
  page is nothing but lists of links.
- **What W3 removes.** Citation markers and reference lists, infobox, navboxes, sidebars, sister-project boxes,
  hatnotes, message boxes, the hidden short description, "[edit]" and "[citation needed]", the table of contents,
  and **images together with their captions** (a caption such as "Coronal" says little without its picture; the
  converter drops images anyway). A section is dropped when its title is References, Notes, Citations,
  Sources, Further reading, External links, See also, Footnotes or Bibliography, or, in any language, when it has
  no subsections and holds nothing or only a list whose items are mostly links. That last rule would also drop a
  section that is a bare list of links to related topics, which has nothing to make cards from either.
- A numbered equation is a `role="presentation"` table: layout, so the formula and its number are paragraphs.
- A formula comes twice (hidden MathML + fallback image, both with the TeX in an attribute, wrapped in
  `{\displaystyle …}`); `MarkdownText` reads one.
- Sections are nested `<section data-mw-section-id>`; section ids are stable within one revision only, so a
  section is identified by its position and title, not by id.
- Reference, citation, notes, bibliography, see-also and external-link sections are dropped **by structure**
  (a section that is only a reference list or only links) as well as by English title, because the titles differ
  by language (the Japanese fixture names them 関連項目, 出典 and 外部リンク).
- The text is CC BY-SA 4.0. Mnemo reads it on the user's device for the user's own cards; the app stores no copy
  and ships none. Cards the user makes from it are theirs to share under the licence's terms; the app does not
  attach an attribution to them (**owner**: say if a source line should be added to the notes).

### Adding a site extractor

A new site is one class in `core/ingest/.../ingest/site/`, its fixtures and its test. Before writing one:

1. The generic extractor (after W6's scoring) really is not good enough for it.
2. The content is public: no login, no paywall workaround, no API key the user would have to supply (a key would
   be a new secret and belongs behind `SecretStore`: a separate decision).
3. It contacts only the host typed or the same organisation's API / raw host, and the class's doc comment says
   which.
4. The site's terms allow automated reading of that content, and its User-Agent and rate rules are followed.
5. Fixtures with licence notes in the README, a test per URL shape, and a case where `handles` is true but the
   page is not one it reads (it returns null).
6. It is added to the `sites` list in `dataModule`, to the roadmap's table and to this ADR's list below.

Sites: Wikipedia (W3, `WikipediaExtractor`). Not planned: video sites (transcripts need an unofficial endpoint or a key their terms do
not allow) and paywalled news (not circumvented).

### No new dependency

OkHttp and jsoup, already in `:core:ingest`; JSON, if a site needs it, through kotlinx.serialization, already
in the catalog. `NOTICE`, the FOSS dependency check and the F-Droid recipe do not change. The code is
`commonMain`, one implementation for Android and the desktop.

### EPUB

`EpubReader` switches to `MarkdownText` in W5, after the converter has proven itself on web pages. Its
stub and part thresholds are then counted on text without Markdown marks, so a `#` never changes a chapter's
kind, and the `onElement` offsets that find table-of-contents anchors must keep landing where they do now.

## Consequences

- Links keep their headings, emphasis, lists, code and tables, which the AI uses to make better cards and the
  user to see what is being sent.
- Markdown marks use a few percent of each request's tokens, and a model may copy them into a card; the
  prompt already says the cards' own Markdown subset.
- Every extractor is a promise to keep working when a site changes. Fixtures catch a regression in our code, not
  a site's redesign; W7 includes a pass on real pages for that. A site's failure is returned instead of
  falling back, so a broken extractor shows up as an error rather than as quietly worse text.
- Unlike AI requests (ADR 0005), these requests follow redirects and send no secret, so they go to any
  `http(s)` host the user types. The Link source's hint, "read on your device", stays true.
