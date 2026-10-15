# Mnemo — Project Overview

> A modern, local-first spaced-repetition app for Android and the desktop (Windows, macOS, Linux), with AI card
> creation through any OpenAI-compatible provider.

| | |
|---|---|
| **Status** | Feature-complete for v1 on Android and the desktop; waiting on the owner's hardware QA and first release ([release/README.md](release/README.md)) |
| **Platforms** | Android (minSdk 29, target 37) and the desktop (JVM), one Kotlin Multiplatform codebase with Compose Multiplatform and Material 3 |
| **Package** | `com.yahyafati.mnemo` |
| **License** | GPL-3.0-or-later (ADR 0009) |

## Docs

| Doc | For |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Modules, layers, dependency rules, platform seams, key flows |
| [adr/](adr/README.md) | Why things are the way they are, one decision per record, and the step names (D4, S3, P5 …) used in code comments |
| [design/](design/) | HTML mockups of Decks, Study, Create and Analytics: the visual reference, not app code |
| [release/](release/README.md) | Channels, signing, store texts, privacy policy, Android QA, what is left before the first release |
| [desktop/install.md](desktop/install.md), [desktop/qa.md](desktop/qa.md) | Desktop users' install guide; the desktop QA runbook |
| [sync/help.md](sync/help.md), [sync/google-setup.md](sync/google-setup.md), [sync/qa.md](sync/qa.md) | Sync for users; the Google client ids; the sync QA runbook |

`CLAUDE.md` at the repo root is the working guide to the code (commands, build setup, conventions per area).

## Vision

Anki's scheduling works, but the app is dated, hard to learn, and makes creating cards slow. Mnemo keeps what works
(spaced repetition, decks, `.apkg` compatibility) and changes three things:

1. **Modern look and feel.** A polished Material 3 interface with gestures, motion and clear stats.
2. **Local first.** Cards, review history and settings live on the device. Everything works offline with no
   account; nothing leaves the device unless the user asks.
3. **Bring your own AI.** Card generation and study help work with any OpenAI-compatible endpoint the user
   configures: OpenAI, OpenRouter, Groq, DeepSeek, Mistral, Gemini, a self-hosted Ollama or LM Studio, and others.

## Principles

| Principle | In practice |
|---|---|
| **Offline is the default** | Study, browse, edit, stats, import/export work with no network. AI is optional and degrades gracefully. |
| **The user owns the data** | Standard SQLite storage, full backup, `.apkg` import/export, JSON export, no lock-in. |
| **Provider-agnostic AI** | No vendor SDKs. Providers are data the user edits, not code. |
| **Privacy by default** | No analytics or telemetry. API keys encrypted outside the database. A notice before the first request to each provider. |
| **Fast study loop** | One gesture per review, no loading between cards. |
| **Proven scheduling** | FSRS-6, with an on-device optimizer fitted to the user's review log. |

## Features

- **Decks.** Nested decks (`Parent::Child`), today's due/new/learning counts, streak and retention tiles, per-deck
  retention health, starred decks, exam countdowns, sharing a deck as `.apkg` (Android).
- **Study.** Tap to flip, swipe for Again/Good, four rating buttons with their next interval, undo. Basic, Basic +
  Reversed, Cloze, Type-in and Multiple choice cards; Markdown, images, math, audio and text-to-speech, hints.
  Bury, suspend, star, flag and edit from the review screen.
- **Create.** A manual editor for every card type. **Smart Extract** turns pasted text, a PDF (page ranges, chapters,
  scanned pages read by a vision model, cards straight from page images, figures cropped onto cards), a link
  (Markdown, Wikipedia sections), an EPUB book (one deck per chapter) or dictation (Android) into a review queue;
  nothing is saved until the user accepts. **Co-Author** suggests missing cards, finds duplicates and improves weak
  cards in a deck. While studying: Explain, Example and Rewrite.
- **Browse.** Search, filter and bulk-edit every card.
- **Analytics.** True retention, review volume, stability, time saved, forgetting curve, activity calendar,
  forecast, deck maturity, hardest cards. Computed locally from the review log.
- **Settings and data.** AI providers (presets, test connection, image check, per-task routing, token usage),
  scheduling and the FSRS optimizer, appearance (brand palette or dynamic color), study audio, a daily reminder and
  home-screen widget (Android), backup/restore (manual and daily), Anki import/export, JSON export.
- **Sync** (optional, off by default). Between the user's own devices through a folder, Google Drive's hidden app
  folder or a WebDAV server, end-to-end encrypted with a passphrase. No Mnemo server.
- **Desktop.** The same app in a window, with keyboard shortcuts, a menu bar, right-click menus, drag and drop, and
  installers for every system.

## Non-goals

- Cloud accounts, a hosted backend, or any server run by Mnemo.
- Proprietary SDKs (every AI call is the OpenAI-compatible protocol; no Play Services or Firebase).
- Anki's add-ons and custom HTML/JS card templates (ADR 0003: fields, not templates).
- Social features or shared decks between people.

## Success criteria

- A new user creates a deck and finishes a first review session in under 2 minutes.
- An Anki user imports an `.apkg` and keeps studying with their review history intact.
- A 1,000-word note becomes accepted AI cards in under 30 seconds, with any configured provider.
- The study loop runs at 60/120 fps on mid-range phones.
- Airplane mode breaks nothing except AI and sync.

## Ideas for later

On-device models as a provider, richer Anki templates, signed desktop installers, more site extractors (ADR 0012),
desktop dictation and text-to-speech, an opt-in update check.
