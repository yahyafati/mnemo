# ADR 0015: Context-aware Smart Extract

- **Status:** Accepted on the proposed defaults (2026-10-18): the owner agreed to every point marked **(owner)**.
  C0–C4 are built ("As built" below); C5–C7 are open.
- **Date:** 2026-10-18
- **Context for:** steps C0–C7; ADR 0006 (AI card creation); ADR 0011 (EPUB books); ADR 0012 (web extraction);
  ADR 0014 (PDF pages); ARCHITECTURE §5.2

## The problem

Cards made from books and links are often about the material's *illustrations* rather than its *ideas*: the
anecdote an author opens a chapter with, the name of a patient in a case history, the year of an example. A book run
also produces chapter decks full of such cards for chapters that hold no ideas at all (a prologue, a story chapter).

There are two causes, and this ADR handles both.

1. **We ask for a quota.** `ExtractOptions.targetCards` (`:core:model`) turns the part's words into a number (one
   card per 70 words on Balanced, at least 3, at most 40) and `CardGenerationPrompt` says "Write about N cards". A
   textbook is dense enough to fill that; a narrative chapter or an essay is not, and the model makes up the
   difference with stories. Nothing in the prompt says what to leave out, and "use only facts from the source" and
   Comprehensive's "every fact worth remembering" make a date in an anecdote look like a fact worth a card.
2. **A part arrives with no context.** `TextChunker` cuts the box into parts of at most 1,200 words, and each is
   sent alone with the source's title and "part 4 of 9". The model can't tell whether the three paragraphs it got are
   the chapter's argument or an aside that illustrates it, nor which idea a story is there for. A part that starts in
   the middle of a section doesn't even have that section's heading.

## Decision

Smart Extract gets four changes, all in the path from the text box to the request. Sources, the box, the review
queue, deduplication and accepting cards stay as ADR 0006 describes.

1. **Fewer, better cards (C1).** The number becomes a ceiling, the prompt says to write cards about ideas and not
   about the examples that illustrate them, and an empty reply is a valid answer.
2. **Heading-aware parts with a path (C2).** The box's text is split along its Markdown headings: whole sections
   where they fit, small neighbouring sections together, a long section split inside, and every part says where it is
   (`Game Theory › 1. Introduction › 1.5 Nash Equilibrium`).
3. **A short overlap (C3).** A part that continues a section it didn't start gets the end of the part before it, marked
   as context and not as material for cards.
4. **A context summary (C4).** Before the first part of a long source, one request writes a short summary of the
   whole thing, including what its main examples illustrate; every part is sent with it.

The new settings are in a collapsed **Advanced** section of Smart Extract (C5), so the screen a new user sees is
the same as today's apart from one row.

### 1. Fewer, better cards (C1)

**A ceiling, not a quota.** The request becomes "Write **up to** N cards … Fewer is fine. Never pad: if this part
has nothing worth remembering, reply `{"cards": []}`." `targetCards` keeps its formula (it is a sensible ceiling and
the estimate) but its minimum stops forcing three cards out of nothing. The estimate under the box reads "Up to about
N cards".

**Ideas, not illustrations.** New rules in the system message:

> - Examples, anecdotes, stories, case histories and analogies are there to explain an idea: write the card about the
>   idea, not about the story. Don't ask about names, places, dates or other details that only belong to an
>   illustration.
> - An example may appear briefly on the back of a card when it helps recall the idea.
> - A famous example that is itself what the field remembers (an experiment, a landmark case) may get its own card.

The last rule matters: Pavlov's dogs and *Marbury v. Madison* are the content, not decoration. The test the
prompt gives is "would someone who understood the part but forgot the story be missing anything?"

Comprehensive changes from "Cover every fact worth remembering" to "Cover every idea and supporting detail worth
remembering". Balanced and Concise keep their wording.

**The switch.** *Skip examples and anecdotes* (Advanced, **on** by default for every source) controls the rules
above. Off, the prompt instead says "Examples and case histories in the source may get cards of their own", for the
reader who studies history, law or medicine and wants the stories. **(owner)** On for every source, rather than only
books and links: pasted notes rarely contain anecdotes, and one default is easier to explain.

**An empty reply is a valid reply.** Today `CardGenerationClient` sends `CardGenerationPrompt.REPAIR` whenever a
reply had no readable card. A reply that parses as `{"cards": []}` must now end the part with no cards and no
repair; only an unreadable reply is repaired. `GeneratedCardParser` reports the difference (it has seen the empty
`cards` array), and a fixture in `core/ai/src/test/resources/replies` pins it.

