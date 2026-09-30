# ADR 0010: Desktop with Compose Multiplatform

- **Status:** Accepted, with the open checks listed under "Not yet proven" (they are closed in D1 and D4)
- **Date:** 2026-09-29
- **Context for:** desktop ROADMAP D0–D9 (`docs/desktop/ROADMAP.md`); ARCHITECTURE §3–4; ADR 0002, 0004, 0005, 0009

## Decision

Mnemo for Windows, macOS and Linux is built from the Android codebase. The Android-only modules
become Kotlin Multiplatform (KMP) modules with two targets, Android and desktop (JVM), and a new
`:desktop` module launches the app. Each row of the D0 table was decided, and the ones that could
be tested were tested on a throwaway branch (`spike/desktop-d0`, not merged). "Proved" below means
a test ran; "recommended" means it was decided without a test yet.

| Decision | Choice | Status |
|---|---|---|
| Code sharing | KMP modules with AGP 9's `com.android.kotlin.multiplatform.library` and `jvm("desktop")`. Pure-JVM modules (`:core:model`, `:core:scheduler`, `:core:ai`, `:core:anki`, `:core:common`) stay JVM. | Proved |
| Dependency injection | **Koin** 4.2.2 | Proved (graph, ViewModel with `SavedStateHandle`); see the `verify()` caveat |
| Math on desktop | **JLaTeXMath** 1.0.7, raw TeX as the fallback | Proved (21 of 26 sample formulas) |
| Audio on desktop | `javax.sound` with `mp3spi` and `vorbisspi` | Decoding proved for wav, mp3, ogg; playback and the other formats are not |
| API keys on desktop | OS keychain through `java-keyring` 1.0.4 (holds the AES key `SecretStore` already uses), key file fallback | Built in D4; keychain proved on macOS arm64 only |
| File pickers | Own `expect`/`actual` API in `:core:ui/files` | Recommended |
| Background jobs | Coroutines in an application scope behind the existing repository interfaces | Recommended |
| SQLite | Android keeps the framework driver (`AndroidSQLiteDriver`); desktop uses `BundledSQLiteDriver` | Proved on both (SQLite 3.50.1 on desktop) |
| Data directory | Linux `$XDG_DATA_HOME/mnemo`, Windows `%LOCALAPPDATA%\Mnemo`, macOS `~/Library/Application Support/Mnemo` (`MNEMO_DATA_DIR` overrides); secrets in a `secrets` directory backups never read | Built in D4 (finding 9) |
| Version numbers | Shared: `mnemo.versionName` is the desktop package version too | Recommended |
| macOS architectures | Apple Silicon first. All three native libraries the app needs (SQLite, zstd, Skia) also ship Intel builds, so adding Intel is a CI runner question, not a code one. | **Owner to confirm** |

### Versions (ready for `gradle/libs.versions.toml`)

