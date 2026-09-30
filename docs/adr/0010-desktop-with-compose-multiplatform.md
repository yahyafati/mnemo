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
| API keys on desktop | OS keychain through `java-keyring` 1.0.4 (holds the AES key `SecretStore` already uses), key file fallback | Recommended, not tested on any OS |
| File pickers | Own `expect`/`actual` API in `:core:ui/files` | Recommended |
| Background jobs | Coroutines in an application scope behind the existing repository interfaces | Recommended |
| SQLite | Android keeps the framework driver (`AndroidSQLiteDriver`); desktop uses `BundledSQLiteDriver` | Proved on both (SQLite 3.50.1 on desktop) |
| Data directory | Linux `$XDG_DATA_HOME/mnemo`, Windows `%LOCALAPPDATA%\Mnemo`, macOS `~/Library/Application Support/Mnemo`; secrets in a sibling directory backups never read | Recommended |
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

- **Windows x64 natives at run time.** Only checked statically (above). A CI run on
  `windows-latest` is a D1 task.
- **A real Linux x64 machine and macOS Intel**, and the Linux packages a user needs installed
  (the four packages added to the container were not tested one by one, and the tests opened no
  window). The D1 CI matrix covers Linux; the install guide (D8) lists what is needed.
- **OS keychain** through `java-keyring` on any OS. A test writes to the user's real keychain, so
  it was left for D4, on CI machines and on the owner's machines. The key-file fallback is the
  safety net.
- **Audio playback.** Only decoding was tested.
- **Koin with navigation arguments** through `koinViewModel()`, and `workerOf`. Only the plain
  ViewModel with a handle was run; D2 covers the rest on Android first.
- **`DatabaseSnapshot` on the driver API** (backup: WAL checkpoint plus a copy inside a write
  transaction), and restore-at-start. D4.
- **Apache PDFBox 3 on desktop** (D4). It declares BouncyCastle as a dependency; exclude it as ADR
  0006 does for PdfBox-Android, and confirm the text extractor tests still pass.
- **A running window and installers.** The spike used tests, not `application { }`. D1 and D8.
- **R8 keep rules for the new libraries.** `:app:assembleRelease` succeeds with the KMP module
  in `:app`, but nothing in `:app` calls it, so R8 shrinks it away and Koin's and Room KMP's rules
  are not exercised. D2 and D4 run `assembleRelease` and a release smoke test after each move.

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
  identical (a test compares `identityHash` with the committed 4.json), and keeps fixture databases
  made by the Android app for every schema version.
- Desktop-only bundled libraries (JLaTeXMath, the audio SPIs, java-keyring and their transitive
  dependencies, Skiko, the JRE) are added to `NOTICE` and `app/config` when they are first bundled,
  and `scripts/fdroid/check-foss-deps.py` reads `:desktop:runtimeClasspath`.
- Adding iOS or web targets later would require replacing `java.time` and the JVM-only modules;
  that is out of scope.
