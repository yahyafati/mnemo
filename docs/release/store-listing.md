# Store listing (draft)

For Google Play. F-Droid's copy of these texts is `fastlane/metadata/android/en-US/` (see
[fdroid.md](fdroid.md)); it drops the Play-only lines, so edit both when the wording changes.
Limits: title 30, short description 80, full description 4,000 characters.

## Title

Mnemo: Spaced Repetition

## Short description

Flashcards that schedule themselves. Offline, private, with optional AI.

## Full description

Mnemo helps you remember what you learn, with a few minutes of review a day.

**Study smarter with FSRS**
Mnemo schedules every card just before you'd forget it, using FSRS, the modern algorithm now in
Anki. It can even fit the algorithm to your own review history, on your phone.

**Fast, pleasant reviews**
• Tap to flip; swipe left for Again, right for Good
• Basic, reversed, cloze, type-in and multiple-choice cards
• Images, math (LaTeX), code, audio, text-to-speech and hints
• Undo, star, flag, bury and suspend without leaving the session

**Bring your Anki decks**
Import .apkg and .colpkg files with their review history and media, and export back to Anki.

**See your progress**
True retention, a forgetting curve, a review calendar, forecasts, per-deck maturity and your
hardest cards, all computed on your device.

**Optional AI, your provider**
Turn notes, PDFs, links and dictation into cards, ask for explanations while studying, or let
Co-Author suggest missing cards and fix the ones you keep forgetting. Works with any
OpenAI-compatible provider you choose (OpenAI, OpenRouter, Groq, Mistral, Gemini, DeepSeek, or
your own Ollama or LM Studio). Nothing is sent until you set one up and confirm it.

**Sync your devices, optionally**
Keep your phone and computers in step without an account or a Mnemo server. Each device writes its changes to a place you own: a folder (with Syncthing,
Nextcloud or similar), a WebDAV server such as Nextcloud, or a hidden folder in your Google Drive. Studying offline on several devices merges cleanly, no review is
lost, and a passphrase encrypts everything before it leaves the device. Off until you turn it on.

**Private by design**
No account. No ads. No tracking. Your cards stay on your phone, with backups and exports to
wherever you choose.

Also: daily reminders, a home-screen widget, exam countdowns, dark theme, Material You colors,
tablet and foldable layouts, and TalkBack support.

## Category and tags

Education. Tags: flashcards, spaced repetition, study, memory, Anki.

## Content rating

Everyone. No user-generated content is shared; no ads; no data collection.

## Data safety form (Google Play)

- Data collected: **none**. Data shared: **none**.
- Note for the reviewer: content sent to AI providers goes from the device directly to a
  third-party endpoint the user configures, at the user's request, after an in-app disclosure; the
  developer operates no service and receives nothing. (Play treats user-initiated transfers to a
  service the user chose as not "shared"; confirm against the current form wording.)
- Sync (optional, off by default): the user's collection goes to storage the user chooses (a folder, their own WebDAV server, or the hidden app-data folder of their own Google Drive,
  `drive.appdata` only), encrypted with the user's passphrase if they set one. The developer receives nothing. Same reading as for AI providers: a transfer the user starts to a service
  they chose; confirm against the current form wording **(owner, with the Google consent screen's data-use text)**.
- Encrypted in transit: yes (HTTPS; plain HTTP only to user-marked local servers and local WebDAV servers).
- Deletion: uninstalling removes everything; decks and cards can be deleted in the app.

## Screenshots

Final files and their order are in `play-console.md` §3. Phone (1080×2400): Decks, a study card (basic and multiple choice),
Smart Extract review queue, Co-Author, Analytics. Tablet: Decks grid and study side by side. The
Roborazzi baselines in `feature/*/src/test/screenshots` show the intended layouts.

## Before publishing

- [ ] Decide licensing and distribution (`distribution.md`).
- [ ] App signing key (Play App Signing) and release `signingConfig` (never committed).
- [ ] Contact address in the privacy policy, and host it at a public URL for the listing (Pages workflow: `play-console.md` §2).
- [ ] `versionCode`/`versionName` for 1.0.
- [ ] Manual checks in ADR 0008 "Open" ([qa.md](qa.md)).
