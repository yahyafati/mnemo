# Mnemo for desktop: QA runbook (desktop ROADMAP D9)

The pass that decides whether desktop v1.0 goes public. Every check runs on the **installer a user would
download**, not on `:desktop:run`: the packaged app has a trimmed Java runtime (D8), file associations and
an installer of its own, and each of those can fail where the development build doesn't. Do it on real
machines, one per row of the table below, and record every result in [the results log](#results-log). It is the
desktop twin of the Android runbook ([../release/qa.md](../release/qa.md)); the checks the two share (AI,
backup, airplane mode) are worded the same on purpose.

The parts a machine can do are in `scripts/qa/desktop-checks.py` (run it with `--help`). The rest needs a
person at the keyboard looking at the window and listening to the audio.

**Not bugs** (known gaps, listed in the release notes): unsigned installers and their first-run warning, no text to speech, dictation, reminder or widget, sound only in `wav`, `mp3` and `ogg`, and
math that JLaTeXMath cannot draw showing as raw TeX. Sync, which this release also has, is checked by its own runbook, [../sync/qa.md](../sync/qa.md).

## 0. Set up

**Machines** (one of each row is the minimum; a VM is fine except for the rows that say *real display*):

| Role | What | Used for |
|---|---|---|
| Windows | Windows 11 x64, and Windows 10 if you have it. One machine with a **user name that has a space or a non-ASCII letter** (for example `José Núñez`) | `.msi`, SmartScreen, per-user install, non-ASCII paths (§8), scaling 100/150/200 % on a *real display* |
| macOS | Apple Silicon, and an Intel Mac if you have one (the Intel `.dmg` is built on an Intel runner and only ever started there by CI). Run the Gatekeeper step on macOS 15 or newer, and on 14 or older if you have one | `.dmg`, Gatekeeper, application menu, Dock, Retina |
| Linux (Debian family) | Ubuntu LTS, GNOME, **Wayland** session; and once on an **X11** session | `.deb`, the libraries list in `install.md`, both display servers |
| Linux (Fedora family) | Fedora, KDE or GNOME | `.rpm` |
| Linux without a keychain | Any desktop where no Secret Service answers (a bare window manager, or `gnome-keyring` stopped) | The key-file fallback (§3) |
| Portable | Any Linux x86-64 | `.tar.gz`, run from a folder with a space in its name |

You also need: a phone with the Android release build (for §4), an Anki install to open our exports, a hosted AI
provider key and a local Ollama or LM Studio (§3), and a real Anki collection of your own with 10,000+ cards, images,
audio and math if you have one (otherwise §2.2 builds one).

**Get the build.** Before the tag exists, run *Actions › Release › Run workflow* on the commit to test:
it keeps the installers as workflow artifacts. After a `v*` tag the same workflow attaches them to a **draft**
release. Download only the file for each machine, then check it:

```bash
python3 scripts/qa/desktop-checks.py verify-sums <folder with the installers and SHA256SUMS.txt>
python3 scripts/qa/desktop-checks.py info      # paste the output into the log, per machine
```

The first GitHub run of the `desktop` job in `ci.yml` and of `release.yml` has never happened (D1, D4, D8
leave it open). If either is red, fix that first: it is a P0 for this pass.

**Automated exit check, first** (on any one machine, from the commit being released):

```bash
./gradlew assembleDebug testDebugUnitTest testAndroidHostTest lint verifyRoborazziAndroidHostTest
./gradlew :core:ai:test :core:anki:test :core:model:test :core:scheduler:test :core:common:test :core:sync:test
./gradlew desktopTest :desktop:test
./gradlew verifyRoborazziDesktop          # macOS arm64 only: the baselines are recorded there
python3 scripts/desktop/check-android-imports.py
python3 scripts/fdroid/check-foss-deps.py
```

Reset between runs with an empty collection by starting the app with `MNEMO_DATA_DIR=<empty folder>` (the
scratch folder of a run): you don't have to uninstall to test a first run, except in §1.

## 1. Install, update, uninstall

Do this on **each** machine, with the real installer.

### 1.1 Install and first run

- [ ] **Windows:** the `.msi` runs without an administrator prompt; SmartScreen's "More info › Run anyway" path works as
      `install.md` says; Mnemo is in the Start menu and on the desktop (if the installer makes a shortcut), with the
      Mnemo icon, and is installed under `%LOCALAPPDATA%\Programs\Mnemo`, **not** in `%LOCALAPPDATA%\Mnemo`.
- [ ] **macOS:** the `.dmg` mounts, dragging to Applications works, and the "cannot check for malware" dialog is opened
      past by the route `install.md` gives for this macOS version (right-click › Open up to 14; System Settings ›
      Privacy & Security › Open Anyway on 15+; `xattr -dr com.apple.quarantine` as the fallback). The Dock icon is Mnemo's.
- [ ] **Linux:** `sudo apt install ./mnemo_*.deb` (or `sudo dnf install ./mnemo-*.rpm`) pulls in nothing surprising, Mnemo is
      in the applications menu with its icon and starts. `Mnemo` from the portable `.tar.gz` starts from a folder
      whose name has a space. If a library from `install.md`'s list is missing on a minimal install, the failure must
      be the readable Skia/OpenGL one, and the list in `install.md` must name the package: fix the doc if it doesn't.
- [ ] The first window opens in under 5 s on the slowest machine (`desktop-checks.py startup <app image>` times the
      database creation; write down what you feel for the window). No console window stays open behind it (Windows).
- [ ] The version in Settings › About equals the release tag; the window title is "Mnemo"; Settings › About › Open-source
      licenses opens and lists the libraries (no empty list, no crash).
- [ ] The data folder is where `install.md` says, and nothing was written to the install folder while using the app.

### 1.2 File association

- [ ] With Mnemo **closed**, double-click an `.apkg` in the file manager: Mnemo starts and imports it. Same for `.colpkg`.
- [ ] With Mnemo **open** (idle, then mid-study, then minimised), double-click another `.apkg`: no second window, the
      running one comes to the front within a second or two and imports it. The second process exits by itself.
- [ ] Right-click an `.apkg` › Open with: Mnemo is offered (and is the default after install, unless something else
      claimed the type).

### 1.3 Update

Install the **previous** release (for the first release: a 0.9.0 build made by the workflow with `mnemo.versionName` lowered
on a scratch branch) and use it so there is data: a deck, studied cards, a media card, a provider with a key, a window
size you changed. Then install the new version **over** it, per `install.md`.

- [ ] Windows: the `.msi` replaces the old one (one entry in Installed apps, not two).
- [ ] macOS: replacing the app in Applications works while Mnemo is quit.
- [ ] Linux: the package upgrades in place.
- [ ] The app opens straight into the collection, **without onboarding**. The database migrated (no error on the first
      launch; `desktop-checks.py counts` shows integrity `ok` and the same counts as before), the provider's key still
      works (Test connection), and the window comes back at the size and place you left it.
- [ ] If the new release raised the Room schema version: a collection copied from the old version's folder before the
      update also opens (this is the once-only check that `MigrationTest` cannot make).

### 1.4 Uninstall

- [ ] Windows › Installed apps › Mnemo › Uninstall; macOS › drag to Trash; Linux › `apt remove mnemo` / `dnf remove mnemo`.
      The program, its menu entry and its file association are gone (double-clicking an `.apkg` no longer opens Mnemo).
- [ ] The **collection is still there** (and so is the `secrets` folder), and installing again opens it as it was.
- [ ] Windows: nothing is left in `%LOCALAPPDATA%\Programs\Mnemo`, and the uninstall did not delete `%LOCALAPPDATA%\Mnemo`.

## 2. The collection

### 2.1 First run

- [ ] An empty collection shows onboarding. Go through the pages (the reminder page is **not** there), name a first deck,
      and it opens the editor. Quit and start again: no onboarding, the deck is there.
- [ ] Onboarding's Import option opens the system file dialog (a native one on each OS), and cancelling it changes nothing.

### 2.2 Import a large collection

Use **your own** collection if you have one (10,000+ cards, images, audio, math). Otherwise build one written by real Anki:

```bash
python3 -m venv /tmp/ankienv && /tmp/ankienv/bin/pip install anki
/tmp/ankienv/bin/python scripts/qa/make-large-apkg.py large.apkg --cards 12000     # prints the counts to expect
```

- [ ] Import it with File › Import… (or ⌘/Ctrl+O). Progress shows, the window stays responsive (you can move it, switch
      tabs) and it finishes without error. **Time it** with a stopwatch from the dialog's OK to "done". Target: under 60 s
      for 10,000 cards on a mid-range laptop; slower is a P2, a frozen window is a P1. (For an automated number run
      `desktop-checks.py import-time <app image> large.apkg`: on an Apple Silicon laptop the 12,000-card file above
      took about 4 s, see the dry run at the end.)
- [ ] `desktop-checks.py counts` (app closed, or it reads a copy) matches: decks, notes, cards, review logs, media. For a
      real collection compare with Anki's Browse count and Stats; dates and intervals of a few cards must match Anki.
- [ ] Decks shows the tree (`Parent::Child` nested), scrolling a list of 100+ decks is smooth, Browse searches the big
      collection in well under a second, and the memory of the Mnemo process after the import is sane (Task Manager /
      Activity Monitor / `ps`: write it down, worry over 1.5 GB).
- [ ] Importing the **same file again** does not double the cards (duplicates are recognised by Anki's note guid and skipped, ADR 0003).
- [ ] Quit mid-import (File › Quit) on a second import, start again: the collection is intact (`counts` says integrity
      `ok`) and the import can be repeated.

### 2.3 Study

- [ ] Study **100 cards** of the big collection with the keyboard (Space, 1–4) and 20 with the mouse, undo three times
      (⌘/Ctrl+Z). No stutter between cards, no blank flash, and the next-interval labels change with the rating.
- [ ] Every card type: Basic, Basic + Reversed, Cloze with several numbers, Type-in (right answer, wrong answer, **typing a
      space and the letter `e` into the answer field**: the card must not flip or open the editor), Multiple choice
      (pick by `1`–`4` and by clicking), a card with a hint.
- [ ] **Math:** inline and display formulas render (fractions, roots, sums, a matrix) in light and dark; TeX JLaTeXMath
      can't draw shows as raw source, not as an error or a blank.
- [ ] **Images:** a PNG, a JPEG, a large photo and a missing image (the card still shows, with the placeholder).
- [ ] **Audio:** a card with `[sound:x.mp3]`, `.wav` and `.ogg` plays (listen); "Play card audio automatically" on and off
      behaves; a `.m4a` or `.flac` link shows the message saying the format is not played, instead of silence; switching
      cards stops the previous sound; leaving the study screen stops it. Nothing in the UI offers text to speech.
- [ ] Undo, edit (`E`), and flag or suspend (if present in the card menu) behave as on the phone; right-click the card for the
      same actions.

### 2.4 Create, browse, analytics

- [ ] Create one note of **each** kind (Basic, Reversed, Cloze, Type-in, Multiple choice) with an image picked through
      the native file dialog and a sound attached. Ctrl/⌘+Enter adds the card and puts the cursor back in the first field.
- [ ] Browse: search by text, tag and deck; open, edit and delete a note; the right-click menu has the overflow
      button's entries.
- [ ] Analytics and the Decks retention tiles show plausible numbers on the big collection; first content in under 1 s;
      scrolling is smooth. **Optimize** in Settings › Scheduling runs to the end (it applies only if it lowers the loss; either
      result is fine, a crash or a spinner that never stops isn't).
- [ ] Exams: set an exam date on a deck, the countdown shows.

### 2.5 Export

- [ ] File › Export as .apkg… for one deck: the file opens in **Anki desktop** with notes, media and scheduling intact, and
      importing it into a fresh Mnemo collection reproduces the counts. JSON export opens in an editor and is valid JSON.

## 3. AI providers and Smart Extract

Use your own keys. **Never** paste a key into an issue, a log, a screenshot or this file.

- [ ] **Hosted provider** (OpenAI, OpenRouter, Groq, …): Settings › AI providers › add › **Test connection** lists the
      models and capabilities. A bad key gives the 401 message, a made-up model a readable error; nothing crashes.
- [ ] **Local provider** (Ollama or LM Studio on this machine, and once on another machine on the LAN): base URL
      `http://localhost:11434/v1` (or the LAN address), marked local. Test connection works over plain HTTP. Unmark
      "local": HTTP is refused. Stop the server: the error is readable.
- [ ] The disclosure dialog appears before the first request to each provider.
- [ ] Settings › AI providers says where the key behind your API keys lives: **keychain** on the machines that have one
      (Windows Credential Manager, macOS Keychain, Secret Service on Linux: look for an entry named Mnemo in it), and
      on the **no-keychain Linux machine** the file warning, with `secrets.key` in the data folder readable by your
      account only (`ls -l`: `-rw-------`). Restart: the saved key still works in both cases.
- [ ] **Smart Extract, 1,000 words in under 30 s:** paste a plain 1,000-word article (`wc -w`), Generate, stopwatch; cards
      should appear long before the last one. Accept them. Record the model, hosted and local, and write down a slow
      local model rather than hiding it (as in the Android runbook, §2).
- [ ] Also once each: a **PDF** through the file dialog, a **link**, and **dropping** a `.pdf`, a `.txt` and a `.md` on
      the Smart Extract screen. The Dictation source is **not** offered. Explain / Example / Rewrite while studying, and
      Co-Author chat and "Suggest missing cards".
- [ ] Every AI output has a **Report** button that asks first and then opens the prefilled GitHub issue in the default browser
      (check the issue text: output, feature, model, no key).
- [ ] To repeat these without spending tokens: `desktop-checks.py mock-ai` serves canned replies on `127.0.0.1:11435` (this checks
      the plumbing, not a real provider).

## 4. Backup, restore and moving a collection

- [ ] File › Back up… (or Settings › Data) writes a `.zip` to a folder you choose. Progress shows; the file opens as a zip
      and holds no `secrets`, no API key text (`grep -ri "sk-"` on the unpacked file finds nothing).
- [ ] File › Restore from backup… with that file on a **different** collection: it asks first, the app restarts itself, and
      the old collection's data is replaced by the backup's (`counts`). Providers come back **without keys**.
- [ ] Automatic backup: choose a folder in Settings › Data, turn it on, and change the clock or wait past a day (or quit and
      start after a day): a dated backup appears, and once there are more than 7 the oldest go ("the last 7 are kept").
- [ ] **Android → desktop → Android**, with real devices:
      1. On the phone: Settings › Data › Back up, save the file, copy it to the desktop.
      2. On the desktop, in a scratch collection (`MNEMO_DATA_DIR`): Restore it. Decks, cards, review history, media, the
         settings and the FSRS weights match the phone (`counts` against the phone's Decks and Browse); study ten cards on
         the desktop.
      3. On the desktop: Back up, copy the file to the phone, Restore it there. The phone has the ten reviews and opens
         normally (R8 build).
      Also once the other way from a desktop-first collection.
- [ ] **One deck:** export `.apkg` on the phone, import it on the desktop (it **adds**), and back again.

## 5. The desktop experience (D7)

### 5.1 Keyboard only

Without touching the mouse or trackpad, on each OS: import a deck (`⌘/Ctrl+O`), study 50 cards (Space, `1`–`4`,
`⌘/Ctrl+Z`), add 10 notes (`⌘/Ctrl+N`, `⌘/Ctrl+Enter`), and visit every page (`⌘/Ctrl+1`–`4`, `⌘/Ctrl+,`, `Esc`).

- [ ] Nothing in that session needed the mouse, and focus was never lost to a place you couldn't see.
- [ ] `?` opens the shortcuts sheet, it matches what actually works, and it shows ⌘ on macOS and Ctrl elsewhere.
- [ ] Typing the plain-character shortcuts (`e`, `1`, `?`, Space) into **any** text field (search, editor, type-in, AI box,
      Co-Author chat, deck name) types the character and does nothing else.
- [ ] Non-US layouts: one session with a German or French layout (Ctrl+, and `?` are the ones that move). Note what breaks.
- [ ] With an input method (Japanese or Chinese IME) on: composing text in the editor works and Space/Enter don't reveal
      the card while composing.

### 5.2 Menu bar

- [ ] **Windows and Linux:** File (Import…, Export as .apkg or JSON…, Back up…, Restore from backup…, Quit), Edit (New note,
      Find cards, Settings…), View (the four tabs), Help (Keyboard shortcuts, Open-source licenses, Report an issue,
      Privacy policy, About). Every item works, and its accelerator shown in the menu works too (the ones that are
      only proved on macOS today).
- [ ] **macOS:** the application menu has About Mnemo, Settings… (⌘,), and Quit (⌘Q); Help opens the links in the
      browser; ⌘Q mid-import and mid-study doesn't lose the last answer (check the history).
- [ ] Menus hold no Undo / Cut / Copy / Paste of their own, and ⌘/Ctrl+Z, X, C, V work in every text field.

### 5.3 Mouse, drag and drop

- [ ] Tooltips on icon buttons name the shortcut; the hand cursor on buttons, chips, rail items and rows; scrollbars on
      every long page, draggable, and they don't cover content.
- [ ] Right-click menus on a deck card, a Browse row and the study card.
- [ ] Drag from the real file manager onto the window: an `.apkg` and a `.colpkg` import (several at once, in turn), a
      `.zip` **asks** before restoring, other files do nothing harmful. A `.pdf` dropped on Smart Extract becomes its source.
      The drop target highlights while hovering. (Wayland: drops from some file managers are not delivered; write
      down which combination fails.)

### 5.4 Window

- [ ] The minimum size is enforced (720 × 520) and the layout is usable at it; the navigation rail shows from 600 dp;
      Decks, Browse and Licenses stop growing on a very wide window (a 4K or ultra-wide monitor).
- [ ] Resize, move and maximise, quit, start: size, position and maximised state come back. Quit on a second monitor,
      unplug it, start: the window appears on the remaining screen, title bar reachable.
- [ ] `window.properties` is in the data folder and **not** in a backup.
- [ ] Full-screen / maximise works with the OS's own control; the title bar follows the OS light or dark on macOS.

### 5.5 Second instance and crashes

- [ ] Start Mnemo while it is running: the message "Mnemo is already open" appears (not a crash, not a second window) and
      the second process exits with status 1 (from a terminal). Starting it with a file instead hands the file over (§1.2).
- [ ] Kill the running process without warning (`kill -9`, End task), then start again: it opens the collection (the lock is
      released by the OS). `counts` says integrity `ok`.
- [ ] Two **different** users on one computer each get their own collection and their own keychain entry.

## 6. Network off

Turn Wi-Fi and Ethernet off (or block the app in the firewall). Then a **cold** start.

- [ ] Study, create, edit, browse, search, Analytics, import/export, backup and restore, Settings, licenses and privacy
      screens all work. Math cards render (no network fonts).
- [ ] AI surfaces (Smart Extract, Explain/Example/Rewrite, Co-Author, Test connection, link source) fail with a
      readable message and a retry, never a crash or an endless spinner. A **local** provider on this machine still works.
- [ ] **The app opens no connection of its own** (sync off, which is how it starts: [../sync/qa.md](../sync/qa.md) §9 covers it on). With the app idle and after a study session, list its connections and
      expect none (macOS `lsof -i -a -p $(pgrep -x Mnemo)`; Linux `ss -tp | grep -i mnemo`; Windows `netstat -ano` with
      the PID from Task Manager). The only traffic is to a provider you configured, the link you pasted, or the browser the
      Report button opens. There is no update check and no telemetry (`install.md` says so).

## 7. Display, theme and accessibility

- [ ] **Scaling 100 %, 150 % and 200 %** (Windows: Settings › System › Display on a real display; macOS: a scaled
      resolution; Linux: GNOME's scale, and fractional scaling on Wayland). On each: text is sharp (no blurry upscaling),
      the study screen, editor, Decks, Analytics and Settings show every control with nothing clipped, math and
      images are the right size, the window's minimum size still fits the screen, and dragging the window between a 100 %
      and a 200 % monitor redraws correctly.
- [ ] Settings › **Card text size** at Large on top of 200 %: nothing unreachable.
- [ ] **Dark mode:** switch the OS between light and dark while the app runs: it follows. Then set the theme manually in
      Settings: it wins over the OS. Decks, Study, Analytics, the dialogs and the math cards read well in both.
- [ ] The text fields show a caret, selection, and the OS's right-click paste works; copy from a card works.
- [ ] A screen reader is **not** a v1 goal for the desktop (Compose's desktop accessibility is partial): open Narrator,
      VoiceOver or Orca once and record what is announced, for the record only.

## 8. Robustness

- [ ] **Non-ASCII and spaces in paths:** on the Windows machine with `José Núñez` as the user, the app starts, the database
      is created under `%LOCALAPPDATA%\Mnemo` (SQLite's native library and the OS keychain both have a history with
      this), and import, backup and restore work. Also `MNEMO_DATA_DIR` set to a folder with a space and an accent.
- [ ] A data folder that is **read-only** or full: the app says so instead of crashing with a stack trace (P2 if it just
      fails to start with a readable message, P1 if it loses data).
- [ ] A very long deck name, a card with 50 KB of text, an emoji and a right-to-left note (Arabic or Hebrew): nothing breaks.
- [ ] Leave Mnemo open overnight on the big collection: memory doesn't grow without bound, CPU is ~0 % when idle.
- [ ] Put the laptop to sleep mid-study and wake it: the session continues and the next answer saves.
- [ ] The app's files stay inside the data folder, the OS temp folder and the install folder; uninstall leaves nothing
      else (look for stray folders in the home directory).

## 9. Release hygiene

- [ ] The draft release has: the `.msi`, both `.dmg`s (Apple Silicon and Intel), the `.deb`, the `.rpm`, the `.tar.gz`, the APK, `SHA256SUMS.txt`, a copy of each under a name without the version, and notes (from
      `docs/release/release-notes.md`) that match the **known issues found in this pass**: update the file, don't edit only the draft.
- [ ] The `LICENSE` and `NOTICE` files are in each app's `resources` folder, and every library the app ships is either
      in `NOTICE` or in Settings › About › Open-source licenses.
- [ ] `docs/desktop/install.md` is true: file names, libraries, first-run steps (walk it on a clean machine), data
      locations, update and uninstall steps. Fix the doc or the app, never leave them disagreeing.
- [ ] `README.md` no longer says "in progress", links the release and `install.md`; `CLAUDE.md` and ARCHITECTURE's desktop
      lines say v1.0 is out. The F-Droid recipe is unchanged (it builds `:app` only).

## 10. Triage

- [ ] Every failure above is a GitHub issue labelled `P0`–`P3` and `desktop`, naming the OS and the installer. P0: data
      loss or corruption, a crash at launch or in the study loop, an installer that does not install, or red release CI;
      P1: a core flow broken on one OS (import, study, backup, restore, AI test connection) or a licence problem; P2:
      wrong but recoverable; P3: polish.
- [ ] **No open P0 or P1.** P2 and P3 are listed under "Good to know" in `docs/release/release-notes.md` or deferred.
- [ ] Only then: publish the draft release (owner), and update the README.

## Release day (owner)

1. Confirm `mnemo.versionName` is what you are tagging (`gradle.properties`, `MAJOR.MINOR.PATCH`) and `release/1.0` is the branch
   the tag sits on; push the tag: `git tag v<version> && git push origin v<version>`.
2. The workflow builds all installers and makes the draft. Download them from the draft, run `verify-sums` on them,
   and repeat §1 on at least one machine per OS with **those** files (the ones users get).
3. Press Publish on the draft. Nothing in the workflow publishes by itself.
4. One workflow makes the whole release (the APK too), with notes from `docs/release/release-notes.md` and the
   version's `fastlane/.../changelogs/<versionCode>.txt`; read the notes in the draft before publishing.

---

## Results log

One row per check per machine. **Result:** ✅ pass, ❌ fail (link the issue), ➖ not applicable. Build = the
installer's file name and the commit or tag.

| Check | Machine / OS | Build | Result | Notes |
|---|---|---|---|---|
| 1.1 Install and first run, Windows | | | | |
| 1.1 Install and first run, macOS | | | | |
| 1.1 Install and first run, Linux `.deb` | | | | |
| 1.1 Install and first run, Linux `.rpm` | | | | |
| 1.1 Install and first run, Linux portable | | | | |
| 1.2 File association, Windows | | | | |
| 1.2 File association, macOS | | | | |
| 1.2 File association, Linux | | | | |
| 1.3 Update over the previous version, Windows | | | | |
| 1.3 Update over the previous version, macOS | | | | |
| 1.3 Update over the previous version, Linux | | | | |
| 1.4 Uninstall keeps the collection, Windows | | | | |
| 1.4 Uninstall keeps the collection, macOS | | | | |
| 1.4 Uninstall keeps the collection, Linux | | | | |
| 2.1 Onboarding | | | | |
| 2.2 Import 10,000+ cards (time: ) | | | | |
| 2.3 Study 100 cards | | | | |
| 2.3 Math, image and audio cards | | | | |
| 2.4 Create all note types, Analytics, Optimize | | | | |
| 2.5 Export opens in Anki | | | | |
| 3 Test connection, hosted | | | | |
| 3 Test connection, local | | | | |
| 3 Key storage: keychain | | | | |
| 3 Key storage: key file fallback | | | | |
| 3 Smart Extract 1,000 words < 30 s | | | | |
| 3 PDF, link, drop | | | | |
| 4 Back up and restore | | | | |
| 4 Android → desktop → Android | | | | |
| 5.1 Keyboard-only session | | | | |
| 5.2 Menu bar and accelerators | | | | |
| 5.3 Drag and drop, right-click menus | | | | |
| 5.4 Window size and place | | | | |
| 5.5 Second instance, killed process | | | | |
| 6 Network off | | | | |
| 6 No connection of its own | | | | |
| 7 Scaling 100 / 150 / 200 % | | | | |
| 7 Dark mode | | | | |
| 8 Non-ASCII user name | | | | |
| 8 Overnight, sleep | | | | |
| 9 Release hygiene | | | | |
| 10 No open P0/P1 | | | | |

## Dry run (automated, before the manual pass)

What the scripts could already show, on the author's machine, so the manual pass starts from known ground. It is
**not** a substitute for any row above: one system, the app image instead of the installer, no eyes on the window.

| What | Machine | Result |
|---|---|---|
| `scripts/desktop/smoke-test-app.py` with `--open-file` | macOS 26 arm64, app image from `:desktop:createDistributable` | ok: starts, creates its database, keeps running, takes a file from a second launch |
| `desktop-checks.py startup` (3 runs) | same | database created after 0.4–1.5 s (the first run is slowest) |
| `desktop-checks.py import-time` with `make-large-apkg.py --cards 12000` | same | 12,000 cards in about 3.5 s from launch; `counts` after: 15 decks, 8,000 notes, 12,000 cards, 10,031 review logs, 17 media files, integrity `ok`: the same numbers the generator printed |
| App image size | same | 233 MB |
