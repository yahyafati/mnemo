# Mnemo — Project Overview

> A modern, local-first spaced-repetition app for Android, with AI card creation through any OpenAI-compatible provider.

| | |
|---|---|
| **Status** | Pre-development (template app + design mockups) |
| **Platform** | Android (minSdk 29, target 37), Kotlin + Jetpack Compose, Material 3 |
| **Package** | `com.yahyafati.mnemo` |
| **Design references** | [`docs/design/`](design/): HTML mockups of Decks, Study Session, Create Cards, and Analytics (design only, not app code) |
| **Architecture** | [ARCHITECTURE.md](ARCHITECTURE.md) |

---

## 1. Vision

Anki's scheduling works, but the app is dated, hard to learn, and makes creating cards slow. Mnemo keeps what works (spaced repetition, decks, `.apkg` compatibility) and changes three things:

1. **Modern look and feel.** A polished Material 3 interface with gestures, motion, and clear stats, so opening it each day is pleasant.
2. **Local first.** Your cards, review history, and settings live on the device. The app works fully offline and needs no account. Nothing leaves the phone unless you ask for it to.
3. **Bring your own AI.** Card generation and study help work with any OpenAI-compatible endpoint you configure: OpenAI, OpenRouter, Groq, DeepSeek, Mistral, a self-hosted Ollama or LM Studio, and others. You choose the provider, the model, and what gets sent.

## 2. Guiding principles

| Principle | What it means in practice |
|---|---|
| **Offline is the default** | Every core flow (study, browse, edit, stats, import/export) works with no network. AI features degrade gracefully and are never required. |
| **The user owns the data** | Standard SQLite storage, one-tap full export, `.apkg` import/export, no lock-in. |
| **Provider-agnostic AI** | No hardcoded vendor. Providers are data (a list the user edits), not code. |
| **Privacy by default** | No analytics or telemetry SDKs. API keys are encrypted at rest. The app shows what content will be sent before an AI request goes out. |
| **Fast study loop** | A review takes one gesture. The study screen must feel instant (<16 ms frames, no loading states between cards). |
| **Proven scheduling** | FSRS as the scheduling algorithm, with review logs stored so parameters can be optimized per user. |

## 3. Target users

- **Students** preparing for exams (medicine, law, sciences) who want to turn lecture notes and PDFs into cards quickly.
- **Language learners** who need audio, reversed cards, and consistent daily reviews.
- **Existing Anki users** who want a nicer mobile experience without losing their decks.
- **Privacy-conscious users** who want AI features but only through a provider they choose, including local models.

## 4. Core features

### 4.1 Decks (home) — `design/decks.html`

- Greeting header with today's queue: **due / new / learning** counts and an estimated session time.
- Stat tiles: streak, retention %, mastered cards.
- Deck grid with a category label, due count, total cards, "retention health", last review time, and optional exam countdown.
- Filters: All, Due Today, Starred, custom tags (e.g. "Exam Prep"). Search across decks.
- Actions: **New Deck**, **Import Anki (.apkg)**, start a combined "Daily Mix" session across decks.
- Nested decks (Anki-style `Parent::Child`) supported in data, shown collapsed in the UI.

### 4.2 Study session — `design/active-study-review.html`

- One card at a time with a progress indicator ("Card 7 of 24").
- Tap to flip. **Swipe left = Again, swipe right = Good**, plus buttons for all four ratings (Again / Hard / Good / Easy), each showing its next interval.
- Rich content: Markdown, images, LaTeX/MathJax, code blocks, audio (with TTS as a fallback), and an optional mnemonic/hint.
- Card types: **Basic**, **Basic + Reversed**, **Cloze deletion**, **Type-in answer**, and **Multiple choice**.
- Star/bookmark, flag, bury, suspend, and edit-in-place from the review screen.
- Undo last answer.
- Optional AI actions on a card (only when a provider is configured): "Explain this", "Give me an example", "Rewrite this card".

### 4.3 Card creation — `design/ai-card-creator.html`

Three modes:

1. **Manual create.** A fast editor with field templates, cloze shortcuts, tags, image and audio attachments, and a live preview.
2. **AI Smart Extract.** Turn source material into a batch of cards.
   - Inputs: pasted notes, PDF upload (text extracted on device), a web or lecture link, and dictation (on-device speech-to-text).
   - Controls: destination deck, number of cards / information density (Concise ↔ Comprehensive), card archetypes (definition, cloze, multiple-choice, case study), and language.
   - Output: an editable **review queue** of generated cards. Accept, edit, regenerate, or discard each one, or **Accept All**. Nothing is written to a deck until the user accepts it.
