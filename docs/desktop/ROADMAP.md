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

Convention: an Android implementation lives in a package named `android` (`AndroidXxx.kt`) in the
module that owns the interface; D4–D6 turn those directories into `androidMain`. The list of seams
and their implementations is ARCHITECTURE §4.3.

- **Data**
  - [x] `DocumentAccess` (`:core:common`) replaces direct `ContentResolver`, `DocumentFile` and
        `OpenableColumns` use in `FileMediaRepository`, `SourceRepository`, the transfer workers
        and `BackupManager`. Beyond the roadmap's "open a stream, name, MIME type" it also covers
        what automatic backups and the auto-backup setting need: create and list files in a picked
        folder, delete, keep and release access. Every failure is an `IOException`
        (`AndroidDocumentAccess` turns a revoked grant into one), so callers no longer catch
        `SecurityException`. `MediaRef.extensionFor` (the reverse of `mimeTypeFor`) replaced
        `MimeTypeMap`.
  - [x] `AppDirectories` (`:core:common`: files, cache, media, secrets, restore staging, database
        file, DataStore file) replaces `Context` paths in `DatabaseSnapshot`, `PendingRestore`,
        `BackupManager`, `FileSecretStore`, `FileMediaRepository` and the DataStore module.
        `MnemoApplication` builds an `AndroidAppDirectories` for `PendingRestore` before Koin
        starts; the graph binds both seams in `androidPlatformModule`.
  - [x] Worker bodies moved into plain jobs (`ImportJob`, `ExportJob`, `BackupJob` in
        `:core:data/job`) that report progress through a callback and throw on failure. The
        `CoroutineWorker`s only adapt them (input data, foreground notification, retry, error
        mapping through `toTransferError`). The media-cleanup and optimizer bodies were already
        plain (`MediaRepository.collectGarbage`, `FsrsOptimization.run`), so they got no class of
        their own. Automatic backups and export cleanup are now unit-tested against
        `FakeDocumentAccess`, which they weren't before.
  - [x] `PdfTextExtractor` is an interface; `PdfBoxAndroidTextExtractor` is its Android
        implementation.
  - [x] `SpeechTranscriber` is an interface with `isAvailable()`; the Dictation source is hidden
        when the device has no recognizer (`SmartExtractUiState.dictationAvailable`) or the
        platform has no dictation capability.
- **UI**
  - [x] `PlatformCapabilities` (`:core:designsystem`) replaces the `Build.VERSION` checks in
        `Theme.kt` and Settings. Provided by `MainActivity` around the theme; unprovided, every
        capability is on, which is what the phone app has, so screens and tests need no setup.
        Settings hides the reminder section without `reminders`, Smart Extract hides Dictation, and
        the study screen's read-aloud button needs `textToSpeech` or a sound on the card. The
        dynamic-color row still shows its "Needs Android 12 or newer" text when the capability is
        off: D6/D7 decide what the desktop shows.
  - [x] File dialogs: `rememberFilePicker`, `rememberMediaPicker`, `rememberFileSaver` and
        `rememberFolderPicker` (`:core:ui/files`) replace the activity-result launchers in
        `NoteEditorScreen`, `SmartExtractScreen`, `DataSection`, `DecksScreen` and onboarding. The
        roadmap named two functions; the folder picker and the gallery-style media picker are the
        other two call sites. They report a location string and nothing on cancel.
  - [x] Permission requests (notifications, microphone) go through
        `rememberPermissionRequest(AppPermission)` (`:core:ui/permission`), whose Android part is in
        `permission/android`. It reports `isGranted` and `canRequest`; a platform without
        `runtimePermissions` grants everything and asks nothing. Behavior is unchanged on Android,
        including the reminder's "notifications are blocked" hint.
  - [x] `ReportAiDialog` opens the issue URL through `LocalUriHandler`; the version name comes
        from `LocalAppVersion` (also used by About).
  - [x] `DeckCard` formats relative dates with `RelativeAge` (tested, same English wording as
        `DateUtils`' abbreviated form, so the baselines did not change); `ReminderTimeDialog`
        asks `rememberIs24HourFormat()`.
  - [x] Card rendering: `MathText` calls a `MathRenderer` (`KatexMathRenderer` on Android; raw TeX
        when unprovided), `MediaImage` a `MediaImageLoader` (`AndroidMediaImageLoader`),
        `AndroidCardAudio` moved out of the shared file. `MainActivity` provides all three.
- [x] `scripts/desktop/check-android-imports.py` fails on `android.*`, `androidx.work`,
      `androidx.activity`, `androidx.webkit`, `androidx.documentfile`, `androidx.core`,
      `LocalContext` and `AndroidView` imports outside `android` directories and its `PENDING`
      list; the `android-imports` job in `ci.yml` runs it. `PENDING` holds what D4–D6 still have to
      move: Room and its snapshot, the Keystore cipher, WorkManager and its notifications,
      the Koin bindings that name them, the licenses screen's raw resource, and the launcher
      (`MainActivity`, `MnemoApplication`, the widget), which stay in `:app`. A listed file that no
      longer has an Android import fails, so the list can only shrink.

**Exit:** the import check passes; the Android exit check and screenshots are green. Met:
`assembleDebug testDebugUnitTest lint`, the JVM module tests and `verifyRoborazziDebug` pass, with
no baseline changed. Not run: the instrumented tests and a manual pass on a device (file pickers,
the notification prompts, dictation, KaTeX and images on cards, an automatic backup into a real
folder), which the seams touch. **(owner: worth a smoke test before the next release build.)**

**Findings**

- Theme's dynamic color call moved into `platform/android/AndroidDynamicColor.kt` with a
  `@SuppressLint("NewApi")`: lint can't see that the capability is `Build.VERSION >= S`.
- A KDoc line that contains a MIME wildcard such as `image/*` opens a nested comment in Kotlin and
  fails the build with "Unclosed comment".
- `rememberAndroidPermissionRequest`'s `launch()` on Android 12 and older (no notification
  permission) reports `isGranted` at once instead of asking, which is what the onboarding and
  reminder code did by hand.

