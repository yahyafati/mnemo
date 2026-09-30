# Mnemo — Desktop roadmap (Windows, macOS, Linux)

This roadmap brings Mnemo to the desktop **from the same codebase** as the Android app, with
[Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/). The Android-only
modules become Kotlin Multiplatform (KMP) modules with two targets, Android and desktop (JVM), and
a new `:desktop` module launches the app on a computer. The product roadmap is
[../ROADMAP.md](../ROADMAP.md); the Android release is [../release/ROADMAP.md](../release/ROADMAP.md).

**Decisions already made (2026-09-29):**

| Question | Decision |
|---|---|
| Separate desktop project or shared code | **Shared code, same repo.** The domain, data, scheduling, Anki, AI and UI code is written once. A second project would duplicate most of the ~30,000 lines of tested code and drift. |
| When | **Now, in parallel with the Android release** (release ROADMAP R3–R7). The guardrails below protect the release. |
| Platforms | **Linux, Windows and macOS** |
| Scope of desktop v1 | **Full parity**, except features that only make sense on a phone (see "Not on desktop" below) |
| Distribution | **GitHub Releases**: installers built by CI on tagged releases. Unsigned at first (see D8). |
| Sync | **Later.** Until then, collections move between devices by hand with backup/restore or `.apkg`/`.colpkg` export and import (see "Moving a collection between devices"). |

Owner-only tasks are marked **(owner)**. Everything else can be done in the repo. Efforts are
rough, agent-assisted working days.

---

## Where we start

Desktop Compose runs on the JVM, so every pure-JVM module already works on a computer unchanged.
Counts are main source lines (2026-09-29):

| State | Modules | Lines | Work |
|---|---|---|---|
| **Pure JVM, runs on desktop as is** | `:core:model`, `:core:scheduler`, `:core:ai`, `:core:anki`, `:core:common` (Hilt aside) | ~6,800 | None (drop the Hilt module in D2) |
| **No Android code, but an Android module** | `:core:domain` | ~1,000 | Build change only |
| **Mostly portable** | `:core:data`, `:core:database`, `:core:datastore`, `:core:security`, `:core:ingest`, `:core:testing` | ~6,800 | Android APIs behind seams (D3), Room driver API (D4) |
| **Compose UI** | `:core:designsystem`, `:core:ui`, six features, the shell in `:app` | ~15,000 | Compose Multiplatform, 705 strings to Compose resources, Hilt → shared DI |

What ties the portable code to Android today:

| Android dependency | Where | Desktop answer |
|---|---|---|
| **Hilt** (`@HiltViewModel`, `@HiltWorker`, `@ApplicationContext`, entry points) | every ViewModel, every DI module, app tests | Replace with a multiplatform DI library (D2) |
| **WorkManager** (import, export, backup, media cleanup, optimizer, reminder) | `:core:data/work`, `WorkManager*Repository` | Same repository interfaces; coroutine jobs on desktop (D4) |
| **`Context` / `Uri` / `ContentResolver` / `DocumentFile`** | media, sources, transfers, backups | A small file-access seam; plain paths on desktop (D3) |
| **Room on `SupportSQLiteDatabase`** | `MnemoDatabase` seed callback, the migrations (v1 → v4), `@RawQuery` browse, `withTransaction`, `DatabaseSnapshot` | Room's multiplatform driver API (D4) |
| **Android Keystore** | `KeystoreSecretCipher` (already behind `SecretCipher`) | OS keychain on desktop (D4) |
| **PdfBox-Android** | `PdfTextExtractor` | Apache PdfBox on desktop (D4) |
| **KaTeX in a `WebView`** | `MathText` | A desktop math renderer (D0 decision, D5) |
| **`BitmapFactory`, `MediaPlayer`, `TextToSpeech`** | `MediaImage`, `AndroidCardAudio` (already behind `CardAudio`) | Skia image decoding; a desktop audio player; no TTS at first (D5) |
| **`R.string` / `res/font`** | 773 resource references, 3 bundled fonts | Compose Multiplatform resources (D5) |
| **Activity result launchers, runtime permissions, `Build.VERSION`** | file pickers, dictation, notification permission, dynamic color | Shared picker API; platform capability flags (D3) |

