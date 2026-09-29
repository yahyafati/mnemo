# Mnemo — Pre-launch QA (release ROADMAP R3)

Every check below runs on the **release** build (R8 on), because that is what Play and F-Droid ship.
Debug builds skip minification, so a check that passes there proves little about missing keep rules.
Record each result in [the results log](#results-log) with the device, OS and build. When every row
is green, move the log's summary into [ADR 0008](../adr/0008-v1-polish.md) "Open" and empty that
section (R3's exit).

The commands that a machine can run are in `scripts/qa/device-checks.sh` (needs `adb`; run it with no
arguments for the list). The rest needs a person looking at the screen or listening to the audio.

## 0. Set up

**Devices** (the more of these, the better; the first two are the minimum):

| Role | What | Used for |
|---|---|---|
| Mid-range phone | A 60 Hz phone from the last 3–4 years (for example a Pixel 6a or Galaxy A5x) | Frame rate, TTS, dictation, most of the smoke test |
| High-refresh phone | 90/120 Hz (Pixel 8 or a Galaxy S) | 120 fps |
| Second launcher | Pixel Launcher **and** Samsung One UI | Widget, launcher icon, splash (R1's leftover check) |
| Tablet or foldable emulator | 10" tablet and a foldable (API 35) | Layout pass |

**Build:**

```bash
./gradlew assembleDebug testDebugUnitTest lint                                      # exit check, first
./gradlew :core:ai:test :core:anki:test :core:model:test :core:scheduler:test :core:common:test
./gradlew bundleRelease                                                              # signed, see signing.md
python3 scripts/check-16kb-alignment.py app/build/outputs/bundle/release/app-release.aab
bundletool build-apks --bundle=app/build/outputs/bundle/release/app-release.aab \
  --ks=<upload keystore> --ks-key-alias=<alias> --output=mnemo.apks
scripts/qa/device-checks.sh install mnemo.apks
```

Test what Play delivers (`.apks` from the bundle), not a universal APK. If you only have an unsigned
build, `assembleRelease` plus `apksigner` with a throwaway key is enough for this pass, but then the
upgrade check (§3) needs the *same* key on both builds.

Before you start, run `scripts/qa/device-checks.sh info` and paste its output into the log.

## 1. Hardware checks (ADR 0008)

### 1.1 Frame rate: study loop and Analytics

Goal: no visible jank at 60 Hz, and at 120 Hz on a device that offers it. Use a collection with at
least a few hundred cards and 60+ days of history: `docs/release/assets/demo/seed_demo.py` builds one
(see `assets/README.md`), or restore a real backup.

For each scenario: `frames reset`, run the scenario, `frames report <label>`.

| Scenario | Do | Pass when |
|---|---|---|
| Study, basic + cloze | Answer 40 cards with taps and 20 with swipes; use undo three times | Janky frames < 5 %, 95th percentile within one frame budget more than the target (16 / 8 ms) |
| Study, math card | Open 10 cards with math (KaTeX WebView) | No blank flash between cards, no ANR |
| Study, tablet layout | Same as the first row, on the tablet | Same |
| Analytics | Open the tab, scroll it top to bottom twice, switch the range and deck filter | Janky frames < 5 %, first content < 1 s |
| Decks | Scroll a list of 100+ decks (create with `seed_demo.py` or an import) | Same |

At 120 Hz check *Settings › Developer options* isn't forcing 60 Hz, and that `info` lists 120 fps. A
failure here is a P1 if the study loop stutters; Analytics is P2 unless it drops below 30 fps.

### 1.2 TTS and audio

- [ ] Settings › Study › "Play card audio automatically" on: a card with `[sound:…]` plays as it
      appears; off: it stays silent and the 🔊 Play link works.
- [ ] A card without a sound: the speaker button reads the visible side aloud. Try English and one
      other language whose voice you have installed. If the language voice is missing, the app must
      not crash or hang (it may stay silent).
- [ ] Audio focus: start music (Spotify, YouTube Music), then flip a card with audio. Music should
      duck or pause, then resume afterwards. Then the reverse: a phone call or another app's audio
      arrives mid-card; Mnemo stops rather than talking over it.
- [ ] Bluetooth headphones: audio goes to the headset. Unplugging or disconnecting them mid-card
      doesn't crash.
- [ ] Rapid taps on the speaker button and rapid card changes: no overlapping voices.
- [ ] Leaving the study screen (Home button, back) stops the audio.

### 1.3 Dictation (Smart Extract › Dictation)

- [ ] First use: the "why" dialog appears **before** the system microphone prompt. Deny once: a
      message explains where to allow it; nothing crashes. Allow: dictation starts.
- [ ] Dictate two or three sentences. The text lands in the editable box, and can be edited.
- [ ] On a device **without** on-device or online speech recognition (or with the Google app
      disabled), the app shows a clear message instead of failing silently.
- [ ] Airplane mode: dictation either works on-device or says it can't. No crash.
- [ ] Rotate the phone and background the app mid-dictation: the recogniser is released (the
      microphone indicator goes away).

### 1.4 Widget

Do this on **Pixel Launcher and Samsung One UI**, both light and dark, and on the tablet emulator.

- [ ] It appears in the widget picker with the preview image, and adds at its default size.
- [ ] It shows the to-study count, due/new/minutes (or the streak), and the Study button.
- [ ] Study button opens the Study tab (cold and warm).
- [ ] Answer cards in the app, return to the home screen: the count updates within a few seconds.
- [ ] Change the system font size and the widget's size: text doesn't clip.
- [ ] Wait past 4 a.m. (or change the device date): the count follows the new study day. Reboot:
      the widget comes back with data, not "Loading".
- [ ] Remove it and add it again; clear the app's data and check it shows the empty state, not a crash.

### 1.5 Launcher icon and splash (R1's leftover)

- [ ] Pixel: default icon and **themed icon** (Wallpaper & style › Themed icons); dark and light splash.
- [ ] Samsung One UI: the icon isn't cropped or given an odd background; the splash isn't a blank frame.
- [ ] Android 12+ splash: no double splash, no flash of the wrong theme (start the app with the system
      in dark mode while the app is set to light, and the reverse).

## 2. Providers and Smart Extract (Phases 3–4)

Use your own keys. **Never** paste a key into an issue, a log, or this file.

- [ ] **Hosted provider** (OpenAI, OpenRouter, Groq, …): Settings › AI providers › add › **Test
      connection**. It reports the models found and the capabilities (streaming, JSON output). Then
      try a bad key (401 message, no crash) and a made-up model name.
- [ ] **Local provider** (Ollama or LM Studio on the LAN): base URL `http://<lan-ip>:11434/v1`, marked
      local. **Test connection** works over plain HTTP. Unmarking "local" must refuse HTTP. Turn the
      server off: the error is readable.
- [ ] The disclosure dialog appears before the first request to each provider.
- [ ] Smart Extract, **1,000 words in under 30 s**: paste a plain 1,000-word article (check with
      `wc -w`), tap Generate, start a stopwatch. Stop at the last card of the streamed queue. Cards
      should start appearing well before that. Accept them. Repeat with a hosted and a local model
      and record the model, since a small local model on a laptop CPU may legitimately miss 30 s;
      write that down rather than hiding it. The claim in the ROADMAP is about "any configured
      provider", so decide (and record) whether a slow local model changes the wording in the
      listing or only needs a note in the docs.
- [ ] Also once each: PDF, link and dictation sources; Explain / Example / Rewrite while studying;
      Co-Author chat and "Suggest missing cards".
- [ ] Every AI output has a **Report** button that asks first, then opens the prefilled GitHub issue.
      Check that the issue text holds the output, the feature and the model, and no key.
- [ ] To repeat these without spending tokens: `scripts/qa/device-checks.sh mock-ai` serves canned
      replies (this checks the plumbing, not real-provider behaviour).

## 3. Release smoke test (R8)

The point is to catch what R8 strips. Use the release build throughout.

1. **Fresh install**, empty collection → onboarding shows. Go through the pages, name a first deck,
   and check that it opens the editor. Uninstall, reinstall, and this time choose Import.
2. **Import a real `.apkg`** (Decks › Import Anki (.apkg)): your own collection if you have one, big, with images, audio and
   math. `scripts/qa/device-checks.sh push-apkg <file>` copies it to Downloads (with no argument it
   pushes a small one written by real Anki). Watch the progress notification (the permission "why"
   dialog comes first). Compare the deck tree, note counts and a few cards with Anki. Check images,
   audio and a math card render.
3. **Study with every card type**: Basic, Basic + Reversed, Cloze (with several numbers), Type-in
   (right and wrong answer), Multiple choice, a card with a hint, and one with audio and one with
   math. Rate with buttons and swipes, undo, and check that the next-interval labels change with the
   rating.
4. **Create** one of each kind manually, with an image and a sound attached from the picker.
5. **Analytics** and the Decks retention tiles: numbers are plausible; run **Optimize** in Settings ›
   Scheduling (it applies only if it lowers the loss; either result is fine, a crash isn't).
6. **Exports:** export a deck to `.apkg`, and open it in Anki desktop or AnkiDroid. JSON export opens.
7. **Backup → uninstall → reinstall → restore.** Settings › Data › Back up now, save to a folder.
   Uninstall, reinstall, restore from that file. Everything is back: decks, cards, history, media,
   settings. **API keys are not** (they never leave the device); providers come back without keys.
8. **Upgrade path**: install the previous internal/closed-test build, create data on it (studied
   cards, a media card, a reminder), then install the new build over it with
   `scripts/qa/device-checks.sh install app-release.apk` (`-r` keeps the data). Same signing key
   is required. Check: the app opens without onboarding, everything from step 3 is still there, the
   database migrated (no crash on the first launch), keys still work, the reminder still fires.
   If the two builds share a schema version, also do the once-only check from a v3 install on the
   last debug build that had it, since `MigrationTest` proves the SQL but not the app.
9. **R8 spot checks** (a stripped class shows up as a crash on one screen, not at launch): open every
   Settings screen including Licenses and the privacy policy, Smart Extract with a PDF (PdfBox),
   the link source, a math card, the widget, the reminder notification, the Report button.

## 4. Airplane mode

Turn on airplane mode (`scripts/qa/device-checks.sh airplane on`), then Wi-Fi and Bluetooth stay off.

- [ ] Cold-start the app, study, create, edit, browse, search, Analytics, import/export, backup and
      restore, settings, the widget, the reminder, and the licenses and privacy screens all work.
- [ ] Math cards render (KaTeX is bundled; the WebView blocks network loads).
- [ ] AI surfaces (Smart Extract, Explain/Example/Rewrite, Co-Author, Test connection, link source)
      fail with a readable message and a retry, never a crash or a spinner that runs forever.
- [ ] The app didn't ask for anything network-related on start. (A code review on 2026-09-29 found
      network code only in `:core:ai`, the link importer in `:core:ingest` and the Report button,
      which opens the browser; the card WebView sets `blockNetworkLoads`.)

## 5. Accessibility and layouts

- [ ] **TalkBack** on one phone: Decks → open a deck → study a card → reveal → rate (and the
      "Answer Again/Good" custom actions) → editor → Settings. Everything is announced, in a sensible
      order, with no unlabelled buttons. Multiple-choice options say correct/wrong after picking.
      Co-Author replies are read as they stream, without repeating.
- [ ] **Font size 200 %** (Settings › Display › Font size, and Display size at the largest): study
      screen, editor, Decks, Analytics and Settings still show every control, with nothing clipped.
- [ ] **Tablet and foldable emulators** (10" tablet, Pixel Fold or Galaxy Z Fold, API 35): navigation
      rail, Decks grid, study side by side, tabletop posture, unfolding mid-study keeps the card and
      the answer state, and rotating doesn't lose text in the editor.
- [ ] Dark and light themes, and dynamic colour on/off, look right on Decks, Study and Analytics.
- [ ] RTL (set the device language to Arabic or Hebrew): nothing breaks. Only a broad visual check.

## 6. Triage

- [ ] Every failure above is a GitHub issue labelled `P0`–`P3` (P0: data loss, crash at launch or in
      the study loop; P1: a core flow broken or a store-policy problem; P2: wrong but recoverable;
      P3: polish).
- [ ] **No open P0 or P1.** P2/P3s are listed in the release notes' known issues or deferred.
- [ ] The Android vitals and pre-launch report (R4) are checked again when the build goes up.

---

## Results log

One row per check per device. **Result:** ✅ pass, ❌ fail (link the issue), ➖ not applicable.
Build = `versionName (versionCode)` and how it was installed.

| Check | Device | Android | Build | Result | Notes |
|---|---|---|---|---|---|
| 1.1 Frame rate, study (60 Hz) | | | | | |
| 1.1 Frame rate, study (120 Hz) | | | | | |
| 1.1 Frame rate, Analytics | | | | | |
| 1.2 TTS voices | | | | | |
| 1.2 Audio focus | | | | | |
| 1.3 Dictation | | | | | |
| 1.4 Widget, Pixel Launcher | | | | | |
| 1.4 Widget, Samsung One UI | | | | | |
| 1.5 Icon and splash, Pixel | | | | | |
| 1.5 Icon and splash, Samsung | | | | | |
| 2 Test connection, hosted | | | | | |
| 2 Test connection, local | | | | | |
| 2 Smart Extract 1,000 words < 30 s | | | | | |
| 3 Fresh install and onboarding | | | | | |
| 3 Import real `.apkg` | | | | | |
| 3 Study, every card type | | | | | |
| 3 Backup, reinstall, restore | | | | | |
| 3 Upgrade from previous build | | | | | |
| 3 R8 spot checks | | | | | |
| 4 Airplane mode | | | | | |
| 5 TalkBack | | | | | |
| 5 200 % font | | | | | |
| 5 Tablet and foldable | | | | | |
| 6 No open P0/P1 | | | | | |
