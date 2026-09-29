# Mnemo — Roadmap

This roadmap breaks the product described in [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md) into phases, using the module structure from [ARCHITECTURE.md](ARCHITECTURE.md). Phase numbers match the milestones in PROJECT_OVERVIEW §8 (Phase 0 = M0, and so on).

Each phase ends with a usable app. Airplane mode must break nothing except AI features, starting in Phase 1.

---

## Overview

| Phase | Theme | Outcome |
|---|---|---|
| **0** | Foundation | Multi-module skeleton, design system, and navigation shell that match the mockups |
| **1** | Core SRS (MVP) | Create decks and cards by hand and study them with FSRS, fully offline |
| **2** | Anki interop and data safety | Import/export `.apkg`, back up and restore, browse large collections |
| **3** | AI providers | Manage OpenAI-compatible providers with encrypted keys and per-task routing |
| **4** | AI creation | Turn notes, PDFs, links, and speech into reviewed cards; Explain/Rewrite while studying |
| **5** | Analytics | Retention KPIs, forgetting curve, heatmap, forecast, leeches, on-device FSRS optimizer |
| **6** | Polish and v1 release | Reminders, widgets, media/TTS, extra card types, AI Co-Author, accessibility, tablets |
| **Later** | Post-v1 | Sync, companions, shared decks |

```
Phase 0 ──► Phase 1 ──┬──► Phase 2 ──────────────┐
                      ├──► Phase 3 ──► Phase 4 ──┼──► Phase 6 ──► v1.0
                      └──► Phase 5 ──────────────┘
```

Phases 2, 3, and 5 each depend only on Phase 1, so they can be reordered or run in parallel. Phase 4 needs Phase 3. The leech-improvement part of AI Co-Author (Phase 6) needs both Phase 4 and Phase 5.

---

## Phase 0 — Foundation

**Goal:** turn the template into the modular architecture, with a theme and navigation shell that match `docs/design/`.

### Scope

- **Build setup**
  - `build-logic/` convention plugins: `mnemo.android.application`, `.library`, `.compose`, `.feature`, `.room`, `mnemo.hilt`, `mnemo.jvm.library`.
  - Add KSP, Hilt, Room, Navigation Compose, kotlinx.serialization, DataStore, and the test libraries to `gradle/libs.versions.toml` (keep AGP 9 built-in Kotlin, no `kotlin-android` plugin).
- **Core modules (empty or minimal)**
  - `:core:model`, `:core:common` (`MnemoResult`, `MnemoError`, `@Dispatcher`, `Clock`), `:core:testing` (`MainDispatcherRule`, `TestClock`, `HiltTestRunner`).
- **Design system** (`:core:designsystem`)
  - Move `ui/theme/*` out of `:app`.
  - Port the tokens from ARCHITECTURE §7: light color scheme, generated dark scheme (seed `#4F46E5`), type scale and `MnemoTypography` extras, spacing via `CompositionLocal`, shapes.
  - Bundle Newsreader, Hanken Grotesk, and JetBrains Mono in `res/font/`.
  - The brand palette is the default. Dynamic color becomes opt-in.
  - First components: `MnemoButton`, `MnemoChip`, `StatTile`, `MnemoTopBar`, `MnemoNavigationBar`, `EmptyState`.
- **App shell** (`:app`, `:core:ui`)
  - `MnemoApplication` (`@HiltAndroidApp`), `MnemoApp` (Scaffold + bottom bar + NavHost), `TopLevelDestination`.
  - Bottom bar **Decks · Study · Create · Analytics** with placeholder screens. Settings is reached from the top-bar avatar.
  - Type-safe `@Serializable` routes in `:core:ui/navigation`.
- **Decisions**
  - `docs/adr/0001-fsrs-as-scheduler.md`: FSRS only; SM-2 data is converted on import (resolves the first open question in PROJECT_OVERVIEW §11).

### Exit criteria

- `./gradlew assembleDebug testDebugUnitTest lint` passes.
- All four tabs and Settings are reachable. Edge-to-edge insets are handled on every screen.
- A Roborazzi screenshot of the design-system component catalog is committed as the baseline.

---

## Phase 1 — Core SRS (MVP)

**Goal:** a complete offline spaced-repetition app with no AI: make decks, add cards, study them.

### Scope

- **Scheduler** (`:core:scheduler`)
  - FSRS: next states for each rating, retrievability, `SchedulingInfo` for the interval labels.
  - Unit tests against FSRS reference vectors.
