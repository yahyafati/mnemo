# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Mnemo is a local-first spaced-repetition Android app (package `com.yahyafati.mnemo`). Read `docs/PROJECT_OVERVIEW.md` (product), `docs/ARCHITECTURE.md` (modules, layers, rules) and `docs/ROADMAP.md` (phases). Decisions are recorded in `docs/adr/`.

**Phases 0–6 are implemented** (open: Phase 1's frame-rate exit check, Phase 3's manual "Test connection" against a real hosted and local provider, Phase 4's manual "1,000 words in under 30 s" check and on-device dictation, Phase 5's on-device frame-rate check of Analytics, and Phase 6's hardware checks in ADR 0008): decks (nested `Parent::Child`), manual Basic / Basic + Reversed / Cloze notes, FSRS-6 study sessions with undo, scheduling/appearance settings; Anki `.apkg`/`.colpkg` import and export, media (images, KaTeX math), backup/restore, JSON export and the card browser; AI providers (Settings › AI providers: presets, encrypted keys, test connection, per-task routing, token usage); Smart Extract (paste, PDF, link, dictation → streamed review queue → accepted notes) and study-time Explain / Example / Rewrite; Analytics (KPIs, forgetting curve, activity calendar, deck maturity, forecast, hardest cards), the Decks retention tiles and the on-device FSRS optimizer (Settings › Scheduling); Type-in and Multiple choice cards, hints, audio/TTS, exam countdowns, AI Co-Author, the daily reminder, the home-screen widget, onboarding, tablet/foldable layouts and R8 release builds. The license is GPL-3.0-or-later (`LICENSE`, `NOTICE`, ADR 0009): no proprietary SDKs, and new bundled or linked third-party code needs its notice added to `NOTICE`. Release docs (store listing, privacy policy, distribution, signing) are in `docs/release/`; the path to v1.0 on Play and F-Droid is `docs/release/ROADMAP.md`. A desktop app (Windows, macOS, Linux) from the same codebase via Compose Multiplatform is in progress: `docs/desktop/ROADMAP.md` (steps D0–D9 and the working rules that keep Android shippable meanwhile). D0, D1, D2 and D3 are done: the technical choices and versions are in ADR 0010 (Koin, Compose Multiplatform 1.12.1 with Material3 pinned to 1.9.0, JLaTeXMath, KMP modules with AGP's `com.android.kotlin.multiplatform.library`), `:desktop` opens a window that uses `:core:model` and `:core:scheduler`, and Koin has replaced Hilt on Android (see "Dependency injection" below). No production module has moved to KMP yet: D3 put the Android APIs behind interfaces (see "Platform seams" below), D4 starts moving modules.

Modules today: `:app`, `:desktop`, `:core:{ai,anki,common,data,database,datastore,designsystem,domain,ingest,model,scheduler,security,testing,ui}`, `:feature:{analytics,browse,create,decks,settings,study}`. Add new ones to `settings.gradle.kts`.

## Commands

Use the Gradle wrapper from the repo root:

```bash
./gradlew assembleDebug testDebugUnitTest lint   # the phase exit check
./gradlew installDebug                           # install on connected device/emulator
./gradlew connectedDebugAndroidTest              # instrumented tests, needs a device

# single unit test class / method
./gradlew :app:testDebugUnitTest --tests "com.yahyafati.mnemo.ui.MnemoAppNavigationTest"
./gradlew :core:common:test --tests "com.yahyafati.mnemo.core.common.result.MnemoResultTest"

# screenshot baselines (Roborazzi, committed in <module>/src/test/screenshots: the design system,
# every feature, and onboarding in :app)
./gradlew verifyRoborazziDebug
./gradlew recordRoborazziDebug   # after an intended visual change; compare with docs/design/ by eye

./gradlew assembleRelease        # R8-minified; keep rules in app/src/main/keepRules
```

Desktop (docs/desktop/ROADMAP.md, ADR 0010):