3. **AI Co-Author.** A chat-style assistant scoped to a deck that suggests missing cards, finds duplicates, and improves weak cards (for example ones with a high lapse rate).

### 4.4 Analytics — `design/analytics.html`

- KPIs: true retention vs. target, review volume, average stability, and time saved.
- Forgetting-curve chart comparing FSRS-scheduled recall with passive decay.
- Review heatmap (activity calendar) and streak.
- Per-deck breakdowns, forecast of upcoming due cards, and hardest cards (leeches).
- Everything is computed locally from the review log.

### 4.5 Settings and data

- **AI Providers** (see §5).
- Scheduling: desired retention, daily new/review limits, learning steps, and an FSRS parameter optimizer that runs on device.
- Appearance: system/light/dark, the brand palette (default) or Material You dynamic color as an option, and card font size.
- Reminders: a daily study notification at a chosen time.
- Data: manual backup/restore to a user-chosen location, automatic periodic local backups, `.apkg` import/export, and full JSON export.

## 5. AI providers

### 5.1 Requirements

- The user manages a **dynamic list of providers**: add, edit, remove, reorder, enable/disable.
- Every provider speaks the **OpenAI-compatible API** (`/v1/chat/completions`, `/v1/models`). No vendor SDKs.
- Each **AI task** (Smart Extract, Co-Author, Explain, Rewrite) can use its own provider and model, falling back to a global default.
- AI is fully optional. With no provider configured, AI entry points show a short setup prompt instead of failing.

### 5.2 Provider definition