- **Persistence** (`:core:database`, `:core:datastore`)
  - Entities and DAOs for `Deck`, `NoteType`, `Note`, `Card`, `ReviewLog`.
  - UUID ids, `createdAt`/`updatedAt`/`deletedAt` on every row, indices on `Card(due, state, deckId)`, `ReviewLog(cardId, reviewedAt)`, `Note(deckId)`.
  - Export the schema to `core/database/schemas/` from version 1. Set up `MigrationTestHelper`.
  - User preferences: desired retention, daily new/review limits, learning steps, theme, dynamic color.
- **Data and domain** (`:core:data`, `:core:domain`)
  - `DeckRepository`, `CardRepository`, `ReviewRepository`, `UserSettingsRepository`.
  - `BuildStudyQueueUseCase`, `AnswerCardUseCase` (card update + review log in one transaction), `UndoLastAnswerUseCase`, `GetTodaySummaryUseCase`.
- **Card rendering** (`:core:ui`)
  - `CardFace` with Markdown, code blocks, and cloze rendering.
- **`:feature:decks`** (`design/decks.html`)
  - Greeting header with due/new/learning counts and estimated session time.
  - Deck grid, create/edit/delete deck, star, category, filters (All, Due Today, Starred), search.
  - Nested decks (`Parent::Child`) in the data model, collapsed in the UI.
  - "Daily Mix" session across decks.
  - Stat tiles show streak and card counts. Retention tiles come in Phase 5.
- **`:feature:create`, manual mode** (`design/ai-card-creator.html`, manual tab)
  - Editor for **Basic**, **Basic + Reversed**, and **Cloze** with tags, cloze shortcut, and live preview.
- **`:feature:study`** (`design/active-study-review.html`)
  - Progress header, tap to flip, swipe left = Again / right = Good, four rating buttons with next intervals.
  - Undo, star, flag, bury, suspend, edit in place, session summary.
  - Daily limits respected. Optimistic UI: no loading state between cards.
- **`:feature:settings`, first pass**
  - Scheduling options and appearance (system/light/dark, dynamic color toggle, card font size).

### Exit criteria

- A new user can create a deck and finish a first review session in under 2 minutes.
- The study loop runs at 60/120 fps on a mid-range device (check with a macrobenchmark or the frame metrics).
- ViewModel tests for decks, create, and study. A Compose UI test for the study flow.
- Airplane mode: everything works.

---

## Phase 2 — Anki interop and data safety

**Status:** implemented (2026-09-29). Decisions: ADR 0003 (Anki mapping), ADR 0004 (media, math,
backups). The exit criteria are covered by tests: `AnkiTransferTest` (real Anki packages, import
and export → import), `RoundTripTest`, `BackupTest` (restore on a fresh install) and
`MigrationTest.migrate1To2`. Mnemo's `.apkg` also imports into real Anki 26.09.

**Goal:** Anki users can bring their decks and history, and nobody loses data.

### Scope

- **Media** (`MediaRepository`)
  - Content-addressed storage in `filesDir/media/<sha256>`, `MediaEntity`, garbage-collection worker.
  - Image and LaTeX rendering in `CardFace` (KaTeX/MathJax WebView only for cards that contain math).
- **Anki** (`:core:anki`)
  - `.apkg`/`.colpkg` reader for the legacy (`anki2`) and new (`anki21b`) schemas: notes, note types, media, and review history.
  - Map Anki note types to Mnemo kinds. Convert SM-2 scheduling state to FSRS memory state.
  - `.apkg` writer for export.
  - Test fixtures: sample decks in `core/anki/src/test/resources/`.
- **Import flow**
  - `CoroutineWorker` with a foreground notification for large decks, streamed zip reading, batched transactions.
  - "Import Anki (.apkg)" action on the Decks screen.
- **Backup and export**
  - Manual backup/restore (checkpointed DB + media, zipped) to a Storage Access Framework location.
  - `BackupWorker` for scheduled automatic backups.
  - Full JSON export.
  - `backup_rules.xml` / `data_extraction_rules.xml` set up (encrypted keys excluded once they exist in Phase 3).
- **`:feature:browse`**
  - Card browser: search, filter by deck/tag/state, bulk edit, suspend, and move. Needed once imports bring thousands of cards.

### Exit criteria

- An Anki user imports a real `.apkg` and keeps studying with review history and media intact.
- Export → import round-trips without data loss.
- Restoring a backup on a fresh install recovers all decks, cards, history, and media.
- The first Room migration ships with a `MigrationTestHelper` test.

