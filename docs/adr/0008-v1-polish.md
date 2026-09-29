# ADR 0008: Card types, audio, Co-Author, engagement and release (v1 polish)

- **Status:** Accepted (licensing and distribution: see "Open" below)
- **Date:** 2026-09-29
- **Context for:** ROADMAP Phase 6; PROJECT_OVERVIEW §4.2, §4.3, §4.5, §10; ADR 0003, 0005, 0006, 0007

## Decision

### Two new note kinds, one schema bump (v4)

| Kind | Fields | Cards | Study |
|---|---|---|---|
| `TypeIn` ("Type-in answer") | Front, Back | 1 | A text box under the prompt; Done/Check reveals. `TypedAnswer` compares with the back's plain text, ignoring case, NFC form, repeated spaces and punctuation at the ends, and shows a letter diff (LCS, capped at 400 chars) when wrong. |
| `MultipleChoice` | Question, Answer, Wrong answers (one per line; `- `/`A. ` prefixes dropped) | 1 | The answer and up to 7 wrong answers as options, shuffled with the card id as seed, so the order is stable for a card but differs between cards. Picking an option reveals. |

Both get fixed built-in note-type ids (`…0004`, `…0005`), seeded on create and inserted by
`Migration3To4`. For a checked answer (typed or picked) the study screen outlines the rating it
suggests (Good if right, Again if wrong); the learner still chooses. Everything else (queue,
FSRS, undo, browse) is kind-agnostic.

**Hint/mnemonic** is a nullable `notes.hint` column, not a field: every kind can have one, and
adding a field to the built-in types would change their Anki mapping. The study screen shows it
behind "Show hint" until the answer is revealed.

**Exam countdown** is a nullable `decks.examDate` (epoch day). The deck card shows "Exam in N
days", tertiary within two weeks and error in the last three. It doesn't change scheduling.

### Anki interop

- Export: `Mnemo Type-in answer` uses Anki's own `{{type:Back}}` template, so Anki shows a text
  box too. `Mnemo Multiple choice` keeps all three fields; Anki shows question → answer (Anki has
  no multiple choice).
- Import: any note type whose question template has `{{type:F}}` becomes Type-in; Mnemo's own
  multiple-choice note type id comes back as Multiple choice.
- The note stash (`MnemoNoteData`) now also records `kind` and `hint`, so a round trip restores
  both exactly. Old stashes without `kind` still import by layout.
- JSON export format version 2 adds `examDate` and `hint`.

### Audio and TTS

`[sound:media:<hash>]` already survived import/export (ADR 0004); now it plays. `CardAudio`
(`:core:ui`) uses `MediaPlayer` for media files (queued in order) and the system `TextToSpeech`
engine as the fallback: the card's speaker button plays the visible side's sounds, or reads it
aloud if it has none. Sounds play automatically when a side appears unless Settings › Study ›
"Play card audio automatically" is off. Inline, a sound tag is a tappable "🔊 Play" link. The
editor attaches images and audio from the system picker into media storage (`importUri`).
`LocalCardAudio`/`LocalAutoPlayAudio` are provided by `MainActivity`; previews and tests get a
silent implementation. Media GC now also scans hints.

### AI Co-Author

A third mode in Create, scoped to a deck and its subdecks. Four actions, all proposals until the
user acts:

| Action | Where it runs | What is sent |
|---|---|---|
| Chat | Provider (`AiTask.CoAuthor`), streamed Markdown | Deck name, up to 150 notes as plain text (evenly sampled from larger decks, 160 chars per side), the last 12 turns |
| Suggest missing cards | Provider, through `CardGenerationClient` with `CoAuthorSuggestPrompt` (same `{"cards"}` format, tolerant parser and repair as ADR 0006) | The same deck text, and an optional focus from the text box |
| Find duplicates | **On the device** (`DuplicateFinder`): the same `GeneratedCardValidator.key`, or ≥ 0.8 word Jaccard for questions of 4+ words, comparing only notes that share an uncommon word | Nothing |
| Improve weak cards | Provider, `StudyAssistClient.rewrite` with the card's lapse record (≥ 3 lapses, 5 most-forgotten notes) | One card and its lapse/review counts |

Suggestions are validated and deduplicated against the deck before they are shown, and saved
with `AcceptGeneratedCardsUseCase` (`source = AI`). A rewrite that empties a side or changes cloze
numbers can't be applied (it would delete cards and their history). Duplicates are removed with
`CardRepository.deleteNote` (soft delete). The disclosure dialog names what Co-Author sends.
Suggestions draw on the model's knowledge rather than a source text, so the prompt limits them to
well-established facts and every card is reviewed before saving.