| Library | Version | Notes |
|---|---|---|
| Kotlin, AGP, Room, Roborazzi, lifecycle, zstd-kmp | unchanged (2.3.21, 9.4.1, 2.8.5, 1.75.0, 2.11.0, 0.4.0) | Room's KMP artifacts are the same version as today's |
| Compose Multiplatform plugin (`org.jetbrains.compose`) | **1.12.1** | Skiko 0.150.1. Works with Kotlin 2.3.21 |
| `org.jetbrains.compose.material3:material3` | **1.9.0** | Deliberately not 1.12.0-alpha03, see "Findings" |
| `org.jetbrains.compose.material3.adaptive:adaptive` | 1.3.0-rc01 | Resolves to androidx 1.3.0 on Android, which the app already uses |
| `org.jetbrains.androidx.navigation:navigation-compose` | 2.10.0-beta01 | The only beta in the set. Android still resolves the app's 2.10.2 |
| `org.jetbrains.compose.material:material-icons-extended` | 1.7.3 | All 80 icons in `MnemoIcons` compile against it on both targets: no need to vendor icons |
| `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 | |
| `androidx.sqlite:sqlite-bundled` | 2.6.2 | Desktop only. Natives for linux x64/arm64, macOS x64/arm64, windows x64 |
| `io.insert-koin:koin-core`, `koin-compose`, `koin-compose-viewmodel`, `koin-android`, `koin-androidx-workmanager`, `koin-test` | 4.2.2 | Apache-2.0 |
| `org.scilab.forge:jlatexmath` | 1.0.7 | GPL-2.0-or-later with a linking exception |
| `com.googlecode.soundlibs:mp3spi`, `vorbisspi` | 1.9.5.4, 1.0.3.3 | Believed LGPL, but their POMs carry no license field: confirm each (and JLayer, JOrbis, Tritonus) before bundling in D5 |
| `com.github.javakeyring:java-keyring` | 1.0.4 | BSD-3-Clause. Pulls in JNA (LGPL-2.1-or-later or Apache-2.0) and, on Linux, a D-Bus client |
| `io.github.takahirom.roborazzi:roborazzi-compose-desktop` | 1.75.0 | Desktop screenshots |

Every entry is compatible with GPL-3.0-or-later (ADR 0009). All of them go into `NOTICE` and
`app/config` when they are first bundled.

## Findings that change the roadmap

These came out of the spike and are not what the roadmap assumed.

1. **Room's schema is unchanged by the move.** The KMP build of the real `MnemoDatabase`
   (entities, DAOs, converters copied unchanged) exports a `4.json` whose `database` section equals
   the committed Android one, `identityHash` `0fb4d61cdbd3238e9ab465ae0ff98218` included. So
   existing installs validate against the new code and D4 needs no schema migration.
2. **Room migrations and raw queries need small rewrites, and they work.** `Migration.migrate`
   takes a `SQLiteConnection` (`import androidx.sqlite.execSQL`; statements with arguments use
   `prepare` and `bindText`); the seed callback overrides `onCreate(connection)`; browse queries
   become `RoomRawQuery` with bound arguments, and `@RawQuery` still returns `PagingSource`,
   `Flow` and `List`; `withTransaction` becomes
   `useWriterConnection { it.immediateTransaction { … } }`; `MnemoDatabase` gets
   `@ConstructedBy`. The builder is created per platform (`Room.databaseBuilder` needs a `Context`
   on Android), and `configure(builder, driver)` is shared.
3. **Material3: pin 1.9.0.** Every Compose Multiplatform Material3 newer than 1.9.0 is an alpha,
   and on Android it resolves to the matching androidx alpha (1.12.0-alpha03 gives
   `androidx.compose.material3:material3:1.5.0-alpha22`), which would put an alpha in the store
   build. 1.9.0 resolves to androidx material3 **1.4.0 on Android, exactly what the app has today**,
   and renders on desktop next to Compose Multiplatform 1.12.1's runtime and UI. Revisit when a
   stable Material3 1.5 line exists.
4. **The Android exit check gains tasks.** A KMP module has no `testDebugUnitTest` (checked:
   "task not found"). Its Android host tests are `testAndroidHostTest`, its desktop tests
   `desktopTest`, and its screenshots `recordRoborazziAndroidHostTest` /
   `verifyRoborazziAndroidHostTest` and `recordRoborazziDesktop` / `verifyRoborazziDesktop`. The
   plugin ships all of these. `verifyRoborazziDebug` only covers modules that are still plain
   Android libraries. Verify fails on a real pixel change and passes when it is reverted. `lint`
   exists on a KMP module (`lintAnalyzeAndroidHostTest`). CLAUDE.md's exit check is updated in D1,
   when the first module converts.
5. **Koin's `verify()` is weaker than Hilt.** It only inspects constructor references
   (`viewModelOf`, `singleOf`): a graph missing the database binding still passed `verify()`.
   `checkModules` instantiates every definition and did detect it. D2's graph test uses
   `checkModules` with fake platform modules (in-memory database, software cipher, test
   `WorkManager`), plus `verify()`.
6. **Build-script details.** `compose.runtime`, `compose.foundation` and friends are deprecated
   accessors: use the catalog coordinates. The `androidHostTest` source set has no typed accessor
   (`getByName("androidHostTest")`). KSP configurations are `kspAndroid` and `kspDesktop`. Host
   tests need `withHostTest { isIncludeAndroidResources = true }` and the same `--add-opens` flags
   as today. Two modules with the same package cannot both be in `:app` (dex merge error): the spike
   hit it by copying `core.database` next to the original. Not a problem for real moves.
7. **Math: parity is close, not exact.** JLaTeXMath rendered fractions, roots, sums, integrals,
   matrices, `aligned`, `\mathbb`, `\operatorname`, `\underbrace`, `\binom`, `\newcommand`. It
   throws on `\cancel`, `\htmlClass` and the braced form `\color{red}{x}`, and `\begin{cases}`
   loses its column spacing (`1x>0`). `\ce{…}` fails too, but the Android KaTeX bundle has no
   mhchem either, so that is not a gap. The library's last release was 2018 (1.0.7) and its last
   commit 2022: it is stable but effectively unmaintained. D5 keeps the raw-TeX fallback, adds
   `\cancel` and `\color{…}{…}` rewrites if the cases are common, and re-checks KCEF if this
   becomes a support burden.
8. **Audio: three of the usual formats.** `javax.sound` with `mp3spi` and `vorbisspi` decoded
   wav, mp3 and ogg (a one-second tone; the Ogg file came from ffmpeg's experimental encoder, since the local ffmpeg has no libvorbis). flac,
   m4a (AAC) and opus threw `UnsupportedAudioFileException`. m4a is common in AnkiDroid
   recordings, so D5 shows a clear "unsupported format" message and evaluates a pure-Java AAC
   decoder.
9. **Icons need no work.** `MnemoIcons` compiles unchanged on both targets.

## What was proved

Results are from macOS 26 (Apple Silicon), JDK 21 / 25, Gradle 9.6.0, on the versions above.

- **KMP build** (`:spike:shared`, Android + `desktop`): `java.time` in `commonMain` and a dependency
  on the pure-JVM `:core:model` and `:core:scheduler` compile on both targets, so the fallback of
  moving `:core:model` to `kotlinx-datetime` is not needed. Kotlin treats a source set shared only
  by JVM and Android targets as JVM code. The price is that adding a non-JVM target later (iOS,
  web) would mean moving off `java.time` and the JVM-only modules first.
- **Android build unharmed:** the existing Hilt/KSP modules consume the KMP module and
  `:app:assembleDebug` succeeds with it in the app (Compose Multiplatform, Room, Koin and the
  WorkManager integration on the class path).
- **Tests:** Robolectric plus Compose UI tests run in the module's Android target; Roborazzi
  records and verifies there and on desktop; desktop Compose UI tests (`runComposeUiTest`) render
  through Skia.
- **Room on real Android data.** Databases were made by the *unmodified Android* `MnemoDatabase`
  under Robolectric (real DAOs, real seed callback, real `MigrationTestHelper`), not built by hand:
  a v4 file with a deck, a note (LaTeX in a field, tags, a hint), a card and a review log, plus v1
  (with data) and v3 files. Desktop, with `BundledSQLiteDriver` and the ported code, opened
  copies of all three:
  - v4: every row reads back, `RoomRawQuery` browse (`ids`, `count`, `PagingSource` page) works,
    a write inside `RoomTransactionRunner` survives a close and reopen, `user_version` stays 4;
  - v1 and v3: migrate to v4 with data intact, and Type-in and Multiple choice are seeded;
  - a fresh in-memory database seeds the five built-in note types.
  The Android target (KMP Room with the framework driver, under Robolectric) opens the same three
  files.
- **Natives, this machine (macOS arm64):** SQLite (bundled driver), Skia (rendered a PNG) and zstd
  (`:core:anki`'s 21 tests, which read real Anki packages) all load and run.
- **DI:** a graph with a `SavedStateHandle` ViewModel resolves; `koin-androidx-workmanager` and
  `koin-android` compile into the Android target.
- **Natives on Linux x64:** in a `linux/amd64` container (Debian-based `eclipse-temurin:21`, the
  Gradle JDK 25 toolchain, run under Rosetta on Apple Silicon, with `libgl1 libxrender1 libxtst6
  libxi6` added to the image, which already had fontconfig and freetype) Skia rendered a PNG, the four Room tests (v4, v1,
  v3 and the in-memory one) passed, and `:core:anki`'s 21 tests (zstd and bundled SQLite) passed.
  That run predates the math, Koin and audio tests, so those ran on macOS only.
- **Static evidence for the other platforms:** the SQLite jar bundles `linux_x64`, `linux_arm64`,
  `osx_x64`, `osx_arm64` and `windows_x64` libraries; the zstd jar bundles Linux and Windows builds for x64 and arm64 and macOS builds for both; Skiko publishes runtimes for all five
  OS/architecture pairs (checked against Maven Central).

## Not yet proven

- **Windows x64 natives at run time.** Only checked statically (above). D1 added
  `NativeLibrariesTest` (bundled SQLite, zstd, Skia) and a `desktop` CI job that runs it on
  `windows-latest`, `ubuntu-latest` and `macos-latest`; it has passed on macOS arm64 only, so this
  stays open until that job is green on GitHub.
- **A real Linux x64 machine and macOS Intel**, and the Linux packages a user needs installed
  (the four packages added to the container were not tested one by one, and the tests opened no
  window). The D1 CI matrix covers Linux; the install guide (D8) lists what is needed.
- **OS keychain** through `java-keyring`: proved on macOS arm64 in D4 (`DesktopSecretCipherTest`
  stores, reads and deletes a throwaway entry). Windows Credential Manager and the Linux Secret
  Service are not proved yet: the test skips itself where `works()` is false, so CI passes without
  a keychain. The key-file fallback is the safety net.
- **Audio playback.** Only decoding is tested (D5).
- ~~**Koin with navigation arguments** through `koinViewModel()`, and `workerOf`.~~ Closed in D2 on
  Android (see "Findings from D2"). On desktop, navigation arguments still need a check in D6.
- ~~**`DatabaseSnapshot` on the driver API**, and restore-at-start.~~ Closed in D4 (see "Findings
  from D4").
- ~~**Apache PDFBox 3 on desktop.**~~ Closed in D4: BouncyCastle is excluded and the extractor tests
  pass on both targets.
- **A running window and installers.** D1 opened a window (`:desktop:run` and the packaged app
  image start on macOS arm64). Installers (`.dmg`, `.msi`, `.deb`) are D8.
- **R8 keep rules for the new libraries.** `:app:assembleRelease` succeeds with the KMP module
  in `:app`, but nothing in `:app` calls it, so R8 shrinks it away and Koin's and Room KMP's rules
  are not exercised. D2 and D4 run `assembleRelease` and a release smoke test after each move.

## Findings from D1

- **`jlink` needs a JDK with `jmods`.** The Gradle daemon runs on the JDK 25 that
  `gradle-daemon-jvm.properties` names, and the copy foojay downloads has no `jmods`, so Compose's
  `createRuntimeImage` fails with "This JDK does not contain packaged modules". The desktop
  convention plugin points the packaging tasks at a JDK 21 toolchain, set lazily after the JetBrains
  plugin creates its tasks (a `configureEach` alone lost to the plugin's own value). CI uses
  `setup-java` Temurin 21. `includeAllModules` lists the Gradle JVM's modules, which JDK 21 lacks
  (`jdk.graal.compiler.management`), so the runtime's modules are listed by name; D8 trims them.
- **Desktop tests need Skia's native runtime explicitly.** A library module's test classpath has no
  `skiko-awt-runtime-<os>`, so `mnemo.kmp.compose` adds `compose.desktop.currentOs` to `desktopTest`.
  `runComposeUiTest` is deprecated in favor of `androidx.compose.ui.test.v2`.
- **The build-logic source-set accessors are missing.** `commonMain` and friends are generated
  accessors of build scripts; plugins use `sourceSets.getByName("commonMain").dependencies`.

## Findings from D2

Hilt is replaced by Koin 4.2.2 on Android (`CLAUDE.md`, "Dependency injection"). Checked with the
Android exit check (395 unit tests, the Roborazzi baselines unchanged, lint) and a graph test.

- **Scopes carry over exactly.** Hilt's unscoped `@Inject` classes are Koin `factory`, its
  `@Singleton` ones `single`. Repositories kept their per-injection instances, so nothing that
  looked stateless became shared.
- **Qualifiers cost a lambda.** `factoryOf(::X)` can't pass a qualifier, so the eight classes that
  took `@Dispatcher(...)` are `factory { X(get(), dispatcher(MnemoDispatchers.IO)) }`
  (`dispatcher` is a `Scope` extension in `:core:common`). The rest use `factoryOf`, `singleOf` and
  `bind`. The `@Dispatcher` annotation and every `javax.inject` annotation are removed.
- **`verify()` needs help, `checkModules` needs the right key.**
  - `verify()` reads a class's primary constructor, so `FileMediaRepository` and `FileSecretStore`
    (built through a secondary constructor that takes a `Context`) need an `injections` entry.
    Verifying the modules one by one reports cross-module bindings as missing, so the test
    verifies one module that `includes(mnemoModules)`.
  - `checkModules` finds a worker's definition under `TypeQualifier(workerClass)`, not the
    plain class, so its `WorkerParameters` must be given with that qualifier. A real
    `WorkerParameters` comes from WorkManager's `TestListenableWorkerBuilder`.
  - Both were checked by deleting a binding that is only used inside a lambda: each test fails.
- **Robolectric makes an application per test, Koin is global.** `MnemoApplication.onCreate` would
  throw `KoinApplicationAlreadyStartedException` from the second test on, so app tests run on
  `TestMnemoApplication` (`robolectric.properties` sets it as the default), which stops and starts
  Koin with `mnemoModules + testStorageModule`. Later modules override earlier ones, which replaces
  Hilt's `@TestInstallIn`.
- **Navigation arguments reach the `SavedStateHandle`** through `koinViewModel()` inside the
  Navigation Compose back-stack entry (the Browse test gets "Edit note", derived from `noteId`).
  This closes the "Koin with navigation arguments" item under "Not yet proven"; `workerOf` is
  covered by the graph test. Executing a worker under Koin's factory on a device is not.
- **Layering held.** `:app` still doesn't see Room or DataStore: `:core:data` exposes
  `dataLayerModules`. Each Gradle module owns the module for its own (often `internal`) classes.

## Findings from D4

The data layer is multiplatform (`:core:database`, `:core:datastore`, `:core:security`,
`:core:ingest`, `:core:data`, `:core:domain`, `:core:testing`). Checked with the Android exit check,
`desktopTest` in every module and `:desktop:test` on macOS arm64, and by installing the new debug
and R8 release builds over a real collection on an API 35 emulator (see below).

1. **Android now uses Room's driver API too.** Migrations, the seed callback, raw queries and
   transactions moved to `SQLiteConnection`, `RoomRawQuery` and `useWriterConnection { immediateTransaction }`
   in a first step on the still-Android module, then the module moved. Android builds the database
   with `AndroidSQLiteDriver` (framework SQLite, same file, same WAL), the desktop with
   `BundledSQLiteDriver`. The exported schema did not change (`4.json` and its hash are identical).
   On a real 7-deck, 301-card, 1,503-review collection made by the old build, the new debug and
   release (R8) builds opened it unchanged and saved a new review.
2. **Room's `.lck` file.** With the driver API Room keeps a zero-byte `mnemo.db.lck` next to the
   database (it serializes migrations across processes). Harmless; backups don't copy it.
3. **`AndroidSQLiteDriver` can't run `EXPLAIN`.** Its statements only step queries it recognizes
   as SELECT or PRAGMA. The one test that reads a query plan uses the framework's open helper on
   Android and the driver on desktop (`queryPlan` in `PlatformTest`).
4. **A closed database cancels instead of failing.** With the driver API, using a `RoomDatabase`
   after `close()` throws a `CancellationException`, which code that rethrows cancellation (the
   backup job) treats as cancellation, not failure. The test that wanted a failure now makes the
   output stream fail.
5. **`DatabaseSnapshot`** reads `PRAGMA user_version` and `PRAGMA database_list` through the driver
   (so it needs neither `openHelper` nor the file name), checkpoints the WAL, and copies the files
   inside an immediate transaction. `version` became `suspend fun version()`.
6. **Schema assets for host tests.** AGP's KMP plugin has no `androidTest` assets, so the
   `mnemo.kmp.room` convention adds `schemas/` to the host tests' assets through
   `KotlinMultiplatformAndroidComponentsExtension.onVariants { hostTests … sources.assets }`;
   `MigrationTestHelper` then runs on Android (Robolectric) and, with a schema path, on desktop.
   `MigrationTest` is one shared test for both targets, plus `FixtureDatabasesTest` with real
   databases of versions 1–4 (`core/database/src/commonTest/resources/fixtures`).
7. **Shared tests are JVM tests.** `commonTest` of a module with only Android and JVM targets is
   JVM code: `org.junit.*`, `java.io.File` and JUnit rules work in it. The Robolectric runner is
   chosen by an `expect abstract class PlatformRunner` (`:core:testing`); `PlatformTest` adds a
   temporary folder. Cross-module test helpers (`inMemoryDatabase()`, `fileDatabase()`,
   `testSqliteDriver()`, `TestAppDirectories`) sit in `:core:testing`. `:core:database` and
   `:core:ingest` can't use it (it depends on them), so they carry their own small `PlatformTest`.
8. **Koin lifecycle.** The database single and DataStore's scope are closed with `onClose`, because
   a desktop restart and the tests reopen the same files in one process (DataStore allows one
   instance per file). `org.koin.dsl.onClose` is the import.
9. **Secrets layout on desktop.** The ADR's "sibling directory" is `<data>/secrets`, not a
   directory outside the data directory: a backup reads only the database, the preferences and the
   media folder by name, so nothing ever touches it. The key file, if used, is `<data>/secrets.key`
   beside it, because `SecretStore.retainOnly` deletes every file in `secrets` it doesn't know.
10. **Restart.** A restore needs a new process. `ProcessAppRestarter` starts the same command
    (`ProcessHandle.info()`) with `MNEMO_RESTARTED=1`, and the new process waits up to ten seconds
    for the old one to release the collection lock.
11. **Cross-device backups.** A backup made by the Android build and one made by the desktop build
    (`resources/backups`, from `SampleCollection`) have the same entries and restore on both
    targets.

## Findings from D5

`:core:designsystem` and `:core:ui` are KMP modules (Android and `desktop`), with their fonts, logo and
strings in Compose resources. Checked with the Android exit check (`assembleDebug testDebugUnitTest
testAndroidHostTest desktopTest lint`, the JVM module tests, `verifyRoborazziDebug`,
`verifyRoborazziAndroidHostTest`, `verifyRoborazziDesktop`), `assembleRelease` (R8) with the 16 KB check,
and a debug install on an API 35 emulator (home screen with all three fonts, strings, icons, logo).

1. **Compose resources reach Android only with Android resources enabled.** Without
   `androidResources { enable = true }` on the module's Android target the plugin packages no assets, and
   every Robolectric test fails with "Missing resource … Android context is not initialized".
   `mnemo.kmp.compose` turns it on. With it, the assets are under
   `assets/composeResources/<package of Res>/…` in the AAR, so the Android *library* features and `:app`
   (which do not apply the Compose plugin) get the fonts, strings and logo, in their Robolectric tests too.
   Each module sets `compose.resources { packageOfResClass = … }` so the package is not derived from
   the Gradle group.
2. **Fonts load correctly on both platforms, and `JetBrainsMono` and friends are gone.** `Font(Res.font…)`
   is composable, so the families are built in `MnemoTheme` (`rememberMnemoFonts`) and read through
   `MnemoTheme.fonts` (`MnemoFonts`; `MnemoFonts.System` outside the theme). Variable-font weights were
   passed through `FontVariation`. The Robolectric baselines came out identical apart from the logo (finding 3),
   and the emulator showed the bundled fonts. Loading is asynchronous in principle, so a first frame
   may use a fallback font for a moment; the screenshot tests never caught one, and a preload is not built. Skia draws the same fonts a
   little heavier than Android's renderer; the desktop baselines are their own files.
3. **The logo is the one visible Android change.** `mnemo_logo.xml` is now a Compose drawable, parsed by
   Compose's vector reader instead of Android's: 3–4 antialiased pixels on the tile's edge differ.
   Re-recorded, with no other change: `onboarding_light/dark` (`:app`), `settings_light`
   (`:feature:settings`), and the component catalog (moved to `src/androidHostTest/screenshots`).
4. **The KaTeX page read the fonts from Android `res/font`**, which no longer exists. `CardHtml.FONTS`
   points at the same files as Compose text uses, in the assets (`/assets/composeResources/…/font`), and
   the `/res/` path handler was dropped. Checked that the APK contains those paths; the WebView itself was
   not driven on a device.
5. **Strings.** `%s` became `%1$s` and `\'` became `'` (Compose resources are not aapt strings). Only
   `:core:ui`'s 54 strings moved; each feature's strings move when the feature converts (D6), because a
   module needs the Compose plugin to have `Res`. No feature reads `core.ui.R`, so nothing else changed.
6. **Icons: no vendoring.** `org.jetbrains.compose.material:material-icons-extended` 1.7.3 works for all
   80 icons on both targets, as D0 found.
7. **`@Preview` from `androidx.compose.ui.tooling.preview` compiles in `commonMain`**
   (`cmp-ui-tooling-preview`); no source changes.
8. **Math on the desktop is laid out by `MarkdownText`, not a separate engine.** `MathPainter` turns TeX
   into a bitmap; inline formulas become inline-text placeholders aligned to the baseline (the
   picture hangs below the placeholder by its depth), display formulas get their own centered, scrollable
   line, and a formula the painter cannot draw (null) shows its source in code style. Clozes,
   links and sounds work around formulas because the Markdown pipeline is the same. JLaTeXMath 1.0.7
   throws on `\cancel`, `\htmlClass`, `\color{c}{x}` (rewritten to `\textcolor{c}{x}`) and
   unknown commands; it tolerates an unclosed brace (`\frac{1` draws). The formulas' cache holds 128 pictures.
   Fractions in running text use TeX's text style, so they are small, as KaTeX's are.
9. **Audio.** `javax.sound` with mp3spi and vorbisspi decodes wav, mp3 and ogg through content sniffing
   (media files have no extension). aac in an m4a container has no decoder and is reported
   (`DesktopCardAudio.AudioProblem.UnsupportedFormat`) instead of playing; the shell shows the message
   in D6. A machine with no output line reports `NoOutput`. Only decoding is tested (there is no sound
   card in CI); playback was not heard. ffmpeg's experimental Vorbis encoder makes streams that JOrbis
   decodes to zero bytes when they are short or 22 kHz: the test sound is one second of 44.1 kHz stereo.
10. **Licenses.** soundlibs' parent POM declares LGPL 2.1 for mp3spi, vorbisspi, JLayer, JOrbis and
    Tritonus-share; the LGPL lets a GPL program distribute the combination (section 3). Listed in `NOTICE`.
    They are Gradle dependencies, so the desktop AboutLibraries list (D6) picks them up.
11. **Desktop screenshots.** `recordRoborazziDesktop` / `verifyRoborazziDesktop` work with
    `runDesktopComposeUiTest(width, height)` and `onRoot().captureRoboImage(path)` (from
    `io.github.takahirom.roborazzi`; the Android one is `com.github.takahirom.roborazzi`). Baselines are
    recorded on macOS arm64. Plain `desktopTest` does not compare, so CI on other systems is not affected;
    the `screenshots` CI job (non-blocking) now runs the desktop and Android host verifications too.
    The card screenshots include the speaker emoji, whose glyph depends on the OS.
12. **File dialogs** are AWT `FileDialog` (native on macOS and Windows, GTK on Linux) on the IO dispatcher;
    a folder uses the same dialog with `apple.awt.fileDialogForDirectories` on macOS and Swing's
    `JFileChooser` elsewhere. Only the extension mapping is tested: the dialogs need a person.

## Alternatives

- **Separate desktop project (copy the domain and data code):** rejected in the roadmap; the
  ~30,000 shared lines would drift.
- **Metro instead of Koin:** compile-time like Dagger, but less documented for Compose
  Multiplatform. Not evaluated hands-on. Koin's runtime graph check (`checkModules`) is enough for
  an app this size.
- **KCEF/JCEF for math:** it would run the same KaTeX as Android, but downloads about 100 MB of
  Chromium at first run and embeds a browser engine. Kept as the escape hatch (finding 7).
- **JavaFX Media or VLCJ for audio:** JavaFX adds a large runtime, VLCJ needs VLC installed.
- **Plain JVM modules plus copies of the Android ones instead of KMP:** the spike showed the KMP
  route costs very little (the Room code needed the rewrites in finding 2, the rest moved as is).
- **Newest Material3 (1.12.0-alpha03 or 1.13.0-alpha01):** rejected, see finding 3.

## Consequences

- D1 starts from `release/1.0` (owner: confirm) and adds `mnemo.kmp.*` convention plugins that
  wrap exactly the setup in the spike: `kotlin.multiplatform`, `com.android.kotlin.multiplatform.library`
  with `jvm("desktop")`, `withHostTest { isIncludeAndroidResources = true }`, the Robolectric JVM
  flags, KSP for `kspAndroid` and `kspDesktop`.
- The Android exit check becomes
  `./gradlew assembleDebug testDebugUnitTest testAndroidHostTest desktopTest lint verifyRoborazziDebug verifyRoborazziAndroidHostTest`
  (the last two only where modules exist), and CLAUDE.md says so.
- D2 uses `checkModules`, not `verify()` alone, for the graph test (finding 5).
- D4 ports the migrations and `DatabaseSnapshot` as in finding 2, keeps the exported schema
  identical (Room's schema export writes the same `4.json`; `MigrationTest` validates every
  version against the committed JSON), and keeps fixture databases made by the Android app for
  every schema version. Done.
- Desktop-only bundled libraries (JLaTeXMath, the audio SPIs, java-keyring and their transitive
  dependencies, Skiko, the JRE) are added to `NOTICE` and `app/config` when they are first bundled,
  and `scripts/fdroid/check-foss-deps.py` reads `:desktop:runtimeClasspath`.
- Adding iOS or web targets later would require replacing `java.time` and the JVM-only modules;
  that is out of scope.