```bash
./gradlew :desktop:run                    # open the desktop window
./gradlew :desktop:test                   # includes NativeLibrariesTest: bundled SQLite, zstd and Skia load on this OS
./gradlew :desktop:createDistributable    # app image in desktop/build/compose/binaries (jlink runs on a JDK 21 toolchain)
```

A Kotlin Multiplatform module has no `testDebugUnitTest` (ADR 0010): its tests are `testAndroidHostTest` (Robolectric) and `desktopTest`, and its screenshots `verifyRoborazziAndroidHostTest` / `verifyRoborazziDesktop`. Once the first module converts, the Android exit check also runs `testAndroidHostTest desktopTest` and those two (Gradle fails a task name no module has, so they are not in the CI command yet). `.github/workflows/ci.yml` runs `:desktop:test :core:anki:test` on Ubuntu, Windows and macOS.

`python3 scripts/desktop/check-android-imports.py` (CI job `android-imports`) fails on an Android import outside the Android-only files; `--list` shows every file that has one.

JVM modules (`:core:model`, `:core:common`, `:core:scheduler`, `:core:anki`, `:core:ai`) use `test`, not `testDebugUnitTest`, so run them too: `./gradlew :core:ai:test :core:anki:test :core:model:test :core:scheduler:test :core:common:test`.

Anki test packages in `core/anki/src/test/resources/` are written by real Anki: regenerate with `core/anki/fixtures/make_fixtures.py` (needs `pip install anki`). Tests that depend on them check relationships, not absolute dates.

The Gradle configuration cache is on. `local.properties` is machine-specific.

## Build setup (non-obvious bits)

- **Convention plugins** in `build-logic/` (included build) hold all shared config: `mnemo.android.application`, `.library`, `.compose`, `.feature`, `.room`, `mnemo.jvm.library`, and for desktop `mnemo.kmp.library` (Android + `desktop` targets), `mnemo.kmp.compose`, `mnemo.kmp.feature` and `mnemo.desktop.application`. No module applies the `kmp` ones yet; they were checked on a throwaway module (Robolectric and desktop Compose tests, Roborazzi and lint tasks). `mnemo.kmp.feature` puts `:core:designsystem`, `:core:domain` and `:core:ui` in `commonMain`, so apply it only after they are KMP modules. Add KMP dependencies to `getByName("commonMain").dependencies` (the typed `commonMain` accessor is not available in build-logic). Module build files should be a few lines; put shared settings in the plugins (`build-logic/convention/src/main/kotlin`).
- Library namespaces are derived from the module path (`:core:designsystem` → `com.yahyafati.mnemo.core.designsystem`). Don't set `namespace` in library modules.
- `mnemo.android.feature` = library + Compose + Koin + serialization + `:core:designsystem` + `:core:ui`. Features must never depend on other features. They navigate through the `@Serializable` routes in `:core:ui/navigation/Routes.kt`.
- **AGP 9.x with built-in Kotlin**: there is no `org.jetbrains.kotlin.android` plugin. Don't add it. Pure JVM modules use `org.jetbrains.kotlin.jvm` (via `mnemo.jvm.library`).
- Kotlin is pinned to 2.3.21 (KSP 2.3.x). Lint suggests 2.4.x. Upgrade Kotlin and KSP together (Room uses KSP) and rerun the build.
- The new AGP DSL is in use: `compileSdk { version = release(37) }`, and `buildTypes.release.optimization { enable = true }` (R8) instead of `isMinifyEnabled`.
- `compose_stability.conf` (root, applied by the Compose convention) marks `:core:model` and `java.time` types stable. Keep model classes immutable (`val`, read-only collections), or remove them from it.
- The feature convention applies Roborazzi; screenshot tests capture to `src/test/screenshots/<name>.png`.
- R8 keep rules go in `app/src/main/keepRules/*.keep`. There is no `proguard-rules.pro`.
- minSdk 29, target/compileSdk 37, Java 11 bytecode.
- **Desktop packaging**: the Gradle daemon runs on the JDK 25 toolchain (`gradle-daemon-jvm.properties`), whose builds have no `jmods`, so `jlink` fails on it. `mnemo.desktop.application` points the JetBrains packaging tasks at a JDK 21 toolchain instead (lazily, so `:app` and F-Droid builds never need it). The runtime's modules are listed in that plugin; D8 trims and checks them. Compose Multiplatform artifacts use the catalog's `cmp-*` coordinates, not the deprecated `compose.runtime` accessors (`compose.desktop.currentOs` is the exception). Test with `runComposeUiTest` from `androidx.compose.ui.test.v2`.
- All dependencies and plugins live in `gradle/libs.versions.toml`. Compose artifacts take versions from the BOM, except `material-icons-extended`, which is no longer in the BOM.
- `settings.gradle.kts` uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, so repositories go there, never in module build files. Type-safe project accessors are on (`projects.core.designsystem`).
- Unit tests run on the JDK 25 toolchain. The Android convention adds the `--add-opens`/`--add-exports` flags Robolectric needs, and turns off `failOnNoDiscoveredTests` for modules with no tests yet. Robolectric runs on SDK 36 (`src/test/resources/robolectric.properties`) because it doesn't ship 37.