**Not on desktop (v1):** the home-screen widget, the daily reminder notification, dictation
(`SpeechRecognizer`), text-to-speech, dynamic color, the splash screen and runtime-permission
screens. They stay in Android source sets, and the shared UI hides them through platform
capability flags instead of `Build.VERSION` checks. Some come back later (see "Later").

---

## Working rules

These apply to every step. They matter more than usual because the work runs alongside the
Android release.

1. **Android stays shippable.** Every step ends with the Android exit check green:
   `./gradlew assembleDebug testDebugUnitTest lint`, the JVM module tests, and
   `verifyRoborazziDebug`. A step that breaks Android isn't done, whatever the desktop does.
2. **Store builds come from a release branch.** Before the first invasive step (D2), cut
   `release/1.0` from `main`. Closed-test, production and F-Droid builds are tagged there; fixes
   land on `main` first and are cherry-picked. **(owner: confirm, and point the F-Droid recipe's
   tags at it.)**
3. **Refactor on Android first, then add the target.** D2 and D3 change Android code only, and the
   existing tests prove them. Moving a module to KMP afterwards should move files, not rewrite
   logic.
4. **Bottom-up, one module per commit.** Convert a module only once everything it depends on is
   converted. Small diffs keep reviews (human or agent) honest.
5. **One schema, one collection format.** Desktop uses the same Room schema, migrations, backup
   zip and export formats as Android. Never fork them: sync (Later) depends on it. The rules of
   CLAUDE.md "Data and scheduling" (UUIDs, timestamps, soft deletes, `Clock`, `StudyDay`) apply
   unchanged.
6. **FOSS only, GPL-3.0-or-later.** ADR 0009 applies to desktop too: no proprietary SDKs, every
   new bundled library goes into `NOTICE` and `app/config`, and the FOSS dependency check covers
   the desktop classpath.
7. **Docs move with the code.** At the end of each step, update `CLAUDE.md` (commands, module
   rules), `docs/ARCHITECTURE.md` and this file's checkboxes.

---

## Overview

| Step | Theme | Outcome | Rough effort |
|---|---|---|---|
| **D0** | Decisions and spike | ADR 0010; the riskiest build assumptions proved on a throwaway branch | 1–2 days |
| **D1** | Build and desktop shell | KMP convention plugins, a `:desktop` window, desktop CI job, release branch | 2–3 days |
| **D2** | Replace Hilt | Multiplatform DI on Android, no behavior change | 3–5 days |
| **D3** | Platform seams | Every Android API used by shared code sits behind an interface | 3–4 days |
| **D4** | Data layer multiplatform | Room, DataStore, secrets, ingest, domain and data run on desktop | 5–8 days |
| **D5** | Design system and shared UI | Theme, strings, fonts, icons, card rendering (math, images, audio) on desktop | 5–7 days |
| **D6** | Features and app shell | Every tab and Settings works on desktop | 5–8 days |
| **D7** | Desktop experience | Keyboard, menus, windows, drag and drop, mouse | 3–5 days |
| **D8** | Packaging and distribution | Installers for three OSes on GitHub Releases, built by CI | 2–4 days |
| **D9** | QA and desktop v1.0 | QA runbook passed on all three OSes, public release | 2–3 days |

Total: roughly **6–10 weeks** of focused work.

```
D0 ──► D1 ──► D2 ──► D3 ──► D4 ──► D5 ──► D6 ──┬──► D7 ──┐
                                               └──► D8 ──┴──► D9 ──► desktop v1.0
```

D2 and D3 are Android-only refactors and can overlap once D2's DI modules exist. D7 and D8 can run
in parallel. The critical path is D4 (Room) and D6 (features).

---

## D0 — Decisions and spike

**Goal:** settle the open technical choices and prove the build assumptions before any
production code moves.

- [x] [ADR 0010](../adr/0010-desktop-with-compose-multiplatform.md) decides each row below and
      records the versions. The table keeps the original recommendations; where the spike changed
      one, the ADR's "Findings" say so (Material3 is pinned to 1.9.0, the keychain is untested).