| Field | Notes |
|---|---|
| `name` | Display name, e.g. "OpenRouter" or "Home Ollama" |
| `baseUrl` | e.g. `https://api.openai.com/v1`, `http://192.168.1.20:11434/v1` |
| `apiKey` | Optional (local servers often need none). Encrypted with an Android Keystore key and never exported in plain text. |
| `extraHeaders` | Optional key/value pairs (e.g. OpenRouter's `HTTP-Referer`, org IDs) |
| `models` | Fetched from `GET /models`, with manual entry as a fallback. |
| `defaultModel` | Used when a task doesn't pick one |
| `capabilities` | Per model, detected or user-set: JSON/structured output, vision, streaming |
| `timeout`, `enabled`, `sortOrder` | Housekeeping |

**Built-in presets** pre-fill the base URL for common services (OpenAI, OpenRouter, Groq, Together, DeepSeek, Mistral, Google Gemini's OpenAI-compatible endpoint, Ollama, LM Studio). A **Custom** option takes any URL. A **Test connection** button checks the models endpoint and runs a tiny completion.

### 5.3 How generation works

1. Source text is prepared on device: PDF text extraction, chunking of long inputs, and dictation transcription.
2. A prompt template for the chosen archetypes and density is filled in.
3. The request asks for **structured JSON output** (`response_format` with a JSON schema) when the model supports it. Otherwise the app falls back to "JSON in the message" with tolerant parsing and one repair retry.
4. Streaming responses fill the review queue card by card as they arrive.
5. The parsed cards are validated (required fields, cloze syntax) and shown for review. Nothing is saved until the user accepts.

### 5.4 Privacy and cost

- Before the first request to a provider, show a clear notice of what content is sent and to where.
- Track token usage per provider and per task locally, when the response reports it.
- No proxy server. The device talks directly to the provider the user configured.

## 6. Local-first data

- **Storage:** Room (SQLite) is the single source of truth. Media files live in app-private storage and are referenced by content hash.
- **No account and no backend** in v1.
- **Backups:** manual export and scheduled automatic backups (WorkManager) to a location picked through the Storage Access Framework.
- **Anki interop:** import `.apkg`/`.colpkg` (notes, note types, media, and review history where possible) and export `.apkg`.
- **Future sync (post-v1, optional):** file-based sync through user-owned storage, or a self-hostable sync server. The schema should be designed for this from day one: stable UUIDs, `updatedAt` timestamps, and soft deletes.

### 6.1 Core data model (initial sketch)

```
Deck        (id, parentId?, name, description, category, color, starred, examDate?, config)
NoteType    (id, name, fields[], templates[], kind: BASIC | REVERSED | CLOZE | TYPE_IN | MCQ)
Note        (id, deckId, noteTypeId, fields{}, tags[], source?: MANUAL | AI | IMPORT, createdAt, updatedAt)
Card        (id, noteId, templateOrd, state: NEW | LEARNING | REVIEW | RELEARNING,
             due, stability, difficulty, reps, lapses, flags, suspended, buried)
ReviewLog   (id, cardId, rating, reviewedAt, elapsedDays, scheduledDays, durationMs, stateBefore)
Media       (hash, mimeType, path, size)
AiProvider  (id, name, baseUrl, encryptedApiKey?, headers{}, defaultModel, enabled, sortOrder)
AiModel     (providerId, modelId, supportsJson, supportsVision)
AiTaskRoute (task, providerId, modelId)
```

## 7. Technical architecture (proposed)

The app is currently a single `:app` module. It will be split into modules by layer and feature as described in ARCHITECTURE.md.

| Concern | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 (dynamic color), edge-to-edge |
| Architecture | Unidirectional data flow: Compose screens → ViewModel (`StateFlow` UI state) → repositories → data sources |
| Navigation | Navigation Compose with a bottom bar: **Decks · Study · Create · Analytics** (as in the mockups) |
| DI | Hilt (via KSP) |
| Persistence | Room (via KSP), DataStore for preferences |
| Networking | OkHttp + Retrofit (or Ktor) with kotlinx.serialization; SSE streaming for chat completions |
| Secrets | Android Keystore-backed encryption for API keys |
| Scheduling | FSRS implementation in a pure-Kotlin module (unit-tested against reference vectors) |
| Background work | WorkManager for backups, reminders, and FSRS optimization |
| Rendering | Markdown + LaTeX in card faces |
| Files | PDF text extraction on device; `.apkg` = zip + SQLite, parsed on device |
| Testing | JUnit unit tests for the scheduler, parsers, and repositories; Compose UI tests for key flows |

Build constraints from `CLAUDE.md` still apply: AGP 9 with built-in Kotlin (no `kotlin-android` plugin), dependencies only through `gradle/libs.versions.toml`, and repositories only in `settings.gradle.kts`.

### 7.1 Module layout

See [ARCHITECTURE.md](ARCHITECTURE.md) for the module map, dependency rules, folder structure, and key data flows.

## 8. Roadmap

Summary below. See [ROADMAP.md](ROADMAP.md) for each phase's scope and exit criteria.

| Milestone | Scope |
|---|---|
| **M0 — Foundation** | Architecture, DI, Room schema, design system, bottom navigation, theme matching the mockups |
| **M1 — Core SRS (MVP)** | Decks CRUD, manual card creation (Basic, Reversed, Cloze), study session with FSRS, gestures, undo, daily limits |
| **M2 — Anki interop and data safety** | `.apkg` import/export, backup/restore, auto-backups |
| **M3 — AI providers** | Provider list management, presets, test connection, encrypted keys, per-task routing |
| **M4 — AI creation** | Smart Extract (paste, PDF, link, dictation), review queue, Explain/Rewrite on cards |
| **M5 — Analytics** | KPIs, forgetting curve, heatmap, forecast, leeches, FSRS optimizer |
| **M6 — Polish** | Reminders, widgets, media/TTS, AI Co-Author, accessibility pass, tablet layouts |
| **Later** | Optional sync, desktop/web companion, shared decks |

## 9. Non-goals (v1)

- Cloud accounts, a hosted backend, or any server run by Mnemo.
- Proprietary vendor SDKs (every AI call goes through the OpenAI-compatible protocol).
- Full parity with Anki's add-on ecosystem and custom HTML/JS card templates.
- Social or community features.

## 10. Success criteria

- A new user can create a deck and finish a first review session in under 2 minutes.
- An Anki user can import an existing `.apkg` and keep studying with their review history intact.
- A 1,000-word note becomes accepted AI cards in under 30 seconds, with any configured provider.
- The study loop runs smoothly at 60/120 fps on mid-range devices.
- Airplane mode breaks nothing except AI features.

## 11. Open questions

- **Scheduling defaults:** the Decks mockup mentions SuperMemo-2 while Study and Analytics show FSRS. Proposal: FSRS only, with SM-2 values converted on import.
- **Sync strategy:** file-based sync vs. an optional self-hostable server, and when.
- **Local on-device models** (e.g. Gemini Nano / AICore) as an extra non-network "provider"?
- ~~**Card template flexibility:** how much of Anki's HTML/CSS templating to support for imported decks.~~ Decided in ADR 0003: fields, not templates.
- **Monetization / licensing:** open source? Paid? Affects distribution (Play Store, F-Droid).