## Release engineering (release ROADMAP R2)

- `mnemo.versionCode` / `mnemo.versionName` live in `gradle.properties` (bump `versionCode` for every upload). Release signing is `ReleaseConfig.kt` in `build-logic`: it reads `MNEMO_KEYSTORE_FILE`, `MNEMO_KEYSTORE_PASSWORD`, `MNEMO_KEY_ALIAS` (and optional `MNEMO_KEY_PASSWORD`) from the environment or `local.properties`, and falls back to an unsigned build. Never commit a keystore or its passwords; the routine is `docs/release/signing.md`.
- The Open-source licenses screen is `LicensesScreen` (`:feature:settings`), fed by the raw resource that AboutLibraries generates in `:app` (`R.raw.aboutlibraries`, passed through `licensesScreen(librariesRes = …)` because features can't see `:app`'s `R`). Bundled files Gradle doesn't know about (fonts, KaTeX, py-fsrs) and the license texts are JSON in `app/config/{libraries,licenses}`: add a file there when you bundle something new, and to `NOTICE`. Don't add Google's `oss-licenses-plugin` or any Play Services/Firebase library (`LicensesFlowTest` fails on them).
- Every piece of AI output shows a Report button (`ReportAiButton`, `:core:ui/ai`): confirm dialog, then a prefilled GitHub issue in the browser. New AI surfaces need one. Links to the repo, issues and policy are `ProjectLinks` (`:core:model`).
- Ask for a permission with `PermissionRationaleDialog` (`:core:ui/permission`) first, unless the toggle the user just flipped already explains it.
- F-Droid (R7, `docs/release/fdroid.md`): its texts, changelog and graphics are `fastlane/metadata/android/en-US/` (`scripts/fdroid/fastlane.py` checks it, `--sync` copies the graphics from `docs/release/assets`; every release needs `changelogs/<versionCode>.txt`). The recipe for `fdroiddata` is `docs/release/fdroid/com.yahyafati.mnemo.yml`, kept in `fdroid rewritemeta`'s canonical form. F-Droid's scanner rejects the foojay JDK-download plugin (the recipe's `prebuild` removes it, so keep it a single line in `settings.gradle.kts`) and the APK dependency-metadata block (`dependenciesInfo.includeInApk = false`). `scripts/fdroid/check-foss-deps.py` runs in CI.
- Pre-launch QA (R3) is `docs/release/qa.md`: a runbook for the release build with a results log. `scripts/qa/device-checks.sh` wraps the `adb` parts (install-over, startup time, `gfxinfo` frame stats, airplane mode, `.apkg` push, mock AI server). Record results in the log, not in chat.
- `python3 scripts/check-16kb-alignment.py <aab|apk>` checks native libraries for 16 KB pages; CI (`.github/workflows/ci.yml`) runs it on the unsigned release build.

## Platform seams (desktop D3, ARCHITECTURE §4.3)

- Shared code (everything but `:app` and the `android` packages) must not import `android.*`, `androidx.work`, `androidx.activity`, `androidx.webkit`, `androidx.documentfile`, `androidx.core`, `LocalContext` or `AndroidView`. Put the API behind an interface and its Android implementation in a package named `android` (`AndroidXxx.kt`); D4–D6 turn those into `androidMain`. The check script's `PENDING` list holds the Android code that has not moved yet (Room, Keystore, WorkManager, the licenses screen's raw resource) and only shrinks: a listed file with no Android import fails too, so delete its entry.
- Files and folders: `AppDirectories` (`files`, `cache`, `media`, `secrets`, `restoreStaging`, `databaseFile`, `dataStoreFile`) and `DocumentAccess` (`info`, `openInput`, `openOutput`, `delete`, folder create/list, `keepAccess`) are in `:core:common`; failures are `IOException`. Never use `Context.filesDir` or a `ContentResolver` in shared code. Tests use `FakeDocumentAccess` (`:core:testing/platform`) and `AndroidAppDirectories(context)` under Robolectric.
- Transfers: the work is in `ImportJob`/`ExportJob`/`BackupJob` (`:core:data/job`), which throw and report progress through a callback; the `*Worker`s map exceptions with `toTransferError()`, and own the notification, retries and `WorkKeys`. Media cleanup and the optimizer already are plain (`MediaRepository.collectGarbage`, `FsrsOptimization.run`), so their workers just call them.
- `PdfTextExtractor` and `SpeechTranscriber` are interfaces (`:core:ingest`). `SmartExtractUiState.dictationAvailable` comes from `SourceRepository.isDictationAvailable()`; the Dictation source is hidden without it or without the `dictation` capability.
- UI: read `LocalPlatformCapabilities` (never `Build.VERSION`), `LocalAppVersion`, `LocalUriHandler`, `rememberIs24HourFormat()`. Pick files with `rememberFilePicker`/`rememberMediaPicker`/`rememberFileSaver`/`rememberFolderPicker` (they report a location string, and nothing on cancel). Ask for permissions with `rememberPermissionRequest(AppPermission.…)`: use `canRequest` to decide whether to explain first, `isGranted` for state. `MnemoApp`'s host (`MainActivity`) provides the Android values, and `MnemoTheme` must be inside `LocalPlatformCapabilities`.
- Card math is `MathText` → `LocalMathRenderer` (Android: `KatexMathRenderer`; unprovided: raw TeX), images are `MediaImage` → `LocalMediaImageLoader`, sound is `LocalCardAudio`. A new platform-specific renderer follows the same shape.
- Relative times use `RelativeAge` (`:feature:decks`), not `DateUtils`.

