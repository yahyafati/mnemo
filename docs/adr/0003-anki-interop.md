# ADR 0003: How Anki decks map to Mnemo

- **Status:** Accepted
- **Date:** 2026-09-29
- **Resolves:** PROJECT_OVERVIEW §11, "Card template flexibility" (ROADMAP: decide by Phase 2)

## Context

Anki notes have any number of fields, and their cards come from HTML/CSS/JS templates, one card
per template (or per cloze number). Mnemo has three note kinds with two Markdown fields each
(Basic, Basic + Reversed, Cloze), rendered natively (ADR 0002). Users coming from Anki expect their
decks, review history and media to survive the move, and want to be able to go back.

Anki writes three package formats: legacy `collection.anki2` and `collection.anki21` (schema 11,
note types as JSON) and, since 2.1.50, `collection.anki21b` (zstd-compressed schema 18, note types
as protobuf, zstd media). Shared decks in the wild use all three.

## Decision

1. **No template engine.** Mnemo reads which fields a template shows and keeps those, not the
   template's HTML, CSS or JavaScript.
   - A cloze note type becomes **Cloze**: the `{{cloze:…}}` field is the text; fields the answer
     side adds become Extra.
   - A note type whose second template is the first one reversed becomes **Basic + Reversed**
     (per note: if the note has no reverse card, e.g. "optional reversed" left empty, it is Basic).
   - Anything else becomes **Basic** from its first template. The question side's fields make the
     front; the fields the answer adds make the back. Several fields are joined as paragraphs.
   - Cards of other templates are **skipped and counted** in the import summary.
2. **HTML becomes the Markdown subset.** Structure and emphasis are kept (line breaks,
   paragraphs, lists, bold, italic, strike, code, links, headings, quotes, tables as lines);
   colors, fonts, sizes and scripts are dropped. MathJax (`\(…\)`, `\[…\]`) is kept as math and
   Anki's `[latex]`, `[$]`, `[$$]` become MathJax. Text that would read as Markdown is escaped.
3. **Media** is stored by content hash and referenced as `media:<sha256>` (ADR 0004); `<img>` and
   `[sound:…]` point at it. A file missing from the package keeps its name.
4. **Scheduling** follows ADR 0001: FSRS memory state from Anki's own card data when present
   (Anki with FSRS on), else by replaying the review log, else estimated from the SM-2 interval
   and ease. Due dates are kept. Replay counts days as Anki does (calendar days from the
   collection's day boundary), and matches Anki's `compute_memory_state` (see `AnkiImportMapperTest`).
   Flags, suspension and the `marked` tag (as a star) carry over; burying doesn't.
5. **Duplicates** are recognized by Anki's note guid (a new `notes.guid` column) and skipped, as
   Anki's importer does. Existing decks are matched by full name; filtered decks are ignored and
   their cards go to their home deck.
6. **Export** writes the legacy schema-11 `.apkg`, which every Anki version and AnkiDroid imports,
   with Mnemo's three note types ("Mnemo Basic", "Mnemo Basic + Reversed", "Mnemo Cloze"). Cards
   carry both SM-2 fields and FSRS memory state. Mnemo also stores the note's original Markdown,
   id and exact timestamps (in `notes.data`) and each card's exact schedule (in Anki's `cd` custom
   data), so importing its own export is lossless. A package that went through Anki falls back to
   converting the HTML.
7. `:core:anki` is a pure-JVM module. SQLite is reached through the `androidx.sqlite` driver API
   (the framework driver on Android, the bundled one in JVM tests) and zstd through `zstd-kmp`
   (native, with Android and desktop builds). Its fixtures are packages written by real Anki
   (`core/anki/fixtures/make_fixtures.py`).

## Consequences

- Most Anki decks (Basic, Reversed, Cloze and simple custom types) import with their history.
  Decks built on templates (heavy CSS, JavaScript, multiple card types per note) lose those cards
  or their styling; the import summary says how many cards were left out.
- Custom note types beyond the three kinds would need their own ADR (Phase 6 adds Type-in and
  Multiple choice).
- Anki → Mnemo → Anki keeps content and schedules, but notes arrive in Anki as Mnemo's note types
  with the fields flattened as described above.