---

## Phase 3 — AI providers

**Status:** implemented (2026-09-29). Decisions: ADR 0005 (keys outside the database, network
rules, capability detection). Covered by tests: `OpenAiCompatibleClientTest` and
`ConnectionProbeTest` (MockWebServer), `FileSecretStoreTest`, `DefaultAiProviderRepositoryTest`,
`BackupTest.backupsNeverContainApiKeys`, `MigrationTest.migrate2To3`, and `AiProvidersFlowTest`
(setup prompt → add a provider). Still open: the manual exit check of "Test connection" against a
hosted provider and a real Ollama/LM Studio server, which needs a device and the user's own key.

**Goal:** users configure any OpenAI-compatible provider safely. No AI features use it yet beyond testing the connection.

### Scope

- **Security** (`:core:security`)
  - `SecretCipher`: AES-GCM with a non-exportable Keystore key. Keys are decrypted only in memory per request.
- **AI client** (`:core:ai`)
  - `OpenAiCompatibleClient`: `GET /models`, `POST /chat/completions`, SSE streaming via `okhttp-sse`.
  - DTOs, `ProviderConfig` (base URL, key, extra headers, timeout).
  - `ProviderPresets`: OpenAI, OpenRouter, Groq, Together, DeepSeek, Mistral, Gemini (OpenAI-compatible), Ollama, LM Studio, and Custom.
  - MockWebServer tests.
- **Data**
  - `AiProviderEntity`, `AiModelEntity`, `AiTaskRouteEntity` and migration.
  - `AiProviderRepository` with `routeFor(AiTask)` and fallback to the default provider.
- **Settings UI**
  - Provider list: add, edit, remove, reorder, enable/disable.
  - Test connection (models endpoint + one-token completion), capability detection (JSON output, vision, streaming), manual model entry fallback.
  - Per-task routing (Extract, Co-Author, Explain, Rewrite).
- **Network and privacy**
  - HTTPS required, except user-marked local providers (LAN IPs, `localhost`) through a scoped `network_security_config`.
  - First-request notice showing what is sent and where.
  - Local token-usage tracking per provider and task.
  - API keys excluded from backups and exports.

### Exit criteria

- Test connection passes against at least one hosted provider and one local Ollama/LM Studio server.
- Keys are never logged, exported, or stored in plain text.
- With no provider configured, AI entry points show a setup prompt instead of failing.

---

## Phase 4 — AI creation

**Status:** implemented (2026-09-29). Decisions: ADR 0006 (pipeline, output format and fallbacks,
tolerant parser, dedupe and what is sent, sources). Covered by tests: `GeneratedCardParserTest`
(the fixture set of malformed replies in `core/ai/src/test/resources/replies`, parsed whole, in
chunks and one character at a time), `JsonRepairTest`, `CardGenerationClientTest` (structured and
prompt-only output, streaming and not, fallbacks, one repair, cards kept after a failure),
`ExtractorsTest` and `TextChunkerTest` (`:core:ingest`), `AiGenerationRepositoriesTest`,
`SmartExtractUseCasesTest`, `SmartExtractViewModelTest`, `SmartExtractScreenTest` (paste →
generate → accept), `StudyAssistViewModelTest` and `StudyScreenTest`. Still open: the manual exit
check of "1,000 words to accepted cards in under 30 seconds" with a real provider, and dictation on
a device (Robolectric has no speech recognizer).

**Goal:** the headline feature: turn source material into good cards quickly, with the user in control.

### Scope

- **Ingest** (`:core:ingest`)
  - Paste, PDF text extraction (e.g. PdfBox-Android), web/lecture link to readable text, on-device dictation, `TextChunker`.
- **Generation** (`:core:ai`, `:core:domain`)
  - Prompt templates for archetypes (definition, cloze, multiple-choice, case study), density, and language.
  - `GeneratedCardsSchema` for `response_format` when supported. Otherwise JSON-in-message with tolerant parsing and one repair retry.
  - Incremental `GeneratedCardParser` over SSE deltas → `Flow<GeneratedCard>`.
  - Validation: required fields, cloze syntax, dedupe against the destination deck.
  - `GenerateCardsUseCase`, `AcceptGeneratedCardsUseCase` (one transaction).
- **`:feature:create`, Smart Extract**
  - Source picker, destination deck, density slider, archetypes, language.
  - Review queue that fills as cards stream in: accept, edit, regenerate, discard, Accept All.
  - Nothing is saved before acceptance. Cards received before a network failure stay in the queue.
  - Notes created this way are tagged with `source = AI`.