## Data and scheduling

- Layers follow ARCHITECTURE §2: Room (`:core:database`) and DataStore (`:core:datastore`) are only seen by `:core:data`, whose repositories (`*Repository` interfaces, `Offline*` implementations) map entities to `:core:model` types. Use cases live in `:core:domain`.
- `:core:scheduler` is a pure-Kotlin port of py-fsrs 6 with its own `Fsrs*` types (it may not depend on `:core:model`). `FsrsTest` holds the reference vectors; keep it in sync if the port changes. `StudyScheduler` (`:core:domain`) maps model cards to it and seeds fuzz per card, so previews equal saved answers.
- Room schema is exported to `core/database/schemas/` (currently v4). A schema change needs a version bump, a migration in `migration/Migrations.kt` (SQL must match the generated schema JSON) and a `MigrationTest` case (it runs under Robolectric in `testDebugUnitTest`). Never enable destructive migration.
- Rows use UUID ids, epoch-millis timestamps and soft deletes (`deletedAt`); every query filters `deletedAt IS NULL`. Undo is the exception: it hard-deletes the review log.
- Time goes through `Clock`; "today" is `StudyDay` (rolls over at 4 a.m. local, like Anki).
- The study loop is optimistic: `StudyViewModel` computes the answer, shows the next card, then saves through a `Mutex` so saves and undos stay in order.
- Card text is a Markdown subset plus Anki cloze, images (`![](media:<sha256>)`), math (`\(…\)`, `\[…\]`, `$$…$$`) and `[sound:…]`. The parser and HTML renderer are in `:core:model/markdown` (shared with `:core:anki`); `:core:ui/card` renders natively, or through a KaTeX WebView only for cards with math (ADR 0002, 0004). Cloze parsing is `Cloze` in `:core:model`.
- Media is content-addressed in `filesDir/media/<sha256>` (`MediaRepository`); `MediaEntity.id` is the hash, not a UUID. Garbage collection keeps anything younger than a day.
- Anki interop is ADR 0003. `:core:anki` is JVM-only: SQLite through the `androidx.sqlite` driver API (`AndroidSQLiteDriver` in the app, `BundledSQLiteDriver` in its tests) and zstd through `zstd-kmp`. Android-module Robolectric tests can't load zstd's Android natives, so `:core:data`'s build extracts the host's native from `zstd-kmp-jvm` onto the test library path.
- Long transfers (import, export, backup, media cleanup) are WorkManager workers in `:core:data/work`, registered with `workerOf` in `workModule`; `MnemoApplication` is the `Configuration.Provider` and installs `KoinWorkerFactory` (the default initializer is removed in the manifest). UIs observe them via `DataTransferRepository`. App tests replace the `WorkManager` definition with a test one (`testStorageModule`).
- Restore is applied at startup by `PendingRestore.applyIfPresent`, before Koin creates the database or DataStore (they are built on first use, after `startKoin`).

