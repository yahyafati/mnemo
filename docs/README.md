# Mnemo docs

Every document in this folder, grouped by who reads it. New to the code? Read
[PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md), then [ARCHITECTURE.md](ARCHITECTURE.md), then the ADR of the area
you are touching. `CLAUDE.md` at the repo root is the working reference for the code as it is now (commands,
build setup, conventions per area).

## The product and the code

| Document | What it is |
|---|---|
| [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md) | What Mnemo is, its features and principles |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Modules, layers, dependency rules, data flows, folder structure |
| [design/](design/) | The HTML mockups the brand palette and screens come from: [decks](design/decks.html), [study and review](design/active-study-review.html), [AI card creator](design/ai-card-creator.html), [analytics](design/analytics.html) |

## Decisions (ADRs)

[adr/README.md](adr/README.md) is the index, and explains the step names (Phase 0–6, R, D, B, W, S, P) that
code comments use.

| ADR | Decision |
|---|---|
| [0001](adr/0001-fsrs-as-scheduler.md) | FSRS is the only scheduler |
| [0002](adr/0002-in-house-markdown-renderer.md) | An in-house Markdown subset for card content |
| [0003](adr/0003-anki-interop.md) | How Anki decks map to Mnemo |
| [0004](adr/0004-media-and-math.md) | Media storage, math rendering and backups |
| [0005](adr/0005-ai-providers-and-secrets.md) | AI providers, API keys and network rules |
| [0006](adr/0006-ai-card-creation.md) | AI card creation (Smart Extract, study-time AI) |
| [0007](adr/0007-analytics-and-fsrs-optimizer.md) | Analytics and the on-device FSRS optimizer |
| [0008](adr/0008-v1-polish.md) | Card types, audio, Co-Author, reminders, widget, layouts |
| [0009](adr/0009-license-and-distribution.md) | GPL-3.0-or-later; free distribution |
| [0010](adr/0010-desktop-with-compose-multiplatform.md) | The desktop app with Compose Multiplatform and Koin |
| [0011](adr/0011-epub-books.md) | EPUB books |
| [0012](adr/0012-web-extraction.md) | Web extraction and site extractors |
| [0013](adr/0013-sync.md) | Sync between devices, no server |
| [0014](adr/0014-pdf-pages-and-images.md) | PDF pages, scanned pages and page images |

## Desktop

| Document | What it is |
|---|---|
| [desktop/install.md](desktop/install.md) | For users: download, install, update, uninstall, data locations, moving a collection |
| [desktop/qa.md](desktop/qa.md) | QA runbook and results log for the installers on each OS, and release day |

## Sync

| Document | What it is |
|---|---|
| [sync/help.md](sync/help.md) | For users: setting up sync, status messages, forgotten passphrase, restore, new phone |
| [sync/google-setup.md](sync/google-setup.md) | Google Drive client ids and the build settings that turn Drive on |
| [sync/qa.md](sync/qa.md) | QA runbook and results log for sync on real hardware |

## Release

[release/README.md](release/README.md) is the start: channels, what every release needs, and the owner's open
tasks.

| Document | What it is |
|---|---|
| [release/signing.md](release/signing.md) | The keystore, the Actions secrets, the release routine |
| [release/qa.md](release/qa.md) | Android pre-launch QA runbook and results log |
| [release/fdroid.md](release/fdroid.md) | Publishing on F-Droid; the recipe is [release/fdroid/com.yahyafati.mnemo.yml](release/fdroid/com.yahyafati.mnemo.yml) |
| [release/play-console.md](release/play-console.md) | Google Play Console setup, every form in order |
| [release/store-listing.md](release/store-listing.md) | Play store texts and data safety (draft) |
| [release/privacy-policy.md](release/privacy-policy.md) | The privacy policy; the app links to it, so don't move it |
| [release/release-notes.md](release/release-notes.md) | The template of every GitHub release's notes |
| [release/assets/README.md](release/assets/README.md) | Logo, icons, store graphics, screenshots and the scripts that make them |
