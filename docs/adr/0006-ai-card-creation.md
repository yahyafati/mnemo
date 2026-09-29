# ADR 0006: AI card creation

- **Status:** Accepted
- **Date:** 2026-09-29
- **Context for:** ROADMAP Phase 4; PROJECT_OVERVIEW §5.3; ARCHITECTURE §5.2

## Decision

### The pipeline and where each step lives

```
source ─► :core:ingest ─► text box (editable) ─► TextChunker ─► :core:ai ─► :core:domain ─► review queue ─► notes
          PDF / link /                            1,200 words   prompt,       validate,       (memory)       one
          dictation                               per request   stream,       dedupe                         transaction
                                                                parse
```

- `:core:ingest` (Android library, no Hilt): `PdfTextExtractor`, `WebPageExtractor`,
  `SpeechTranscriber`, `TextChunker`. Like the AI client, its classes are plain constructors that
  `:core:data`'s DI modules build, and only `:core:data` depends on it (`SourceRepository`).
- `:core:ai`: `CardGenerationPrompt`, `GeneratedCardsSchema`, `GeneratedCardParser` +
  `JsonRepair`, `ChatTextRunner` (one request, streamed or not) and `CardGenerationClient` /
  `StudyAssistClient`. All pure JVM and tested against MockWebServer and a fixture set.
- `:core:data`: `CardGenerationRepository` and `StudyAssistRepository` build the request from an
  `AiRoute` (decrypting the key through `ProviderConfigs`), map parsed cards to `GeneratedCard`,
  and log token usage per task. `CardRepository.addNotes` writes many notes in one transaction.
- `:core:domain`: `GenerateCardsUseCase` (parts in order, validation, dedupe),
  `RegenerateCardUseCase`, `AcceptGeneratedCardsUseCase`, `GeneratedCardValidator`.

Every source ends up as **text in one editable box**: a PDF or page is read into it, dictation
appends to it. The user sees and can trim exactly what will be sent.

### One output format, asked for twice

Cards come back as `{"cards": [{"type": "basic"|"cloze", "front", "back", "tags": [...]}]}`.
Multiple choice (options as a Markdown list) and case studies are Basic cards until Phase 6 adds
their card types. When the model supports structured output the request carries this schema as
`response_format` (strict); the format is **also** spelled out in the system prompt, because some
servers accept `response_format` and ignore it (ADR 0005), and models without it need the
instructions anyway.

Requests start from the model's detected capabilities and back off when the server objects
(`RequestMode.relaxedFor`), but only before any text arrived: a 4xx naming `response_format` /
`json_schema` drops the schema, one naming `stream_options` drops usage reporting, one naming
`stream` drops streaming, and a bare 400/422 with a schema drops the schema. 401, 403 and 429 are
never retried.

### A tolerant, incremental parser