## Dependency injection (Koin 4, ADR 0010)

- Each module that has bindings exposes a public `xxxModule` (`commonModule`, `databaseModule`, `dataStoreModule`, `securityModule`, `dataModule` + `workModule`, `domainModule`, one per feature). `:core:data` lists the data-layer ones in `dataLayerModules`; `:app`'s `mnemoModules` (`di/AppModules.kt`) is the whole graph, and `MnemoApplication` starts it with `androidContext`. Classes carry no DI annotations: `factoryOf(::X) bind Y::class`, `viewModelOf(::XViewModel)`, `workerOf(::XWorker)`.
- Hilt's unscoped bindings are `factory`, `@Singleton` ones are `single` (database, DataStore, cipher, secret store, OkHttp client, PdfTextExtractor, WorkManager). Keep it that way unless a class needs sharing.
- A class that needs a dispatcher gets it in a lambda definition: `factory { X(get(), dispatcher(MnemoDispatchers.IO)) }` (`dispatcher` is in `:core:common/di`). `factoryOf` can't pass qualifiers.
- Screens get ViewModels with `koinViewModel()`; navigation arguments arrive in the `SavedStateHandle` as before. The widget provider is a `KoinComponent`.
- Later modules override earlier ones, which is how app tests swap the database, preferences, WorkManager and cipher: `TestMnemoApplication` starts `mnemoModules + testStorageModule`.
- `DependencyGraphTest` (`:app`) is the replacement for Hilt's compile-time check: `verify()` plus `checkModules`. When you add a ViewModel that reads a `SavedStateHandle` or a worker, add its class name to the lists in that test, or it fails with a missing parameter.

## Card types, reminders, widget (ADR 0008)

- `NoteKind` has Basic, Reversed, Cloze, TypeIn and MultipleChoice; built-in note types have fixed ids (`NoteType.BuiltIns`), inserted by the seed callback and by migrations. Every `when (kind)` must stay exhaustive.
- Multiple choice fields: question, answer, wrong answers (one per line). `CardSides.of(..., seed)` shuffles options with the card id; `StudyCard.sides` is cached. Typed answers are checked by `TypedAnswer`.
- `Note.hint` is a column, not a field. `updateNote` takes the hint explicitly: pass `note.hint` to keep it.
- Card audio goes through `LocalCardAudio` (`:core:ui/card/audio`); layout signals through `LocalWindowLayout` (`material3-adaptive`). Both are provided in `MainActivity`.
- The reminder is a self-rescheduling unique one-time work (`WorkManagerReminderRepository`, `ReminderWorker`); intents that open a tab use `AppIntents` (`:core:common`). The widget (`:app/widget`) is RemoteViews.
- Onboarding shows on first run with an empty collection. App tests start with it finished (`testStorageModule`); `FirstSessionTest` resets it.