- **Study-time AI** (`:feature:study`)
  - "Explain this", "Give me an example", and "Rewrite this card", shown only when a provider is configured.

### Exit criteria

- A 1,000-word note becomes accepted AI cards in under 30 seconds with any configured provider.
- The parser handles the fixture set of malformed model outputs (unit tests).
- The same flow works with a model that does not support structured output.

---

## Phase 5 — Analytics

**Status:** implemented (2026-09-29). Decisions: ADR 0007 (metric definitions, SQL-only stats
with retrievability buckets, the optimizer as a port of py-fsrs's, apply-only-if-better).
Covered by tests: `FsrsOptimizerTest` (py-fsrs 6.3.2 reference data from
`core/scheduler/fixtures/make_optimizer_fixtures.py`: loss and gradient to 1e-12, fitted weights
to 1e-9, 100k reviews), `StatsDaoTest` (each aggregate, plus 120k reviews with index checks),
`OfflineStatsRepositoryTest`, `FsrsOptimizationTest`, `RetentionStatsUseCasesTest`,
`AnalyticsTest` (ViewModel and screen), `DecksViewModelTest` and `SettingsViewModelDataTest`.
Still open: checking the Analytics screen's frame rate on a device with a large real collection.

**Goal:** show users that the method works, and tune scheduling to them.

### Scope

- **Stats** (`StatsDao`, `StatsRepository`, `ComputeRetentionStatsUseCase`)
  - SQL aggregates only; no loading rows into memory.
- **`:feature:analytics`** (`design/analytics.html`)
  - KPIs: true retention vs. target, review volume, average stability, time saved.
  - Forgetting-curve chart (FSRS vs. passive decay), review heatmap and streak, per-deck breakdowns, due forecast, leeches.
  - Custom Compose `Canvas` charts in `:core:ui/chart`.
- **Decks screen**
  - Wire up the retention tile, mastered cards, and per-deck "retention health".
- **FSRS optimizer**
  - `FsrsOptimizer` fits parameters from the review log on device. `OptimizeFsrsWorker` runs it in the background. Button in scheduling settings.

### Exit criteria

- All charts and KPIs are computed locally and stay responsive with 100k+ review log rows.
- Optimizer results are tested against reference data.

---

## Phase 6 — Polish and v1 release

**Goal:** close the remaining feature gaps and ship v1.

### Scope

- **Study and cards**
  - **Type-in answer** and **Multiple choice** card types.
  - Audio attachments and TTS fallback. Mnemonic/hint field.
  - Exam countdown on decks.
- **AI Co-Author** (`:feature:create`)
  - Deck-scoped chat that suggests missing cards, finds duplicates, and improves weak cards (using lapse data from Phase 5).
- **Engagement**
  - Daily study reminder (`ReminderWorker`) at a chosen time.
  - Home-screen widget with today's queue.
- **Quality**
  - Accessibility pass: TalkBack labels, touch targets, font scaling, contrast in both themes.
  - Tablet and foldable layouts with `material3-adaptive`.
  - Roborazzi screenshot suite compared against the mockups. End-to-end test: onboarding → create deck → study → stats.
  - Performance pass on the study loop and large imports.
- **Release**
  - R8 enabled with keep rules in `app/src/main/keepRules/`.
  - Decide licensing and distribution (Play Store, F-Droid) — the last open question in PROJECT_OVERVIEW §11.
  - Store listing, privacy policy (no telemetry), onboarding.

### Exit criteria

- All success criteria in PROJECT_OVERVIEW §10 are met.
- No open P0/P1 bugs. Release build passes lint and all test suites.

---

## Later (post-v1)

- **Sync**: file-based sync through user-owned storage, or an optional self-hostable server. The schema already has UUIDs, timestamps, and soft deletes for this.
- **On-device models** (e.g. Gemini Nano / AICore) as a non-network provider.
- **Richer Anki templates**: more of Anki's HTML/CSS templating for imported decks.
- **Desktop/web companion** and **shared decks**.

## Open questions by phase

| Question (PROJECT_OVERVIEW §11) | Decide by |
|---|---|
| FSRS only vs. SM-2 support | Phase 0 (ADR 0001) |
| How much Anki templating to support | Phase 2 (ADR 0003) |
| On-device models as a provider | Phase 3 (design `AiProvider` so it can fit), built Later |
| Sync strategy | Later, but schema rules apply from Phase 1 |
| Monetization / licensing | Phase 6 |