## D4 — Data layer multiplatform

**Goal:** the whole data and domain layer runs on desktop, against the same database format.

- **Room (the riskiest change, because it touches users' data)**
  - [x] First, on Android only: migrations take a `SQLiteConnection` (`execSQL` through the
        driver API), the seed callback uses `onCreate(connection)`, browse queries are
        `RoomRawQuery`, `TransactionRunner` uses `useWriterConnection { immediateTransaction { … } }`,
        `DatabaseSnapshot` reads the version and the file through PRAGMAs, and the database got
        `@ConstructedBy` when the module moved. Android runs on `AndroidSQLiteDriver` (D0).
        `MigrationTest` passes for every version (1 → 4), and the exported `4.json` did not change.
  - [x] Then the desktop target: `BundledSQLiteDriver`, `MnemoDatabase.build(file)`, the database
        in the D0 data directory, and the same `MigrationTest` against `core/database/schemas/`
        on desktop (`MigrationTestBase` has an Android and a desktop actual).
  - [x] Fixture databases for versions 1–4 (`core/database/src/commonTest/resources/fixtures`,
        made by the Android build, with data) migrate on both targets (`FixtureDatabasesTest`).
  - [x] On a device: the new debug and R8 release builds were installed over a collection made
        by the previous build (7 decks, 301 cards, 1,503 reviews, an AI provider) on an API 35
        emulator: it opened unchanged, a card could be answered and the review was saved.
- **Modules, in dependency order**
  - [x] `:core:common`: stays a JVM module (used from `commonMain` as is); its Hilt modules went
        in D2.
  - [x] `:core:datastore`: KMP; `PreferenceDataStoreFactory` with a path from `AppDirectories`.
  - [x] `:core:database`, as above.
  - [x] `:core:security`: `DesktopSecretCipher` with the AES key in the OS keychain
        (`KeyringKeyStorage`, java-keyring) or an owner-only key file (`KeyFileStorage`);
        `SecretStore` and the ciphertext format are unchanged.
  - [x] `:core:ingest`: Apache PDFBox 3 on desktop (`PdfBoxTextExtractor`); link fetching
        (OkHttp + jsoup) is shared; dictation is Android-only (`NoSpeechTranscriber`).
  - [x] `:core:domain`: a build change only.
  - [x] `:core:data`: `TransferQueue`s in an application scope behind `DataTransferRepository`
        and `FsrsOptimizationRepository`; the automatic backup and the media cleanup are checked
        at start and hourly; `ReminderRepository` keeps the setting and schedules nothing;
        `restartToRestore()` relaunches the desktop app (`ProcessAppRestarter`). The Android
        workers are in `androidMain`.
  - [x] `:core:testing`: fakes in `commonMain`; `PlatformTest` (Robolectric on Android only).
- **Desktop app behavior**
  - [x] `PendingRestore.applyIfPresent` runs in `openCollection` (called from `main()`) before the
        DI graph opens the database or DataStore, as it does on Android.
  - [x] Single instance: an OS file lock in the data directory (`SingleInstanceLock`). A second
        launch says Mnemo is already open instead of opening the database twice; a restart waits
        for the old process. Tested with a real second process.
- **Tests**
  - [x] Unit tests that don't need Robolectric moved to shared source sets and run on both targets
        (`commonTest`): database, datastore, security, ingest, data, domain.
  - [x] `DesktopCollectionTest` (`:desktop`): an empty data directory → import a real Anki package
        → study and undo through the use cases → back up → stage a restore, restart, restored →
        export → a second collection imports the export. A second test covers the automatic
        backup and the start-up maintenance. `DesktopGraphTest` resolves every definition of the
        desktop graph.
  - [x] Cross-device fixtures: a backup made by the Android build restores on desktop, and one
        made on desktop restores under Robolectric (`CrossDeviceBackupTest`).

**Exit:** `:desktop:test` and `desktopTest` run the data-layer suite; `MigrationTest` passes on
both targets; the Android exit check is green. Met on macOS arm64:
`assembleDebug testDebugUnitTest testAndroidHostTest desktopTest lint verifyRoborazziDebug`, the
JVM module tests and `:desktop:test`. Open: the first run of the desktop CI job on Windows and
Linux (native libraries, the keychain test skipping itself), the keychain on Windows and Linux,
and by hand on a phone: import an `.apkg`, a backup into a real folder, an AI "Test connection",
the widget and a reminder (the same checks as D2, now over the driver API).

**Findings:** see ADR 0010, "Findings from D4".

## D5 — Design system and shared UI

**Goal:** Mnemo's look, text and card rendering on desktop, with Android's screenshots unchanged.

- [x] `:core:designsystem` to Compose Multiplatform (`mnemo.kmp.library` + `mnemo.kmp.compose`). The
      fonts (Newsreader, Hanken Grotesk, JetBrains Mono) and their license texts are in
      `composeResources`. `Font(Res.font…)` is composable, so the families are built inside
      `MnemoTheme` and read through `MnemoTheme.fonts` (the public `JetBrainsMono` … vals are gone).
      Dynamic color stays Android-only (`expect platformDynamicColorScheme`); the desktop has
      `desktopPlatformCapabilities()` (everything off).
- [x] `MnemoIcons`: the JetBrains material-icons artifact 1.7.3, nothing vendored.
- [~] Strings: `:core:ui`'s 54 strings are in `composeResources/values/strings.xml`, with
      `stringResource(Res.string.x)`. **The features' strings (the rest of the 705) move with their
      module in D6**, because a module needs the Compose plugin, hence to be KMP, to have `Res`. Android
      `R.string` stays where Android reads text outside Compose.
- [x] `:core:ui`: KMP module. Routes were plain `@Serializable` types and the adaptive layout uses
      multiplatform `material3-adaptive`, so they moved as they were; the picker, permission and clock-format
      functions became `expect`/`actual` (`FilePickers`, `rememberPermissionRequest`,
      `rememberIs24HourFormat`); `AiSetupPrompt`, `AiDisclosureDialog`, `ReportAiButton` and
      `PermissionRationaleDialog` are in `commonMain`. The navigation host itself is D6 (nothing in
      `:core:ui` uses `navigation-compose`).
- [x] Card rendering on desktop: images through Skia with an LRU cache
      (`DesktopMediaImageLoader`); math through JLaTeXMath (`JLaTeXMathPainter`), laid out by
      `MarkdownText` (inline in the line, display on its own), raw TeX as the fallback; sound through
      `javax.sound` (`DesktopCardAudio`: wav, mp3, ogg; other formats are reported, not played). TTS is
      hidden by the capability flag. `ProvideDesktopPlatform` wires them up for the window and the tests.
- [x] Desktop file dialogs (AWT `FileDialog`, Swing folder chooser off macOS), `rememberIs24HourFormat`
      from the locale, permissions always granted.
- [x] Screenshots: the Android baselines are unchanged except the logo's antialiased edge (3 baselines
      re-recorded, see ADR 0010 "Findings from D5"); the catalog baseline moved to
      `src/androidHostTest/screenshots`. New desktop baselines: the component catalog (light, dark) in
      `:core:designsystem` and cards with math, an image and a sound link, in `:core:ui`
      (`src/desktopTest/screenshots`).
- [x] `:desktop` shows the sample cards with `MnemoTheme` and `CardFace` (a math card included).
- [ ] Not done, left for D6/D7: the desktop message when a sound has an unsupported format
      (`DesktopCardAudio` reports it through a callback), a check of the KaTeX WebView on a device, the
      desktop baselines on Windows/Linux (recorded on macOS arm64 only), hearing the audio.

**Exit:** the component catalog and sample cards render on desktop; `verifyRoborazziDebug` passes
on Android. Met on macOS arm64: `assembleDebug testDebugUnitTest testAndroidHostTest desktopTest lint`,
the JVM module tests, `:desktop:test`, `verifyRoborazziDebug`, `verifyRoborazziAndroidHostTest` and
`verifyRoborazziDesktop`; `assembleRelease` with the 16 KB check; a debug install on an emulator.
**Findings:** see ADR 0010, "Findings from D5".

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