## AI providers (ADR 0005)

- `:core:ai` (JVM) is the only network client: `OpenAiCompatibleClient` (OkHttp, SSE via `okhttp-sse`) and `ConnectionProbe` (test connection + capability detection). Tests use MockWebServer, which runs on plain-HTTP localhost, so their configs set `isLocal = true`.
- API keys are never in Room. `SecretStore` (`:core:security`) keeps them AES-GCM-encrypted with a Keystore key in `noBackupFilesDir/secrets`, so backups/exports can't contain them. Never add a key column, log a key, or put one in `SavedStateHandle`. Robolectric has no Keystore: tests use `SoftwareSecretCipher`, and app tests replace the `SecretCipher` definition (`testStorageModule`).
- Plain HTTP only for providers marked local whose host is a local address (`AiEndpoint.check`, enforced again in the client). The network security config can't express LAN ranges, so the rule lives in code. The OkHttp client never follows redirects.
- `AiProviderRepository.routeFor(task)` → the task's route or the default provider (first enabled one with a model), or null. Every AI entry point shows `AiSetupPrompt` (`:core:ui/ai`) when it's null, and `AiDisclosureDialog` before the first request to a provider.
- Presets and URL rules are in `:core:model` (`AiProviderPresets`, `AiEndpoint`) because the settings UI needs them and features can't see `:core:ai`.

## AI creation (ADR 0006)

- Smart Extract: `SourceRepository` (`:core:ingest`: PdfBox-Android, OkHttp + jsoup, `SpeechRecognizer`) fills one editable text box → `GenerateCardsUseCase` splits it (`TextChunker`, ≤ 1,200 words per request) and runs the parts in order through `CardGenerationRepository` → `CardGenerationClient` (`:core:ai`). Nothing is saved until `AcceptGeneratedCardsUseCase` (one transaction, `source = AI`).
- `:core:ingest` has no DI library: its classes are plain constructors built in `:core:data`'s `dataModule`, like the AI client. PdfBox's BouncyCastle dependency is excluded on purpose (see the ADR); its tests need `isIncludeAndroidResources` for PdfBox's assets.
- Output format: `GeneratedCardsSchema` (`{"cards": [{type, front, back, tags}]}`), sent as `response_format` only if the model has `jsonOutput`, and always spelled out in the prompt. `ChatTextRunner` drops a feature the server rejects (schema, `stream_options`, streaming) before any text arrived; auth/429 errors are never retried.
- `GeneratedCardParser` is incremental and tolerant; `JsonRepair` runs even on valid JSON (LaTeX like `\frac` is valid JSON for a form feed). New malformed-reply cases go in `core/ai/src/test/resources/replies` with an expected count in `GeneratedCardParserTest`.
- Only the source text and the queue's fronts are sent; the deck's notes are deduplicated locally (`GeneratedCardValidator.key`). Keep it that way: the disclosure text says so.
- Co-Author (`:feature:create/coauthor`, `CoAuthorRepository`) sends at most `CoAuthorDeck.MAX_NOTES_SENT` notes as plain text; "Find duplicates" is on-device (`DuplicateFinder`). Keep the disclosure string in sync with what is sent.
- Study-time AI lives in `StudyAssistViewModel` (separate from `StudyViewModel`) and only shows once the answer is revealed and a route exists. A rewrite of a cloze note must keep the same cloze numbers, or it can't be applied.

## Analytics and the optimizer (ADR 0007)

