# ADR 0011: EPUB books

- **Status:** Accepted
- **Date:** 2026-10-02
- **Context for:** `docs/epub/ROADMAP.md` (B0–B7); ADR 0006 (AI card creation); ARCHITECTURE §5.2

## Decision

A book (EPUB) becomes **one deck per chapter**, under a deck for the book. The decks start **empty**;
cards come from Smart Extract (ADR 0006), chapter by chapter, when the user asks.

### Empty decks first, cards on demand

Generating cards for every chapter at import was rejected. A 300-page book is 80,000–100,000 words,
about 70–90 AI requests at `TextChunker`'s 1,200 words, which costs real money on a hosted provider,
takes long on a local one, and ends in hundreds of cards nobody asked for to review. Importing is
instant and free, and the user picks the chapters worth turning into cards. Nothing leaves the device
without a user action, and Smart Extract's review queue stays the only gate before notes are saved.

### What a chapter is

The chapters are the **table of contents'** top-level entries: the EPUB 3 `nav` (`epub:type="toc"`), else
EPUB 2's `toc.ncx`, else one chapter per linear file of the reading order (spine). Details that decide
the result:

- An entry's text runs from its target (a file, or an `id` inside a file) to the next entry's target, so
  several entries can share one file, and a chapter can run over several files, including files the
  table of contents does not list.
- **Parts.** An entry whose title starts with *Part, Book, Volume, Unit, Act* or *Division*, that has
  entries under it, and whose own page has almost no text (under 100 words and 600 characters) is a
  title page: its children are the chapters, and the page's text is dropped. An entry with children and
  no link at all behaves the same. Any other entry with children is a chapter and its children are
  sections of it (their text stays in the chapter). Sections as decks of their own are out of scope.
  The word list is English-only on purpose; a book that names its parts otherwise gets its parts as
  chapters, which is usable.
- **Stubs.** A chapter of 30 words or fewer and 200 characters or fewer (a bare "Part One" heading) is
  folded into the chapter after it, as its first lines. Longer short chapters (a poem) stay.
- **Front and back matter** (cover, title and copyright pages, contents, dedication, acknowledgments,
  index, "about the author", Project Gutenberg's license) are classified by `epub:type`, the EPUB 3
  landmarks and the EPUB 2 `guide`, then by title. They are marked, not removed: the picker leaves
  them unchecked, because the heuristics are not perfect.
- A chapter's id is its position after folding. It is stable for the same file and reader version.
- The reader leaves titles it cannot find blank; the screens write "Chapter N" in the user's language.

### Text only

Images, MathML, SVG, page-break markers, note references, footnote bodies and ruby readings (`rt`, `rp`)
are dropped: the text of a chapter is what a reader would read in order. Formulas and diagrams are lost
and the UI says so. Words are counted as `SourceText` does, by whitespace, which counts a Japanese or
Chinese chapter as a handful of "words"; the reader's own thresholds therefore use characters as well,
but `TextChunker` does not, and Smart Extract for such a book needs a character-aware limit (open item
for roadmap step B5).

### DRM

Mnemo reads books without DRM only, and never tries to read protected content. The reader fails with
`SourceProblem.Drm` when `META-INF/rights.xml` or `sinf.xml` exists, or when `META-INF/encryption.xml`
lists a text document with an algorithm other than font obfuscation. Font obfuscation
(`http://www.idpf.org/2008/embedding` and Adobe's) is **not** DRM and is in many plain books: a book
with only that opens normally.

### Safety and limits

- The book is copied to the cache folder (the file API only gives a stream and a zip needs random
  access), read with `ZipFile`, and the copy is deleted whatever happens. Entries are read into memory
  by name and **never written to disk**, so a name like `../x` is only a name. References (`href`) are
  resolved against the file that holds them, percent-decoded, and refused when they leave the zip.
- Limits (`EpubLimits`): file 100 MB (checked while copying), 5,000 zip entries, 8 MB of *uncompressed*
  data read from any XML or XHTML entry (the declared size is not trusted: this is the zip-bomb guard),
  400 chapters, 4,000,000 characters in all, 200,000 per chapter. Past the entry and file caps the read
  fails with `TooLarge`; past the others it keeps what it has and sets `BookSource.truncated`.
- Content documents are parsed as HTML after empty elements (`<span/>`) are written out, because the HTML
  parser would keep such a tag open and swallow the rest of the page. Package files are parsed as XML.
- Book text and titles are never logged.

### Nothing is stored about the book

No new table, no copy of the book in app storage, no link between a deck and its book. A deck made from a
book is an ordinary deck. Generating cards later means picking the file again, and the chapter's deck is
preselected by its name (numbered `01 Title`, so the deck list keeps the book's order and equal titles do
not merge). Linking a deck to its book needs a schema change, a migration, a fixture database and a
decision on whether backups carry the book; it is deferred until it is asked for.

### No new dependency

`java.util.zip` and jsoup, which `:core:ingest` already has. The reader is `commonMain` code shared by
Android and the desktop. `NOTICE`, the FOSS dependency check and the F-Droid recipe do not change.

## Consequences

- Importing a book is cheap and safe to try; the cost is paid per chapter, with the number of requests
  visible before it is spent.
- The heuristics (parts, stubs, front matter) will be wrong for some books. Everything they decide is
  visible and editable in the picker, and they are covered by tests built from small in-memory EPUBs
  (`EpubBuilder`), one per rule.
- Books with fixed layouts, scans or only images have no text and fail with `NoText`.
