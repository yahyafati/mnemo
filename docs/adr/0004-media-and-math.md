# ADR 0004: Media storage, math rendering and backups

- **Status:** Accepted
- **Date:** 2026-09-29
- **Context for:** ROADMAP Phase 2 (media, backup and export); ARCHITECTURE §5.4, §6, §8

## Decision

### Media

- Files live in `filesDir/media/<sha256>`, one per distinct content. `MediaEntity` (id = the
  hash) keeps the original name (for Anki export) and MIME type. The same image imported twice is
  stored once.
- Card Markdown refers to media as `media:<sha256>`: `![alt](media:…)` and `[sound:media:…]`.
  Hashes, not names, because decks from different sources reuse names like `image.png`.
- `MediaCleanupWorker` runs weekly and deletes media no live note refers to, plus stray files.
  Anything younger than a day is kept, so an import or edit in progress never loses a file.
- Images render natively (`MediaImage`, decoded off the main thread and downsampled). Remote
  images and formats Android can't decode (SVG) show their alt text: cards never touch the
  network. Sounds show a marker until audio arrives (Phase 6).

### Math

- Only cards that contain math (`\(…\)`, `\[…\]`, `$$…$$`) use a WebView, with KaTeX bundled in
  `:core:ui` assets (offline). The same Markdown AST renders to HTML (`MarkdownHtml`, in
  `:core:model`), themed from the Compose color scheme.
- The WebView serves everything through `WebViewAssetLoader` (KaTeX, fonts, media), blocks
  network loads, and reports its height to size itself. Revealing a cloze toggles a class
  instead of reloading.

### Backups

- A backup is a zip: `manifest.json`, a consistent copy of the database (WAL checkpoint, then a
  copy under a write transaction), the preferences file and every media file.
- Restore stages the files and restarts the app; `MnemoApplication` applies them before Hilt
  creates the database or DataStore. A backup from a newer schema is refused; an older one is
  migrated by Room.
- Automatic backups write daily to a folder the user picks (persisted SAF permission) and keep
  the last seven; manual backups use a different name prefix so they are never pruned.
- Android Auto Backup covers the database and preferences but not media (the 25 MB cloud cap
  would fail the whole backup); device-to-device transfer includes media.

## Consequences

- One source for card text (Markdown), rendered natively almost everywhere; math cards pay a
  WebView (~a few ms to start) only when needed.
- A cloud-restored install has cards but no media until a Mnemo backup is restored.
- The markdown parser moved from `:core:ui` to `:core:model` so the exporter and the renderer
  share it (amends ADR 0002, point 1).
