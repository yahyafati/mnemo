# Architecture decision records

Each ADR records one decision, why it was made, and (for the later ones) what was found while building it
("Findings from …", "As built (…)"). They are history: a later ADR or the code can supersede a detail, and
`CLAUDE.md` and `docs/ARCHITECTURE.md` describe the code as it is now.

| ADR | Decision |
|---|---|
| [0001](0001-fsrs-as-scheduler.md) | FSRS is the only scheduler |
| [0002](0002-in-house-markdown-renderer.md) | An in-house Markdown subset for card content |
| [0003](0003-anki-interop.md) | How Anki decks map to Mnemo (fields, not templates) |
| [0004](0004-media-and-math.md) | Media storage, math rendering and backups |
| [0005](0005-ai-providers-and-secrets.md) | AI providers, API keys and network rules |
| [0006](0006-ai-card-creation.md) | AI card creation (Smart Extract, study-time AI) |
| [0007](0007-analytics-and-fsrs-optimizer.md) | Analytics as SQL aggregates, and the on-device FSRS optimizer |
| [0008](0008-v1-polish.md) | Type-in and multiple-choice cards, audio, Co-Author, reminders, widget, layouts |
| [0009](0009-license-and-distribution.md) | GPL-3.0-or-later; free distribution |
| [0010](0010-desktop-with-compose-multiplatform.md) | The desktop app with Compose Multiplatform and Koin |
| [0011](0011-epub-books.md) | EPUB books: one empty deck per chapter, cards on demand |
| [0012](0012-web-extraction.md) | Links as Markdown, site extractors (Wikipedia) |
| [0013](0013-sync.md) | Sync between devices through user-owned storage, no server |
| [0014](0014-pdf-pages-and-images.md) | PDF page ranges, AI reading of scanned pages, cards from page images, figures |
| [0015](0015-context-aware-extraction.md) | Context-aware Smart Extract: fewer filler cards, heading-aware parts, overlap, a context summary (C0–C3 built) |

## Step names

Each feature was built in numbered steps, and the ADRs and code comments still name them. The roadmaps that
listed the steps were removed once the work landed (they are in the git history).

| Prefix | Work | ADR |
|---|---|---|
| Phase 0–6 | The Android app up to v1: foundation, core SRS, Anki interop and backups, AI providers, AI creation, analytics, polish | 0001–0008 |
| R0–R7 | Release to Google Play and F-Droid (license, brand, signing, QA, store setup) | 0009, [release](../release/README.md) |
| D0–D9 | The desktop app (D3 platform seams, D4 KMP data layer, D5 shared UI, D6 shell, D7 keyboard and mouse, D8 installers, D9 QA) | 0010 |
| B0–B7 | EPUB books | 0011 |
| W0–W7 | Web extraction | 0012 |
| S0–S8 | Sync (S1 change recording, S2 format and stores, S3 merge engine, S4 lifecycle, S5 screen, S6 Google Drive, S7 WebDAV, S8 QA) | 0013 |
| P0–P8 | PDF pages and images (P1 page ranges, P2 chapters, P3 rendering, P4 images in requests, P5 AI reading, P6 cards from page images, P7 figures, P8 polish) | 0014 |
| C0–C7 | Context-aware Smart Extract (C1 fewer filler cards, C2 heading-aware parts, C3 overlap, C4 context summary, C5 Advanced settings, C6 PDF bookmarks as headings, C7 QA); C0–C3 built | 0015 |

"Sideload S0–S7" in older comments is the direct-distribution plan (GitHub Releases and the download page), now
described in [`../release/README.md`](../release/README.md); it is unrelated to sync's S steps.