- Stats are SQL aggregates only (`StatsDao`, one row per day/deck/bucket), mapped by `StatsRepository` and combined in `ComputeRetentionStatsUseCase` (Analytics) and `GetRetentionOverviewUseCase` (Decks tiles, retention health). Don't load cards or reviews to compute a metric.
- SQLite has no `pow`: retrievability comes from per-deck buckets of `elapsed days / stability`, evaluated once per bucket with `Fsrs.retrievability(ratio, 1.0)`.
- Metric definitions (true retention, mature ≥ 21 days stability, time saved vs. daily review, leech ≥ 8 lapses) are in the ADR; keep the UI strings consistent with them.
- `FsrsOptimizer` (`:core:scheduler/optimizer`) ports py-fsrs 6.3.2's optimizer with hand-derived forward-mode gradients. If you change its math, rerun `core/scheduler/fixtures/make_optimizer_fixtures.py` (needs `pip install "fsrs[optimizer]==6.3.2"`) and `FsrsOptimizerTest`; `gradientMatchesFiniteDifferences` checks derivatives on their own.
- Fitted weights live in `UserSettings.fsrsWeights` (DataStore). `OptimizeFsrsWorker` → `FsrsOptimization` applies them only if they lower the loss. Always build FSRS parameters with `StudyScheduler.parameters(settings)` so the fitted weights are used.

## Tests

- Fakes for every repository are in `:core:testing/repository`. ViewModel tests build real use cases on top of them. Create ViewModels lazily or inside the test, after `MainDispatcherRule` has set `Dispatchers.Main`.
- In Android modules use `org.junit.Test` (`kotlin.test.Test` doesn't resolve there); `kotlin.test` assertions are fine.
- Compose UI tests run on Robolectric in `src/test` (features and `:app`), so the exit check covers them. App tests run on `TestMnemoApplication` (`@Config(application = …)`, also the default in `robolectric.properties`) with `testStorageModule` (in-memory DB, per-test DataStore file) and a phone-sized `@Config(qualifiers = …)`; Robolectric's default screen is too small for off-screen clicks.
- Known Robolectric limit: a text field inside a Compose `AlertDialog` never lets the test go idle. Test dialog logic through the ViewModel instead.

## UI

- Jetpack Compose + Material 3. Wrap screens in `MnemoTheme` (`:core:designsystem`, `theme/Theme.kt`). The brand palette from `docs/design/` is the default. Dynamic color is opt-in (`dynamicColor = true`).
- M3 slots cover colors, shapes, and most type. Extra tokens come from `MnemoTheme.typography` (`studyPrompt`, `metricLg`, `metricSm` …) and `MnemoTheme.spacing`. Fonts (Newsreader, Hanken Grotesk, JetBrains Mono) are bundled in `res/font/`.
- Launcher icon layers, the splash icon and `mnemo_logo` are generated from the logo by `docs/release/assets/generate_icon_drawables.py` (see `docs/release/assets/README.md`): edit the script and rerun it, not the XML. `MainActivity` launches with `Theme.Mnemo.Starting` (core-splashscreen); use `MnemoLogo` for the logo in Compose.
- Use `MnemoIcons`, not `Icons.*`, and the components in `core/designsystem/.../component/`. Keep the Roborazzi catalog test in sync when you add a component.
- **Insets**: the app runs edge-to-edge. On tab screens, `MnemoApp` shows `MnemoTopBar`/`MnemoNavigationBar`, which pad themselves for the system bars, and the Scaffold uses `contentWindowInsets = WindowInsets(0)`. Tab screens just fill the padding they are given. Non-tab screens (like Settings) get the whole window and own a `Scaffold` + `MnemoTopBar` with a back button. The shell hides its bars for them.
- Feature screens follow the Route/Screen split described in ARCHITECTURE §4.1: a stateless `*Screen` with `@Preview`, and a `navigation/*Navigation.kt` exposing `NavGraphBuilder.xxxScreen()` and `NavController.navigateToXxx()`.