| Decision | Options | Recommendation |
|---|---|---|
| Code-sharing model | Full KMP modules; or pure-JVM modules plus copies of the Android ones | KMP modules with an Android target (AGP 9's `com.android.kotlin.multiplatform.library`) and `jvm("desktop")`. Pure-JVM modules stay JVM: both targets can use them. |
| Dependency injection | **Koin**; **Metro** (compile-time, Dagger-like) | Koin: the most documented choice for Compose Multiplatform, and it has WorkManager and ViewModel integrations. A `verify()` test in D2 catches missing bindings, the one thing Hilt checked at compile time. |
| Math on desktop | **JLaTeXMath** (pure Java, renders to an image); **KCEF/JCEF** (embedded Chromium running the same KaTeX) | JLaTeXMath: small and offline. KCEF downloads ~100 MB of Chromium and brings a browser engine along. Rendering differs a little from KaTeX, so unsupported TeX falls back to the raw source. |
| Audio on desktop (`[sound:…]`) | `javax.sound` plus MP3/Ogg decoder SPIs; JavaFX Media; VLCJ (needs VLC installed) | `javax.sound` with FOSS MP3/Ogg decoders: no install-time dependency. Unsupported formats show a message. |
| API keys on desktop | OS keychain (for example `java-keyring`: Windows Credential Manager, macOS Keychain, Linux Secret Service); a key file | Keychain for the AES key that `SecretStore` already uses. If there's no keychain (some Linux setups), fall back to a key file readable only by the user, and say so in Settings › AI providers. |
| File pickers | Own `expect`/`actual` API (SAF on Android, AWT `FileDialog` on desktop); FileKit (KMP library) | Own small API in `:core:ui/files`: four call sites, and one fewer dependency. |
| Background jobs on desktop | Coroutines in an application scope; a job library | Coroutines behind the existing repository interfaces (`DataTransferRepository`, `FsrsOptimizationRepository`). Jobs run while the app is open; auto-backup checks at start. |
| SQLite on Android | Keep the framework driver (`AndroidSQLiteDriver`); switch to `BundledSQLiteDriver` | Keep the framework driver on Android (no new native library, APK size or 16 KB page-size concerns); bundled driver on desktop. |
| Where data lives on desktop | — | Linux `$XDG_DATA_HOME/mnemo` (`~/.local/share/mnemo`), Windows `%LOCALAPPDATA%\Mnemo`, macOS `~/Library/Application Support/Mnemo`. Secrets in a sibling directory that backups never read, like `noBackupFilesDir`. |
| Version numbers | Shared with Android; separate | Shared: `mnemo.versionName` in `gradle.properties` is the desktop package version too. macOS needs a major version ≥ 1, which holds. |
| macOS architectures | Apple Silicon only; plus Intel | Apple Silicon first. Add Intel if CI can still build it cheaply. **(owner: confirm.)** |

- [x] **Spike** (throwaway branch `spike/desktop-d0`, not merged). Results and versions are in
      ADR 0010:
  - [x] A module using AGP 9's KMP library plugin with `jvm("desktop")` builds, and its shared
        source set can use `java.time` and depend on the pure-JVM `:core:model`. (Kotlin treats a
        source set shared only by JVM and Android targets as JVM code. If AGP 9's plugin gets in
        the way, the fallback is moving `:core:model` from `java.time` to `kotlinx-datetime`,
        which costs several days: add it to D4.)
  - [x] Robolectric host tests and Roborazzi still run in that module's Android target, with
        Android resources enabled.
  - [x] A Compose Multiplatform release that works with Kotlin 2.3.21, and the JetBrains
        artifacts for lifecycle/ViewModel, navigation-compose, material3, material3-adaptive and
        material icons. Note anything that lags the Compose BOM (2026.09.00).
  - [x] Room with `BundledSQLiteDriver` on desktop opens a copy of a real v4 Mnemo database made
        by the Android app.
  - [~] The natives of `sqlite-bundled`, `zstd-kmp-jvm` and Skia load on Linux x64, Windows x64
        and macOS arm64 (a CI matrix run). Proved on macOS arm64 and, in a container, Linux x64.
        Windows x64 is only checked statically: the CI matrix run moves to D1.

**Exit:** ADR 0010 is accepted with the spike's results, and the version list is ready for
`gradle/libs.versions.toml`. Done, with the open checks listed in the ADR's "Not yet proven"
(Windows and macOS Intel natives, keychain, audio playback, Koin navigation arguments).
**Owner still to confirm:** the `release/1.0` branch (D1) and macOS Apple Silicon only (D8).

## D1 — Build and desktop shell

**Goal:** a desktop window that builds in CI, using real Mnemo code, without touching Android
behavior.

- [~] Cut `release/1.0` from `main` (working rule 2). The branch exists locally at `ac09b4d`, the
      last commit before any D1 change; it is not pushed. **(owner: confirm, push it, and point the
      F-Droid recipe's tags at it.)**
- [x] Convention plugins in `build-logic`: `mnemo.kmp.library` (Android KMP library + `desktop`
      JVM target, toolchains, the Robolectric flags from the Android convention),
      `mnemo.kmp.compose` (Compose Multiplatform plugin, compiler, `compose_stability.conf`,
      resources), `mnemo.kmp.feature` (the KMP twin of `mnemo.android.feature`) and
      `mnemo.desktop.application`. The Android plugins stay until the last module has moved (D6).
      No production module applies the `kmp` plugins yet, so they were proved on a throwaway
      module (deleted): commonMain Compose code, a Robolectric Compose test on the Android target,
      a `runComposeUiTest` test on desktop, and the Roborazzi and lint tasks exist.
      `mnemo.kmp.feature` was only checked to configure, because its `:core:*` dependencies have no
      desktop variant until D4/D5. `mnemo.desktop.application` runs `jlink`/`jpackage` on a JDK 21
      toolchain (the daemon's JDK 25 has no `jmods`).
- [x] Versions from ADR 0010 in `gradle/libs.versions.toml` (use the catalog coordinates, not the
      deprecated `compose.runtime` accessors). Repositories stay in `settings.gradle.kts`.
- [x] `:desktop` module: `main()` opens a window that uses `:core:model` and `:core:scheduler`
      (for example, a sample card with its FSRS next intervals). This proves JVM-module reuse.
      Checked on macOS arm64: the packaged app image starts and stays running, and the screen
      rendered offscreen shows the two sample cards with their intervals.
- [x] Commands in `CLAUDE.md`: `./gradlew :desktop:run`, `:desktop:test`,
      `:desktop:createDistributable`. A KMP module has no `testDebugUnitTest` (ADR 0010), so the
      Android exit check also runs `testAndroidHostTest` and `desktopTest`, and screenshots use
      `verifyRoborazziAndroidHostTest` / `verifyRoborazziDesktop` next to `verifyRoborazziDebug`.
      CI does not run those names yet: Gradle fails a task name no module has, so they join the
      command when the first module converts.
- [~] Close the D0 gap: a CI matrix (Ubuntu, Windows, macOS) that runs the SQLite, zstd and Skia
      checks from the spike on each OS, Windows x64 first. `NativeLibrariesTest` in `:desktop`
      holds the checks, and the `desktop` job in `ci.yml` runs it with `:core:anki:test` on all
      three. It passes on macOS arm64; **the first run on GitHub (Windows and Linux) is still to
      come**, so this stays open until it is green.
- [~] CI: a `desktop` job on Ubuntu runs `:desktop:test` and `:desktop:createDistributable`. The
      installer matrix comes in D8. Written (the same `desktop` job; the app image and the FOSS
      check run on Linux only), not yet run on GitHub.
- [~] F-Droid keeps building: run the recipe's `prebuild` and `:app:assembleRelease` on the new
      tree, and check the scanner is fine with the desktop module and the Compose Multiplatform
      plugin. `check-foss-deps.py` also reads `:desktop:runtimeClasspath`. Done on a copy of the
      tree with the recipe's `rm` and `sed` applied and JDK 21: `:app:assembleRelease` succeeds and
      the desktop classpath (99 libraries) passes `check-foss-deps.py`, also in CI. `fdroid scanner`
      itself was not run (it needs fdroidserver): run it before the release tag.

**Exit:** `./gradlew :desktop:run` opens the window; the Android exit check and the F-Droid build
are unchanged. Met locally: `assembleDebug testDebugUnitTest lint`, the JVM module tests,
`:desktop:test` and `verifyRoborazziDebug` pass. Open: the owner's `release/1.0` push and the first
GitHub run of the `desktop` job.

## D2 — Replace Hilt

**Goal:** the DI library chosen in D0, on Android only, with no behavior change. This is the
largest Android-only diff, so it gets a step of its own.

- [x] DI modules mirror today's Hilt modules one for one: dispatchers, clock, database,
      DataStore, security (`SecretCipher`, `SecretStore`), ingest, data, work. Each Gradle module
      exposes a public `xxxModule`; `:core:data` adds `dataLayerModules` (so `:app` needn't see
      Room or DataStore) and `:app` lists everything in `mnemoModules`. Unscoped Hilt bindings are
      `factory`, `@Singleton` ones are `single`. There is also a `domainModule` for the use cases
      and one module per feature.
- [x] Every `@HiltViewModel` becomes a `viewModelOf` definition, and `hiltViewModel()` is
      `koinViewModel()`. `SavedStateHandle` keeps working (navigation arguments): the Browse test
      opens the editor for a note and gets "Edit note", which only happens if `noteId` arrives.
- [x] `@HiltWorker` workers are `workerOf` definitions (`workModule`), created by
      `KoinWorkerFactory`. `MnemoApplication` stays the WorkManager `Configuration.Provider`.
- [x] `MainActivity`, `MnemoApplication` and the widget drop Hilt. `MnemoApplication` calls
      `startKoin` after `PendingRestore.applyIfPresent`, the widget provider is a `KoinComponent`.
      The `javax.inject`, `@Inject`, `@Singleton` and `@Dispatcher` annotations are gone from
      every class.
- [x] Tests: `HiltTestRunner` is deleted (instrumented tests use `AndroidJUnitRunner`).
      `TestStorageModules` is now `testStorageModule` (in-memory database, per-test DataStore
      file, software cipher, test WorkManager) and `TestMnemoApplication` starts the real graph
      with it on top. It is set as the Robolectric application in `robolectric.properties`.
- [x] `DependencyGraphTest` resolves every definition with `checkModules` (test overrides
      included) and runs `verify()`, so a missing binding fails a unit test instead of crashing at
      runtime. Checked by removing a binding: both fail. `verify()` alone is not enough (ADR
      0010, finding 5; see the D2 findings for what each needs).
- [x] Hilt is gone from `build-logic` (`mnemo.hilt`), the root build and the version catalog. KSP
      stays for Room.
- [~] Release (R8) smoke test, on the Pixel 8 API 35 emulator over an existing collection: the
      R8 build installs as an update, the Decks, Study and Settings screens load with the old
      data, and `MediaCleanupWorker` and `ReminderWorker` run to `SUCCESS` through
      `KoinWorkerFactory`. **Still to do by hand (owner):** import an `.apkg`, an AI "Test
      connection", the widget on a launcher, a backup and a real reminder, on a device.

**Exit:** no `dagger` or `hilt` import left; the Android exit check and all tests are green; a
manual smoke test (study, import, AI test connection, widget, reminder) behaves as before.

## D3 — Platform seams

**Goal:** every Android API that shared code needs sits behind a small interface with its Android
implementation next to it. Android only; the existing tests prove it.

- **Data**
  - [ ] `DocumentAccess` (open a stream, display name, MIME type for a URI string) replaces direct
        `ContentResolver`, `DocumentFile` and `OpenableColumns` use in `FileMediaRepository`,
        `SourceRepository`, `TransferWorkers` and `BackupManager`.
  - [ ] `AppDirectories` (files, media, secrets, cache, database, DataStore file) replaces
        `Context` paths.
  - [ ] Worker bodies move into plain job classes (import, export, backup, media cleanup,
        optimize) that report progress through a callback. The `CoroutineWorker`s only adapt
        them, and transfer notifications stay in the Android adapter.
  - [ ] `PdfTextExtractor` becomes an interface; PdfBox-Android is its Android implementation.
  - [ ] `SpeechTranscriber` reports whether it's available, and the UI hides dictation when it
        isn't.
- **UI**
  - [ ] `PlatformCapabilities` (dynamic color, reminders, widget, dictation, TTS, runtime
        permissions), provided to the UI, replaces the `Build.VERSION` checks in `Theme.kt` and
        Settings.
  - [ ] File pickers: `rememberFilePicker` / `rememberFileSaver` in `:core:ui/files` replace the
        activity-result launchers in `NoteEditorScreen`, `SmartExtractScreen`, `DataSection`
        and `DecksScreen`.
  - [ ] Permission requests (notification, microphone) move into Android-only composables behind
        the capability flags.
  - [ ] `ReportAiContent` opens the issue URL through `LocalUriHandler`, not an `Intent`.
  - [ ] `DeckCard` formats relative dates with its own tested formatter, not
        `android.text.format.DateUtils`.
  - [ ] Card rendering: `MathText` behind a `MathRenderer` seam (Android keeps the KaTeX
        WebView); `MediaImage` decodes through a seam (`BitmapFactory` today, Skia on desktop).
        `CardAudio` is already an interface.
- [ ] `scripts/desktop/check-android-imports.py`: fails if `android.*`, `androidx.work`,
      `androidx.activity`, `androidx.webkit` or `androidx.documentfile` is imported outside the
      designated Android files. Runs in CI.

**Exit:** the import check passes; the Android exit check and screenshots are green.

## D4 — Data layer multiplatform

**Goal:** the whole data and domain layer runs on desktop, against the same database format.

- **Room (the riskiest change, because it touches users' data)**
  - [ ] First, on Android only: migrations take a `SQLiteConnection` (`execSQL` through the
        driver API), the seed callback uses `onCreate(connection)`, browse queries become
        `RoomRawQuery` instead of `SupportSQLiteQuery`, `TransactionRunner` and
        `DatabaseSnapshot` use `useWriterConnection { immediateTransaction { … } }` instead of
        `withTransaction` and `openHelper`, and the database gets `@ConstructedBy`. Android keeps
        the framework driver (D0). `MigrationTest` passes for every version (1 → 4).
  - [ ] Then add the desktop target: `BundledSQLiteDriver`, database file in the D0 data
        directory, and a desktop migration test against `core/database/schemas/`.
  - [ ] Fixture databases for every schema version, made by the Android app, migrate on both
        targets.
- **Modules, in dependency order**
  - [ ] `:core:common`: drop the Hilt modules (D2); nothing else.
  - [ ] `:core:datastore`: `PreferenceDataStoreFactory` with a path from `AppDirectories`.
  - [ ] `:core:database`, as above.
  - [ ] `:core:security`: desktop `SecretCipher` with the key in the OS keychain, or the key-file
        fallback (D0). `SecretStore` keeps its file format.
  - [ ] `:core:ingest`: desktop PDF text through Apache PdfBox; link fetching (OkHttp + jsoup) is
        shared; dictation is Android-only.
  - [ ] `:core:domain`: build change only.
  - [ ] `:core:data`: desktop job runner (application-scope coroutines, progress as `StateFlow`)
        behind `DataTransferRepository` and `FsrsOptimizationRepository`; auto-backup runs at
        start when due; `ReminderRepository` is a no-op on desktop. `restartToRestore()` relaunches
        the desktop app.
  - [ ] `:core:testing`: fakes move to shared code; the Robolectric runner stays Android-only.
- **Desktop app behavior**
  - [ ] `PendingRestore.applyIfPresent` runs in `main()` before the DI graph opens the database or
        DataStore, as it does on Android.
  - [ ] Single instance: a lock file in the data directory. A second launch says Mnemo is already
        open instead of opening the database twice.
- **Tests**
  - [ ] Unit tests that don't need Robolectric move to shared test source sets, so they run on
        both targets.
  - [ ] `DesktopCollectionTest`: an empty data directory → import a real Anki package from
        `core/anki/src/test/resources` → study cards through the use cases → back up → restore →
        export `.colpkg`.
  - [ ] Cross-device fixtures: a backup made by the Android build restores on desktop, and one
        made on desktop restores under Robolectric.

**Exit:** `:desktop:test` runs the data-layer suite; `MigrationTest` passes on both targets; the
Android exit check is green.

## D5 — Design system and shared UI

**Goal:** Mnemo's look, text and card rendering on desktop, with Android's screenshots unchanged.

- [ ] `:core:designsystem` to Compose Multiplatform. The fonts (Newsreader, Hanken Grotesk,
      JetBrains Mono) move to `composeResources/font`. `Font(Res.font…)` is composable, so the
      typography is built inside `MnemoTheme`. Dynamic color stays Android-only.
- [ ] `MnemoIcons`: use the JetBrains material-icons artifact 1.7.3. All 80 icons compile against
      it on both targets (D0), so nothing is vendored. `MnemoIcons` is already the only entry
      point.
- [ ] Strings: the 705 strings move to each module's `composeResources/values/strings.xml`
      (plurals and format arguments included), and `stringResource(R.string.x)` becomes
      `stringResource(Res.string.x)`. Android `R.string` stays only where Android reads text
      outside Compose (notifications, widget, reminder, app name).
- [ ] `:core:ui`: routes and navigation on the multiplatform navigation-compose;
      `LocalWindowLayout` from multiplatform material3-adaptive; `AiSetupPrompt`,
      `AiDisclosureDialog`, `ReportAiButton` and `PermissionRationaleDialog` in shared code.
- [ ] Card rendering on desktop: the Markdown renderer is already Compose and shared; images
      decode through Skia with an LRU cache; math through the D0 renderer, with raw TeX as the
      fallback; audio through the D0 player. TTS buttons are hidden (capability flag).
- [ ] Screenshots: the Android Roborazzi baselines stay unchanged, or change only with an
      explained diff. Add a desktop baseline of the component catalog (Roborazzi's desktop
      support) and of a card with math, an image and audio.

**Exit:** the component catalog and sample cards render on desktop; `verifyRoborazziDebug` passes
on Android.

## D6 — Features and app shell

**Goal:** every tab and Settings works on desktop.

- [ ] Features to `mnemo.kmp.feature`, smallest first: analytics → browse → decks → study →
      settings → create. ViewModels and screens are shared; Android-only UI (reminder settings,
      dictation, permission prompts, widget hints) sits in the Android source set or behind
      capability flags.
- [ ] A shared shell module (`:shell`) takes `MnemoApp`, `TopLevelDestination`, the nav host and
      onboarding from `:app`. It depends on the features, as `:app` does today; features still
      never depend on each other. `:app` and `:desktop` become thin launchers.
- [ ] Onboarding on desktop skips the notification-permission page.
- [ ] Licenses: AboutLibraries generates the desktop list too, and `LicensesScreen` takes the
      library JSON instead of an Android raw resource id. Desktop-only bundles (the JRE, Skia,
      the math and audio libraries) go into `NOTICE` and `app/config`.
- [ ] Tests: ViewModel tests move to shared test source sets. Android Compose UI tests stay on
      Robolectric. Desktop gets Compose UI tests (`runComposeUiTest`) for the first session and
      the study loop.
- [ ] Remove the Android-only convention plugins that nothing uses any more, and update
      `CLAUDE.md` and ARCHITECTURE §3–4 for the new module layout.

**Exit:** on desktop you can create a deck, add all five note types, study with undo, import an
`.apkg`, export, back up and restore, add an AI provider and test the connection (a local one
such as Ollama counts), run Smart Extract from paste, PDF and link, use Explain/Example/Rewrite
and Co-Author, and see Analytics and run the optimizer.

## D7 — Desktop experience

**Goal:** Mnemo feels like a desktop app, not a phone app in a window.

- [ ] **Window:** minimum size, remembered size, position and maximized state; app icon (extend
      `docs/release/assets/generate_icon_drawables.py` to write PNG, ICO and ICNS); the expanded
      (navigation-rail) layouts from the tablet work; a maximum content width for reading.
- [ ] **Keyboard:** study (Space/Enter reveal, 1–4 rate, Ctrl/⌘+Z undo, E edit); global
      (Ctrl/⌘+N new note, Ctrl/⌘+F search in Browse, Ctrl/⌘+, Settings, Ctrl/⌘+1–4 tabs);
      editor (Ctrl/⌘+Enter save, cloze shortcut). A shortcuts sheet on `?`. Focus order and Tab
      navigation work everywhere.
- [ ] **Menu bar:** File (Import…, Export…, Back up…, Restore…, Quit), Edit, View, Help (About,
      Licenses, Report an issue). On macOS, the app menu follows platform conventions
      (⌘Q, Settings…).
- [ ] **Mouse:** hover states, tooltips on icon buttons, right-click menus in Browse and on deck
      cards, visible scrollbars.
- [ ] **Drag and drop:** drop an `.apkg`, `.colpkg` or backup onto the window to import it; drop a
      PDF or text file onto Smart Extract.
- [ ] **Theme:** follow the OS light/dark setting where the platform reports it; the manual
      setting still wins.

**Exit:** a full session (import a deck, study 50 cards, add 10 notes) works without the mouse,
checked on each OS.

## D8 — Packaging and distribution

**Goal:** installers for all three OSes on GitHub Releases, built by CI from a tag.

- [ ] Compose Desktop `nativeDistributions`: Linux `.deb` and `.rpm` plus a portable `.tar.gz`;
      Windows `.msi` (per-user install, Start menu entry, an `upgradeUuid` fixed forever); macOS
      `.dmg` (architectures per D0). `packageVersion` comes from `mnemo.versionName`.
- [ ] A trimmed JRE (`modules(…)`: at least TLS/crypto for AI providers). No minification for
      desktop v1: the runtime dominates the size and ProGuard adds keep-rule risk. Revisit later.
- [ ] File associations: `.apkg` and `.colpkg` open in Mnemo.
- [ ] `.github/workflows/desktop-release.yml`: on a `v*` tag, a matrix (Ubuntu, Windows, macOS)
      builds the packages and attaches them with SHA-256 checksums to a draft GitHub Release.
- [ ] **Unsigned installers:** the release notes explain the first-run warnings: Windows
      SmartScreen ("More info → Run anyway") and macOS Gatekeeper (right-click → Open, or System
      Settings › Privacy & Security › Open Anyway). Signing is Later.
- [ ] `docs/desktop/install.md`: download, first run, where data lives, uninstalling, and moving
      a collection between phone and desktop. A README section links it.

**Exit:** a tagged build produces installers for all three OSes; each installs, launches,
upgrades over the previous version with the data kept, and uninstalls cleanly.

## D9 — QA and desktop v1.0

**Goal:** the release build passes a runbook on real machines.

- [ ] `docs/desktop/qa.md`: a runbook and results log in the style of `docs/release/qa.md`. Per
      OS: fresh install and onboarding; import a large real Anki collection (10,000+ cards) and
      time it; study 100 cards smoothly; math, image and audio cards; a hosted and a local AI
      provider; back up and restore; move a collection Android → desktop → Android; network off
      (everything but AI works); display scaling at 100/150/200 %; dark mode; upgrade install; a
      second instance.
- [ ] Fix every P0/P1 bug, then publish the release (no longer a draft) and update the README.

**Exit:** the runbook passes on Linux, Windows and macOS, and desktop v1.0 is public.

---

## Moving a collection between devices (until sync)

Desktop v1 has no sync, by decision. The manual paths use formats both platforms share (D4 tests
them):

| Want to move | Use | Effect on the other device |
|---|---|---|
| **Everything** (decks, notes, review history, settings, media) | Settings › Data › Back up, copy the file, then Restore | **Replaces** its whole collection |
| **One deck** | Export `.apkg`, then Import | **Adds** to its collection |

- Study on one device at a time, and move the backup before switching. Restoring overwrites
  reviews made on the other device since its last transfer.
- API keys never leave a device (ADR 0005): re-enter them after a restore.
- The reminder and widget are Android-only; their settings are ignored on desktop.

`docs/desktop/install.md` (D8) states this for users.

## Risks

| Risk | Mitigation |
|---|---|
| The refactor breaks the Android build during the closed test | Release branch (rule 2); Android exit check at every step; D2–D3 are Android-only refactors checked by the existing tests |
| The Room migration rewrite loses or corrupts data | Android-first switch (D4); `MigrationTest` on both targets; fixture databases for every schema version |
| AGP 9's KMP plugin can't do something the Android plugin does (Robolectric, resources, lint) | D0 spike; the fallback is keeping that module's Android part as an Android library next to a shared JVM module |
| Compose Multiplatform lags Jetpack Compose (adaptive, icons) | Versions pinned in D0; vendor icons; keep `MnemoIcons` the single entry point |
| Desktop math looks different from KaTeX | Sample cards in the desktop screenshot tests; raw TeX fallback |
| Desktop can't play some Anki audio formats | A clear message instead of silence; the format list is in `install.md` |
| Linux has no keychain | Key-file fallback, shown in Settings |
| Unsigned installers scare users | Warnings explained in the release notes and `install.md`; signing is Later |
| The F-Droid build breaks because of the desktop module | D1 check; the recipe builds `:app` only |
| Agent-sized refactors drift | One module per commit, the Android-import check, `CLAUDE.md` updated after every step |

## Later (after desktop v1.0)

- **Sync** between phone and desktop: file-based through user-owned storage, or an optional
  self-hostable server (../ROADMAP.md "Later"). The shared schema is what makes it possible.
- **Signed installers:** Apple Developer Program for notarization, a Windows code-signing
  certificate or the Microsoft Store. **(owner: cost and accounts.)**
- More channels: Flathub, Homebrew, winget.
- An opt-in update check against GitHub Releases (needs a privacy-policy update).
- Study reminders through desktop notifications or the system tray.
- Desktop TTS through the OS voices, and dictation through a local speech model.
- Intel Mac builds, if D0 leaves them out.