Multiple choice from Smart Extract: the schema's card object gained `options` (required, empty
unless `type` is `choice`), and `CardFields` turns `options` + an answer it can match (by text or
letter) into a Multiple choice card; unmatched options still go on the front of a Basic card.

### Engagement

- **Reminder:** `ReminderSettings(enabled, time)` in DataStore. `WorkManagerReminderRepository`
  enqueues one unique one-time work delayed to the next local occurrence (`DailyTime.nextAfter`);
  `ReminderWorker` posts one notification if cards are due (skips otherwise) and enqueues the
  next with `APPEND_OR_REPLACE`. That follows the wall clock across DST, unlike a 24 h periodic
  work. The app reschedules on start. The notification opens the Study tab
  (`AppIntents.EXTRA_OPEN`). Turning it on asks for `POST_NOTIFICATIONS` on Android 13+.
- **Widget:** plain `RemoteViews` (no Glance dependency): to-study count, due/new/minutes or the
  streak, and a Study button. The system refreshes it every 30 min; `TodayWidgetUpdater` pushes
  changes while the process lives, restarting its query at each study-day boundary because
  "today" is fixed when a counts query starts.

### Adaptive layouts

`material3-adaptive` (1.3.0) gives `currentWindowLayout()` in `:core:ui`: `wide` (width ≥ medium),
`short` (height < medium) and `tabletop` (half-open foldable, horizontal hinge), provided as
`LocalWindowLayout`. Wide windows get `MnemoNavigationRail` instead of the bottom bar (Settings at
its top); Decks is a `LazyVerticalGrid` of ≥ 340 dp columns; Study puts the card and the rating
buttons side by side on wide-and-short windows and on either side of the fold in tabletop.

### Accessibility

- Contrast: `ColorContrastTest` enforces WCAG AA (4.5:1 text, 3:1 outlines) for both brand
  schemes. `outline` was 4.27:1 as text on the light surface, so text that used it now uses
  `onSurfaceVariant` (`outline` stays for borders and icons).
- TalkBack: swipe answers are also custom accessibility actions ("Answer Again"/"Answer Good");
  switch rows are one `toggleable` with the label; multiple-choice options announce correct/wrong;
  streamed Co-Author replies are a polite live region.
- `StudyScreenTest` checks that every clickable has a label and a ≥ 48 dp touch target, and that
  the controls stay on screen at 2× font scale.

### Performance

- `compose_stability.conf` (applied by the Compose convention) marks `:core:model` and the
  `java.time` values as stable: they are immutable, but Compose can't infer that across the JVM
  module boundary, so study-loop composables taking a `StudyCard` were never skipped.
- `StudyCard.sides` is computed once per instance instead of on every read.
- Large imports were already batched (500 notes per transaction, ADR 0003); nothing changed.
- Not done: a macrobenchmark/baseline-profile module. The frame-rate checks stay manual (below).

### Release

- R8 is on for release (`optimization { enable = true }`). Library consumer rules cover Room,
  Hilt, WorkManager, OkHttp and kotlinx.serialization; our rules keep the KaTeX bridge and
  silence PdfBox's optional dependencies. Verified: the minified build installs and runs
  onboarding → editor → type-in study → Co-Author duplicates → Settings on an API 35 emulator.
- Onboarding (first run, empty collection): what Mnemo is, how studying works, then name a first
  deck (opens the editor on it), import from Anki, or skip; optional daily reminder. Users with
  decks (updates, restores) never see it.
- Store listing and privacy policy: `docs/release/`. The privacy policy is also in the app
  (Settings › About) so it can be read offline.

## Open

- **Licensing and distribution** (PROJECT_OVERVIEW §11): not decided here; it is the owner's
  call. `docs/release/distribution.md` lays out the options. Nothing in the code depends on the
  choice.
- Manual checks that need hardware: 60/120 fps in the study loop and Analytics on a mid-range
  device, TTS voices and audio focus, dictation, the widget on real launchers, and "Test
  connection"/"1,000 words in 30 s" with real providers.

## Consequences

- Schema v4 (`MigrationTest.migrate3To4`), backup manifest schema 4, JSON export v2.
- A deck exported to Anki and edited there comes back with Anki's HTML for those notes (ADR 0003);
  a multiple-choice note edited in Anki still keeps its wrong answers (their field survives).
- Roborazzi baselines now exist for every feature (`src/test/screenshots`), checked by
  `verifyRoborazziDebug`. They are compared with `docs/design/` by eye when they change.