**Telling the user.** A run that ends with no cards says there was nothing worth remembering in the text, as is
common for narrative and front matter. In a book run this text is in the banner, whose Next button moves on as it does today. The
chapter's deck stays (ADR 0011 makes deck creation deliberate); **(owner)** offering to delete the empty chapter
decks at the end of the run is a possible later change, not part of this plan.

### 2. Heading-aware parts with a path (C2)

`TextChunker` already starts a part at a heading when it can (`cut()`, `HEADING_FRACTION`), but it treats the text as
a stream of paragraphs: it knows nothing about levels, so it can join the end of one chapter to the start of the next
and it can't tell a part where it is.

**The outline.** `SourceOutline` (`:core:ingest`) reads the box's Markdown headings into a tree: each node has a
title, a level, its own body (the text before its first subsection) and its subsections. Fenced blocks are skipped,
as in `MarkdownSections`, which keeps its own job (the section picker and its three-heading minimum). A text with no
headings is one node, and everything below works on it as it does today.

**Packing.** `SourcePlanner.plan(text, root, maxWords = 1_200)` walks the tree in order and fills parts:

1. A section (heading, body and subsections) that fits in the room left in the current part goes in whole.
2. A section that doesn't fit there but fits in an empty part closes the current part and starts the next.
3. A section bigger than a part is opened up: its body and then its subsections are packed the same way, one level
   down, with its title on their path.
4. A body bigger than a part (a long section without subsections) is cut by `TextChunker.chunk`, which already keeps
   paragraphs, fences and tables whole where it can. Those pieces are *continued* parts of that section.
5. **Siblings stay together, cousins don't.** When the next section has a different parent than what the current part
   holds and the part is already at least half full, the part is closed instead of taking the start of another
   chapter.