`GeneratedCardParser` scans the streamed text for JSON structure (strings, brackets) without
parsing the whole reply. Each object that closes is read as a card (`CardFields`); objects around
cards are skipped. So cards reach the queue one by one while the reply streams, and the reply may be
the schema, a bare array, one object per line, fenced in ```` ```json ````, preceded by prose, or cut
off mid-card (complete cards count). Also handled:

- `<think>…</think>` blocks (reasoning models that put them in `content`) are dropped.
- Other key names (`question`/`answer`, `text`/`extra`, `term`/`definition` …), tags as a string,
  multiple-choice `options` arrays, sloppy cloze (`{{C1: x}}`).
- `JsonRepair`: single quotes, unquoted keys, Python literals, trailing/missing commas, comments,
  raw newlines in strings, and invalid escapes. It runs even on valid JSON, because `"\frac"` is
  valid JSON for a form feed plus `rac` but a model always means LaTeX; `\b`, `\f`, `\n`, `\r`,
  `\t` followed by a known LaTeX command name are kept literal.
- At the end, a reply with no card objects is parsed whole (cards nested inside a JSON string),
  then as plain text (`Q:` / `A:` pairs, one cloze sentence per line).

A reply with no readable card gets **one** repair request: the conversation plus the model's own
reply and "reply again with only the JSON". The fixture set in `core/ai/src/test/resources/replies`
holds the malformed replies this must survive; every fixture parses the same whole, in chunks and
one character at a time.

### Validation, duplicates, and what leaves the device

`GeneratedCardValidator` requires a front, a back for Basic cards, at least one complete deletion
and no unclosed `{{c…` for cloze cards, and sane lengths. It runs when a card arrives and again on
accept (the user may have edited it). Two cards are the same when their fronts match after removing
case, punctuation, Markdown and cloze markup. Duplicates of the destination deck, of the queue, and
of each other are dropped and counted.

Only the source text and the **fronts of cards already in the review queue** (so later parts don't
repeat them) are sent. The destination deck's notes are compared on the device and never sent. The
first-request notice says so.

### Long sources

`TextChunker` splits between paragraphs, else sentences, else words, into parts of at most 1,200
words (about 1,600 tokens: fits every context window, and a 1,000-word note is one request, which
the "under 30 seconds" exit criterion needs). A short tail joins the part before it. Parts run one
at a time, in order, so cards arrive in the source's order and rate limits aren't hit in parallel.
A failure stops the run at that part; cards already received stay, and Retry resumes at the failed
part. Each queued card keeps the text of its part, so Regenerate asks for one replacement from the
same text even after the box changed.

### Nothing is saved before acceptance

The review queue lives in the ViewModel. Accept (one card) and Accept All write through
`AcceptGeneratedCardsUseCase`: one note per card, `source = AI`, all in one transaction. Cards leave
the queue before the write, so a double tap can't save twice. No schema change was needed:
`notes.source` exists since v1.

### Sources

- **PDF**: PdfBox-Android. `PdfRenderer` only extracts text from Android 15, and minSdk is 29.
  The text layer is read (up to 300 pages, 400,000 characters), wrapped lines are joined, and
  owner-password restrictions are lifted (the user has the file); user-password PDFs and scanned
  PDFs without text fail with a clear message. There's no OCR. PdfBox adds about 3 MB (R8 in
  Phase 6 will shrink it). Its BouncyCastle dependency is excluded: only certificate-encrypted PDFs
  need it, and its `bcpkix` contains a trust-all `X509TrustManager` that store scanners flag. Those
  rare PDFs fail as encrypted.
- **Links**: OkHttp + jsoup. The page is parsed, never run: scripts, navigation, footers, cookie
  banners and similar are removed, and the largest of `article`/`main`/common content containers
  is read with paragraph breaks. Plain text and PDF links work too. Unlike AI requests (ADR 0005)
  this client follows redirects: nothing secret is sent. Responses over 8 MB are refused without
  being downloaded. Pages that need JavaScript (video sites) have no text and say so; lecture videos
  need their transcript pasted.
- **Dictation**: the platform `SpeechRecognizer`, on-device where Android has one
  (`createOnDeviceSpeechRecognizer`, Android 12+), otherwise the default recognizer with
  `EXTRA_PREFER_OFFLINE`. It keeps listening phrase after phrase until stopped, and stopping keeps
  the phrase in progress. RECORD_AUDIO is requested only when the microphone is tapped. Audio
  never goes to an AI provider; only the text the user keeps does.

### Study-time AI

"Explain this" and "Give me an example" (task `Explain`) and "Rewrite this card" (task `Rewrite`)
appear on the study card once the answer is showing, and only when a route exists for the task.
Explanations stream as Markdown into a sheet. A rewrite comes back as JSON fields (its own small
schema) and is only a proposal: applying it updates the note's fields in place, keeping deck, tags
and every card's schedule. A cloze rewrite must keep exactly the same cloze numbers; otherwise
applying it would delete or add cards and lose their history, so it can't be applied.

## Consequences

- AI entry points follow ADR 0005: the setup prompt without a provider (Smart Extract; study-time
  AI is hidden instead), the first-request notice per provider, and token usage per task.
- The 30-second exit criterion depends on the provider and model; it can only be checked by hand.
- Not done: OCR, video transcripts, per-archetype counts, parallel parts, and sending images to
  vision models.

## Alternatives considered

- **Tool/function calling** for structured output: less consistently supported across
  OpenAI-compatible servers (and local models) than `response_format`, and it streams arguments in
  a different shape.
- **Parsing only once the reply is complete**: simpler, but the queue would stay empty for the
  whole request, and a dropped connection would lose every card.
- **JSON Lines as the output format**: easy to stream, but no schema can express it, and models
  drift into arrays anyway. The parser accepts it either way.
- **Sending the deck's existing cards to avoid duplicates**: better dedupe for the model, but it
  would send notes the user didn't choose to share. Local dedupe catches exact repeats.
- **`RecognizerIntent` activity** for dictation: no permission needed, but it stops after one
  phrase and shows system UI over the editor.