6. A tiny body (a heading with one sentence before its first subsection) stays with its first subsection, and a short
   tail joins the part before it (today's `TAIL_FRACTION`).

The rules are deterministic and need no request. A part is a `SourcePart(text, path, continued, overlap)` and
`CardGenerationRepository.split` returns those instead of strings; `ExtractRequest.parts`, the queue's `chunkIndex`
and `RegenerateCardUseCase` carry the whole part, so a regenerated card is asked with the same path (and the same
overlap and summary) as the first.

**The path.** A part's path is its root, then the headings that contain all of it: the common ancestor of the
sections it holds, or, for a continued part, the section it continues, marked "(continued)". The headings of the
sections it holds are in its text, so they aren't repeated.

| Source | Root of the path |
|---|---|
| Book chapter (EPUB, including a book run) | the book's title › the chapter's title |
| Link | the page's title |
| PDF, file | the document's title, else the file name |
| Pasted, dictated | none (only the headings in the text) |

A first heading that repeats the root (an EPUB chapter that starts with its own title, a page with its title as an
`h1`) is not repeated. Titles are cut at 80 characters and the path at six levels, keeping the first and the last.

Example: a `Game Theory` source whose chapter 1 has sections 1.1–1.6, of which 1.5 is long.

| Part | Holds | Path sent |
|---|---|---|
| 1 | 1.1–1.4 (siblings, together) | Game Theory › 1. Introduction |
| 2 | first half of 1.5 | Game Theory › 1. Introduction › 1.5 Nash Equilibrium |
| 3 | second half of 1.5 | Game Theory › 1. Introduction › 1.5 Nash Equilibrium (continued) |
| 4 | 1.6 | Game Theory › 1. Introduction |
| 5 | start of chapter 2 | Game Theory › 2. Mixed Strategies |

**PDFs.** A PDF's text layer has no Markdown headings, so C2 alone changes nothing for it. C6 writes the PDF's
bookmarks into the text it reads (`## <bookmark title>` where a bookmark's page starts), which gives PDFs with
bookmarks the same outline. **(owner)** The headings are visible and editable in the box, like any other text.

### 3. A short overlap (C3)

A part that starts in the middle of a section (rule 4 above) is sent with the end of the part before it: its last
paragraphs, up to `OVERLAP_WORDS` (100 words; a single paragraph longer than that is cut at a sentence). It goes in its
own fence, before the source:

```
The end of the text just before this part, already covered. It is context only: write no cards from it.
<before>
…
</before>
```

It is not counted against the part's 1,200 words. Cards from the part before are already in the `avoid` list, and
duplicates are still dropped locally, so the overlap can't add cards twice.

*Overlap* (Advanced) is **Within sections** by default (only continued parts get one), or **Always** (every part
after the first gets the end of the part before it, across headings too), or **Off**.

### 4. A context summary (C4)

The path says *where* a part is; it doesn't say what the source argues or what a story is there for. A short summary
of the whole source does, and it is cheap compared with what it informs.

**What it is.** One request, before the first part, on the Extract route (`AiTask.Extract`, usage logged there).
`SourceSummaryPrompt` (`:core:ai`) asks for plain Markdown and never JSON, at most 150 words:

- what the source is and what it is about, in two or three sentences;
- its main argument or the ideas it builds up, in order;
- **its main examples and what each illustrates** ("the shipwreck story illustrates loss aversion"), so a part
  that holds only the story can make a card about loss aversion, or none.

The source is fenced as material, as in every prompt, and so is the summary when it is sent on: it is model output
derived from the source and is just as untrusted.

**What it reads.** The whole text when it has at most `SUMMARY_INPUT_WORDS` (3,000) words. A longer text is sent as
a **skim**: the outline (every heading) plus the opening paragraph of each section, the lead and the last section
getting a larger share, until the budget is used. A text without headings is skimmed as its first 1,500 words, its
last 500 and evenly spaced paragraphs between. The skim keeps the cost of the summary flat however long the source
is.

**When.** *Context summary* (Advanced) is **Auto** by default: a summary is made when the source has at least three
parts, since a one-part source is its own context and a two-part one gains little for the cost. **Off** never makes
one. **(owner)** The threshold of three parts and the budget of 3,000 words.

**Cost.** For a 10,000-word chapter (9 parts) the summary adds at most 3,000 words of input and about 150 words to each
part: roughly a third more input tokens and no more output beyond the summary itself. Every count of requests the
app shows (the hint under the box, the book run's confirmation, `feature_create_batch_confirm_requests`) includes the
summary.

**How it is sent.** Every part's request gets it before the path:

```
About the whole source, for context only (write cards only from <source>):
<context>
…
</context>
```

**In the app.** The summary is cached in `SmartExtractViewModel`, keyed by the text, the route's model and the
language, like `PageTranscriptionCache`, so Resume, Regenerate and a second Generate on the same text don't ask
again. It is shown above the review queue as a collapsed **Context** card (its first line, then Show), with Edit and
the Report button (`ReportAiButton`, a new `AiReportKind.ContextSummary`), since every piece of AI output needs one.
An edited summary is used for the parts not yet sent and for Regenerate. **(owner)** Showing and editing it: the
alternative is to keep it invisible, which is simpler but leaves a wrong summary uncorrectable.

**Failure.** A failed summary stops the run before any part, like a failed part, with Retry and a second button,
**Generate without context**. Auth and rate-limit errors are not retried, as everywhere.

**Books.** In a book run each chapter is a source of its own and gets its own summary when it is long enough; the
path already names the book and the chapter. A summary of the whole book (from the chapter summaries as the run goes)
is not part of this plan.

### 5. The Advanced section (C5)

Smart Extract's options card keeps its main controls in view: deck, density and card styles. Below them is one row,
**Advanced**, collapsed by default:

| Setting | Values | Default |
|---|---|---|
| Skip examples and anecdotes | on / off | on |
| Context summary | Auto / Off | Auto |
| Overlap between parts | Within sections / Always / Off | Within sections |
| Card language | Same as source / a language | Same as source |

When collapsed and something isn't at its default, the row says what ("Overlap: always · No summary"), so a changed
setting is never hidden. The Advanced settings are remembered (`UserSettings.extractAdvanced`, DataStore), because
they are preferences rather than choices per source; density and card styles stay per screen as today.
**(owner)** Moving the language picker into Advanced: it is rarely changed, and it frees a row on a phone.

The Advanced section is in the screenshot tests (collapsed in the default baseline, open in one of its own) and its
rows follow the D7 rules: one focus stop each, tooltips on the desktop.

### Privacy and the disclosure

Nothing new leaves the device. The summary request sends the source text (or part of it) to the provider the user
already accepted for Smart Extract, and the generation requests send a summary derived from it. The disclosure string
(`feature_create_disclosure_content`: "the source text you entered …") stays true; C4 checks the Smart Extract wording
in `docs/release/privacy-policy.md` and leaves it alone if it says the same.

### The prompt, put together

A part's user message after C1–C4:

```
Write up to 18 cards from the source below. Cover the main ideas and the key supporting details. Fewer is
fine: if it has nothing worth remembering, reply {"cards": []}.

These cards already exist. Don't repeat them:
- …

About the whole source, for context only (write cards only from <source>):
<context>
…
</context>

Where this part is: Game Theory › 1. Introduction › 1.5 Nash Equilibrium (continued)

The end of the text just before this part, already covered. It is context only: write no cards from it.
<before>
…
</before>

Source "Game Theory" (part 3 of 9):
<source>
…
</source>
```

`fenced` escapes `<context>`, `<before>` and `<source>` tags (opening and closing) in every fenced block, not only
`<source>`.

### Rejected

- **A second request that rates and filters the cards.** It doubles the cost and the wait, and it discards cards that
  were paid for. Not asking for filler is cheaper and works better; the review queue is already the human filter.
- **On-device detection of anecdotes** (quotes, names, dates). Brittle, and in history or biography the names and
  dates are what is learned.
- **Embeddings or retrieval** to give each part related passages. A dependency and an index for what a 150-word
  summary and a path mostly do.
- **Changing the part size.** 1,200 words stays (ADR 0006): it fits every model, and C2 already makes parts follow
  the text's structure.

## Consequences

- Fewer cards for narrative sources, and some parts with none. The estimate becomes a ceiling, and the wording says so.
- A long source costs one more request and about a third more input tokens with the summary on; the counts and the
  book run's confirmation show it.
- `CardGenerationRepository.split` returns `SourcePart`s; the queue and Regenerate carry them. The golden requests in
  `core/ai/src/test/resources/golden` change in C1, C2, C3 and C4, each on purpose.
- Better parts for every source with headings: EPUB chapters and links today, PDFs with bookmarks after C6.
- Card quality has no automated test; C0's fixtures and C7's pass are how a change is judged.

## Roadmap

Each step leaves Android shippable and the desktop working, and ends with the exit check plus `desktopTest` and the
JVM modules' `test`. Steps that change the prompt update `RequestGoldenTest`'s files.

| Step | What | Depends on |
|---|---|---|
| C0 | Fixtures and a baseline | — |
| C1 | Ceiling, the illustration rule, empty replies; the Advanced section with its first switch | C0 |
| C2 | `SourceOutline`, `SourcePlanner`, `SourcePart` and the path | C1 (the prompt) |
| C3 | Overlap | C2 |
| C4 | Context summary | C2 (the skim reads the outline) |
| C5 | Advanced section complete: persistence, collapsed summary line, language | C1, C3, C4 |
| C6 | PDF bookmarks as headings | C2 |
| C7 | QA and polish | all |

C3, C4 and C6 can be built in parallel once C2 has landed; they touch different files except for the prompt, where
each adds its own block.

### C0: fixtures and a baseline

- A handful of anecdote-heavy texts that may be committed, and a dense textbook-like control, with a README naming
  each one and its license.
- Markdown fixtures for the planner: nested headings, a long section without subsections, fences and tables under
  headings, no headings at all, an EPUB chapter that repeats its title.
- `scripts/qa/extract-checks.py`: sends a fixture through a provider the owner configures (an OpenAI-compatible URL
  and key from the environment, never committed), writes the cards to a file and counts them per part. The owner
  labels each card *idea* or *illustration* by hand; the counts before any change are the baseline, in
  `docs/release/qa.md` §2.1.

### C1: fewer, better cards

- `CardGenerationPrompt`: "up to", "fewer is fine", the empty reply, the illustration rules behind
  `ExtractOptions.skipIllustrations`, Comprehensive's new wording.
- `GeneratedCardParser` reports an empty `cards` array; `CardGenerationClient` doesn't repair it. Reply fixture
  `empty-cards.json` and its count (0) in `GeneratedCardParserTest`.
- `SmartExtractViewModel`: a run with no cards says there was nothing to learn, in the queue and in the book run's
  banner.
- The estimate's wording ("Up to about N cards").
- The Advanced row in the options card, collapsed, with *Skip examples and anecdotes*. Not yet persisted.
- Tests: golden files, parser, ViewModel (empty run, book run banner), screenshot baselines re-recorded.

### C2: heading-aware parts and the path

- `SourceOutline` and `SourcePlanner` in `:core:ingest`, `SourcePart` in `:core:model`; `TextChunker.chunk` stays as
  the splitter of a long body.
- `CardGenerationRepository.split` → `List<SourcePart>`; the root comes from the source (`SmartExtractUiState`'s title,
  the book and chapter in a book run). `ExtractRequest`, `GenerationRequest`, the queue items and
  `RegenerateCardUseCase` carry the part.
- The prompt's "Where this part is" line.
- Tests: `SourceOutlineTest`, `SourcePlannerTest` (each packing rule, the example table above, CJK word counts,
  determinism), `TextChunkerTest` unchanged and green, golden files, Regenerate keeps the path.

### C3: overlap

- `SourcePlanner` fills `SourcePart.overlap` per the setting; the `<before>` block; `ExtractOptions.overlap`
  (`Overlap { WithinSections, Always, Off }`).
- The Advanced row.
- Tests: planner (overlap sizes, sentence cut, nothing before part 1), golden file, Regenerate sends the same overlap.

### C4: context summary

- `SourceSummaryPrompt` and `SourceSummaryClient` (`:core:ai`), `SummaryInput` (whole text or skim) next to the
  planner, `SourceContextRepository.summarize` (`:core:data`, logs usage), `SummarizeSourceUseCase` (`:core:domain`:
  decides Auto from the number of parts).
- `GenerateCardsUseCase` takes the summary and emits a `SummaryStarted` / `SummaryReady` / `SummaryFailed` event
  before the parts; the ViewModel's cache; request counts in every hint and confirmation.
- The Context card above the queue: collapsed, Show, Edit, Report (`AiReportKind.ContextSummary`). Retry and Generate
  without context on a failure.
- The Advanced row. Check the Smart Extract wording in `docs/release/privacy-policy.md`.
- Tests: summary golden file, skim (budgets, no headings, lead and end), ViewModel (cache hit on Resume and Regenerate,
  edit used by later parts, failure, Off), Context card screenshot.

### C5: the Advanced section complete

- `UserSettings.extractAdvanced` (DataStore) and its repository methods; the ViewModel reads and writes it.
- The collapsed row's summary of non-default settings; the language picker moved in (if the owner agrees).
- Tests: persistence round trip, screenshot baselines (collapsed, open, a changed setting), keyboard focus on the
  desktop (`desktopTest`).

### C6: PDF bookmarks as headings

- When the text layer of selected pages is read, insert `## <title>` (level from the bookmark) at the start of each
  bookmark's page, in page order; a bookmark that points into the middle of a page goes at that page's start.
- Only for the Text and Auto modes' text layer; AI-transcribed pages already come back as Markdown with headings.
- Tests: with `core/ingest/src/commonTest/resources/pdf`'s outlined fixture, headings in the right places, none for a
  PDF without bookmarks.

### C7: QA and polish

- Rerun C0's fixtures with every step on; record cards per part and the share of *illustration* cards against the
  baseline in `docs/release/qa.md` §2.1, with a book run on a real book and a long Wikipedia article.
- Tune the numbers this ADR guesses (overlap size, summary threshold and budget, the half-full rule) from what the
  pass shows, and write them down in "As built (C7)".
- Docs: CLAUDE.md's Smart Extract entries, ARCHITECTURE §5.2, this ADR's "As built" sections, and the store
  listing if it describes the card estimate.

## As built (C0, 2026-10-18)

- The texts are `scripts/qa/extract/*.md`, written for Mnemo (GPL like the rest; invented people and stories) rather
  than taken from Project Gutenberg: four of them, a popular-science chapter full of anecdotes, a novel's prologue with
  nothing to learn, a dense textbook section as the control, and an essay with `#`–`###` headings. Their README says
  what good cards look like for each. The planner's own Markdown fixtures come with C2, next to its tests.
- The requests come from the app's code, not from a copy of the prompt in Python: `ExtractRequestDump`
  (`:core:data` `desktopTest`) splits each text with `splitSource` and builds each part's prompt with `cardsPrompt`,
  the two functions `DefaultCardGenerationRepository` now uses itself, and writes the request bodies and a
  `manifest.json`. It skips itself unless `MNEMO_EXTRACT_DUMP` is set; the variables aren't Gradle inputs, so it runs
  with `--rerun`. The cards of earlier parts (`avoid`) are left out, since they depend on the replies.
- `scripts/qa/extract-checks.py` runs the dump through Gradle (`requests`), sends the bodies to an OpenAI-compatible
  server with the key from `MNEMO_QA_API_KEY` and writes `cards.csv` with an empty `label` column (`run`), and counts
  cards and labels (`summary`). Results go in `docs/release/qa.md` §2.1. C0 and C1 landed in one commit and the owner
  skipped the baseline run, so the first recorded run is after C1; later steps are compared with it.
- What the dump showed before any change: today's chunker cuts `essay-with-headings.md` into 343 and 1,320 words,
  before `### 1.5`, the kind of split C2 is for.

## As built (C1, 2026-10-18)

- `ExtractOptions.skipIllustrations` (default true) switches the three rules on; off, the one "may get cards of their
  own" rule is sent instead. The request says "Write up to N cards", then "Fewer is fine: never pad with minor details.
  If there is nothing worth remembering, reply with {"cards": []}." `targetCards` and its formula are unchanged; only
  the wording makes it a ceiling. `OUTPUT_FORMAT` is unchanged, so Co-Author's requests are byte for byte the same.
- `GeneratedCardParser.isEmptyAnswer` is true after `finish` when the reply, as JSON, is `[]` or an object whose
  only key is `cards` holding `[]` (fenced or after a `<think>` block too). An object with other keys, cards that
  don't parse, prose or nothing at all are still repaired. Fixtures `empty-cards.json` and `empty-cards-fenced.txt`.
  Co-Author goes through the same client and gets the same rule.
- "Nothing to learn" is a state, not a one-off message: `SmartExtractUiState.nothingToLearn` is true when the run is
  `Done` with no card added, the queue is empty and nothing was dropped as a duplicate or unusable (so the model
  proposed nothing). The queue's empty hint and the book run's banner say so; the banner's button is Next chapter as
  for any done chapter. A run of page images whose pages were all blank sends nothing and shows the same hint, which
  is still true there.
- The Advanced section is a row under Card language (`AdvancedOptions` in `SmartExtractScreen.kt`): closed by
  default (`SmartExtractUiState.showAdvanced`, `ToggleAdvanced`), one `SwitchRow` for now
  (`SetSkipIllustrations`), and, closed, the settings not at their default in the row ("Examples get cards"). It is not
  remembered yet (C5). The estimate on the Generate button reads "Generate up to N cards".
- Tests: `CardGenerationPromptTest`, `GeneratedCardParserTest`, `CardGenerationClientTest.anEmptyListGetsNoRepair`,
  the re-recorded `smart-extract-*` golden files, three `SmartExtractViewModelTest` cases, two `SmartExtractScreenTest`
  cases, and the screenshot `create_extract_advanced_light.png` (five baselines re-recorded for the new row).

## As built (C2, 2026-10-18)

- `SourceOutline` (`:core:ingest`) reads the headings into a tree of `Section`s: `title`, `level`, `body` (the text
  before the first subsection), `own` (the heading line and `body`), `text` (the whole section) and `children`, each
  with its `WordCount`. Fences are skipped with the same patterns as `MarkdownSections`, which is unchanged. A title
  loses closing hashes, link targets and emphasis and code marks; it is not `MarkdownText.plain`, which would take `1.`
  off `1. Introduction` as a list marker. A level that skips one (`#` then `###`) is still a subsection.
- `SourcePlanner.plan(text, root, maxWords = 1_200)` follows the six rules with these numbers and readings:
  - "tiny" is a body of at most an eighth of a part (150 words) before a first subsection. Such texts wait and go in
    front of whatever comes first inside (several in a row when a chapter and its first section both open with a
    line), and may take that part over the limit by their size rather than have the section cut. The part is where
    that first thing is: the tiny texts are only the way into it.
  - "half full" is half a part (600 words); "a different parent" means outside the deepest section the part already
    holds, so siblings and the children of an opened section join whatever the fill, and a cousin or an uncle only
    joins a part under half full.
  - A cut text (rule 4) is `TextChunker.chunk` of the section's own text (with any tiny texts in front). Its first
    piece may join the current part; every later piece starts a part and is `continued`, and the last one takes what
    follows under the usual rules. A text without headings is the root's own text, so its parts are exactly
    `TextChunker.chunk`'s, the ones after the first `continued`, with only the root as their path.
  - The tail fold obeys rule 5 too. Without that, the dump's `essay-with-headings.md` still came out as 343 and 1,320
    words, with chapter 2 (148 words) folded onto the end of chapter 1; it is now 343, 1,172 and 148 words, with the
    paths `Game Theory › 1. Introduction`, `Game Theory › 1. Introduction` and `Game Theory`.
  - A part's path is the root, then the titles of the deepest section that holds all of it: the parent of a section
    that is in whole (whose heading is in the text), the section itself for its own text or a piece of it. The root's
    titles are tidied (spaces, empty ones and repeats dropped), every title is cut to 80 characters with "…", a first
    heading equal to the root's last entry (ignoring case) is left out, and a path longer than six keeps its first
    entry, "…" and its last four.
- `SourcePart(text, path, continued)` (`:core:model`) has no `overlap` yet: C3 adds it. `CardGenerationRepository.split`
  and `GenerateCardsUseCase.split` take the root (default none); `GenerationRequest.source` replaces `text` (still a
  getter), and `ExtractRequest.parts`, `QueueItem.part` and `RegenerateCardUseCase(source = …)` carry the part. A
  regenerated card is sent with its part's path, as before with `parts = 1` (no "part N of M").
- The root is `SmartExtractUiState.pathRoot()` in `SmartExtractViewModel`: the book's and the chapter's titles when a
  chapter is in the box, else the state's `title` (a link's page title, a PDF's title or file name, a file's name),
  else none. Pasted and dictated text has no title, and emptying the box forgets it. A run of page images gives each
  group's text layer the root alone as its path.
- `CardGenerationPrompt` takes `path` and `continued` and writes `Where this part is: A › B › C (continued)` after
  the list of known cards and before the source line. The line is left out when there is no path, and when the path
  is only the title the source line names anyway and the part isn't continued (a link or PDF without headings). The
  titles come from the source, so they are escaped like it. Only `smart-extract-part.json` changes among the golden
  files (it is now a continued part with a three-level path); the others have no path and are byte for byte the same.
- `ExtractRequestDump` splits each QA text with its title as the root, and `manifest.json` gains `path` and
  `continued`. `FakeCardGenerationRepository` has a `splitter` (default: "---" lines, the root as every part's path)
  and records the `roots` it was given.
- Tests: `SourceOutlineTest` (9), `SourcePlannerTest` (each rule, the Game Theory table above, the root, title and
  depth limits, CJK sizes, fences and tables, determinism, a short last chapter), `TextChunkerTest` unchanged,
  `CardGenerationPromptTest` (the line, its place, when it is left out, escaping), `RequestGoldenTest`,
  `AiGenerationRepositoriesTest`, `SmartExtractUseCasesTest` (every part and Regenerate send their path),
  `SmartExtractViewModelTest` (Regenerate keeps the part and path after the box changed; a PDF's and pasted text's
  roots) and `SmartExtractBookTest` (book › chapter).

## As built (C3, 2026-10-18)

- `Overlap { WithinSections, Always, Off }` and `ExtractOptions.overlap` (default `WithinSections`) are in `:core:model`;
  `SourcePart(text, path, continued, overlap = "")` gained the text. `SourcePlanner.plan(text, root, maxWords, overlap)`
  takes the setting as a parameter, so a plan is still a pure function of its arguments; it never changes where the
  text is cut, only what each part carries. `CardGenerationRepository.split` and `GenerateCardsUseCase.split` take it
  after the root (default `WithinSections`), and the ViewModel passes `options.overlap` when a run starts. The
  request-count hint under the box doesn't re-split on a change of setting: the overlap never changes the number of parts.
- `SourceOverlap.tail(text, maxWords = OVERLAP_WORDS)` (`:core:ingest`) is the end of a part: its last paragraphs
  (blank-line separated) that fit in 100 words by `WordCount`, never a part of a paragraph unless the last one alone
  is over the limit. Then it is cut at a sentence (`TextChunker.SENTENCE_END`, now `internal`, so Japanese and Chinese
  full stops count), and a single sentence over the limit is cut at a word, or a character in Japanese and Chinese, with
  "…" in front. Fences and tables are not treated specially: a code block with blank lines in it can be cut inside, which
  is harmless for context that only tells the model what came before.
- Which parts get one: `WithinSections` the continued ones (a text without headings: every part after the first),
  `Always` every part after the first, `Off` none. Nothing comes before part 1. The overlap is the tail of the previous
  part's `text` (never of its own overlap), and isn't counted against the part's 1,200 words.
- `CardGenerationPrompt(overlap = …)` writes the `<before>` block of the ADR after the "Where this part is" line (or
  after the avoid list when there is no path) and before the source label, and leaves it out when blank. `fenced()`
  now escapes `<source>` and `<before>` tags, opening and closing, in every fenced text, the path and the overlap
  included. Only `smart-extract-part.json` changes among the golden files (its part now has an overlap).
- The part in the queue carries the overlap (`QueueItem.part`), so Regenerate sends the same overlap as the first
  request, even if the setting was changed afterwards, as it already did for the path.
- Advanced › **Overlap between parts** (`OverlapPicker` in `SmartExtractScreen.kt`) is an `OptionDropdown` with a line
  saying what the choice does, below *Skip examples and anecdotes*: one focus stop, as the other fields. Closed, the
  row names a changed setting ("Overlap: always", "Overlap: off"). Not persisted yet (C5). Only
  `create_extract_advanced_light.png` changes among the screenshot baselines (the collapsed default has nothing new).
- Tests: `SourceOverlapTest`, `SourcePlannerTest` (each mode, nothing before part 1, no change to the cut, no headings,
  limit and size), `CardGenerationPromptTest` (the block, its place, escaping), `RequestGoldenTest`,
  `SmartExtractUseCasesTest` (Regenerate sends the part with its overlap), `SmartExtractViewModelTest` (the setting reaches
  the split; Regenerate keeps the overlap after the setting and the box changed) and `SmartExtractScreenTest`.

## As built (C4, 2026-10-18)

- `SourceSummaryPrompt` and `SourceSummaryClient` (`:core:ai`) are `PageTranscriptionPrompt`'s shape: plain Markdown, no
  schema, streamed when the model can. The system message asks for at most 150 words in the ADR's three parts and
  names the language when the cards have one (the summary is written in it, which is why the cache is keyed by it);
  `clean` takes off a code fence and cuts a reply at `MAX_CHARS` (1,500) at a sentence. A skim is announced in the user
  message ("outline and the opening of each section, with […] where text was left out"). `PromptFence.escape`
  (internal) is the one place that escapes `<source>`, `<before>` and `<context>` tags; `CardGenerationPrompt` has a
  `context` field that writes the `<context>` block between the list of known cards and the path line, and leaves it
  out when blank. Goldens: `smart-extract-part.json` now carries a context, and `smart-extract-summary.json` is the
  summary request.
- `SummaryInput.of(text, budget = 3,000)` (`:core:ingest`) is the whole text, or a skim. With three or more headings
  it is the outline (as many headings as fit in a third of the budget, spread evenly if there are more) plus each
  section's opening: its first paragraphs up to the section's share of the rest, a first paragraph that is longer cut at
  a sentence, `[…]` after anything left out. The lead (text before the first heading) weighs three shares and the
  last section with a body two; if a share would be under 25 words, sections are thinned out evenly (the lead and the
  last kept). Without headings (or with fewer than three) it is the first 1,500 words, the last 500 and pieces of
  about 100 words from the middle of evenly spaced stretches between, joined by `[…]`. A skim is always within the
  budget (tested with 2,000 headings, one huge paragraph and Japanese).
- `SourceContextRepository.summarize(route, SummaryRequest(text, title, language))` (`:core:data`, `SummaryResult`)
  builds the `SummaryInput`, logs usage on the route's task (even for a failed reply) and tidies the answer; a blank
  reply is `InvalidResponse`. `SummarizeSourceUseCase` (`:core:domain`) wraps it, and `SourceSummaryCache` is the
  session cache (text, model, language), like `PageTranscriptionCache`. Whether a source gets a summary is
  `ExtractOptions.makesSummary(parts)` (`ContextSummary { Auto, Off }`, default Auto; at least
  `MIN_PARTS_FOR_SUMMARY` = 3 parts) and `ExtractOptions.requests(parts)` is the number of requests the app shows
  (`SmartExtractUiState.requests` is now derived from `parts` and the options, so changing the setting changes the
  hint at once; the book import's confirmation uses the default options). The count ignores the cache: it is what a
  first run costs.
- `ExtractRequest(context, summarize)`: `summarize` is the whole text to summarise first, `context` a summary that
  exists (cached or edited). `GenerateCardsUseCase` emits `SummaryStarted`, then `SummaryReady(text)` or
  `SummaryFailed(failure)`, which ends the run before any part. `GenerationRequest.context` and
  `RegenerateCardUseCase(context = …)` carry it to the prompt.
- In the ViewModel the summary is in `SmartExtractUiState.context` (`SourceContext(text, expanded, editing)`), the
  cache is `summaries`, and `GenerationState` gained `Summarizing` and `SummaryFailed`. Retry after a failed summary
  asks again; **Generate without context** goes on with none. Edit is refused while a run is going, so a part is
  always sent with the summary as it was when the run last started or resumed; an edit goes into the cache, which is how
  Resume, Regenerate (it sends the summary on screen) and a second Generate use it. A page-image run, ClearText and the
  next chapter of a book run clear the card.
- The **Context** card (`ContextCard` in `SmartExtractScreen.kt`) sits above the queue: first line and Show, then the
  whole text and a line saying what it is; Edit/Done, and `ReportAiButton` (`AiReportKind.ContextSummary`). Advanced ›
  **Context summary** (Auto / Off, `ContextSummaryPicker`) says "No summary" in the closed row when off. Not persisted
  yet (C5).
- Privacy: the Smart Extract row of `docs/release/privacy-policy.md` says a long source also sends its text, or a
  skim of it, for the summary. The in-app disclosure ("the source text you entered …") stays true.
- Tests: `SummaryInputTest`, `SourceSummaryTest` (prompt, cleaning, a mock-server request, the context block and its
  escaping), `RequestGoldenTest`, `SmartExtractUseCasesTest` (order of events, failure, cache, language, Regenerate),
  `SmartExtractContextTest` (when it is made, the count, the cache on Resume, a second Generate, an edit used by
  Resume, Regenerate and Generate, failure with Retry and Generate without context, Stop, Clear),
  `SmartExtractScreenTest`, and the screenshots `create_extract_context_light.png` and the re-recorded
  `create_extract_advanced_light.png`. `ExtractRequestDump` (QA) still sends parts without a summary: judge the
  summary's effect in the app, or extend the dump in C7.
