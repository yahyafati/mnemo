# Mnemo — Architecture

This document describes how the Mnemo codebase is organized: modules, layers, dependency rules, key data flows, and the folder structure. Read [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md) first for what the product is.

> **About `docs/design/`**: the HTML files there are **design references only**: static mockups of the Decks, Study, Create, and Analytics screens. They are not part of the app and are never bundled or loaded at runtime. Every screen is built natively in Jetpack Compose. The mockups are the visual source of truth for layout, and their tokens (colors, type, spacing, shapes) are ported into `:core:designsystem` (see §7).

---

## 1. Architectural goals

| Goal | How the architecture supports it |
|---|---|
| **Local first** | The Room database is the single source of truth. The UI only observes the database. The network (AI) is an optional side input that never owns state. |
| **Provider-agnostic AI** | One OpenAI-compatible client in `:core:ai`. Providers are rows in the database, not classes. |
| **Testable core** | Scheduling (FSRS), AI output parsing, and `.apkg` parsing are pure Kotlin/JVM modules with fast unit tests. |
| **Fast study loop** | The next cards are pre-computed in memory. Answering a card is an optimistic UI update followed by a database write off the main thread. |
| **Scales with features** | Feature modules are isolated. They depend on `core`, never on each other. |

## 2. Layers

Mnemo follows the official Android app architecture guide (UI → Domain → Data) with unidirectional data flow.

```
┌──────────────────────────────────────────────────────────┐
│ UI layer         :feature:*  +  :core:ui / :designsystem │
│   Composable screen ◄── UiState (StateFlow) ── ViewModel │
│   Composable screen ──── UiEvent / action ───► ViewModel │
└───────────────────────────────┬──────────────────────────┘
                                │ calls
┌───────────────────────────────▼──────────────────────────┐
│ Domain layer     :core:domain                            │
│   Use cases: BuildStudyQueue, AnswerCard, GenerateCards, │
│   ImportApkg, ComputeRetentionStats …                    │
└───────────────────────────────┬──────────────────────────┘
                                │ calls
┌───────────────────────────────▼──────────────────────────┐
│ Data layer       :core:data                              │
│   Repositories (interfaces + implementations)            │
│      │            │             │             │          │
│  :database    :datastore     :ai          :anki / :ingest│
│  (Room)       (prefs)        (HTTP)       (files)        │
└──────────────────────────────────────────────────────────┘
        pure Kotlin helpers: :core:model, :core:scheduler, :core:common
```

**Rules**

- **UI** holds no business logic. A ViewModel exposes one `StateFlow<XxxUiState>` and takes user actions as function calls (`onAction(StudyAction.Rate(Good))`).
- **Domain** use cases are small, single-purpose classes (`operator fun invoke`). They are only used where logic combines several repositories or is shared between features. A ViewModel may call a repository directly for simple reads.
- **Data**: repositories expose `Flow` for observable data and `suspend` functions for one-shot operations. They map between database entities / network DTOs and `:core:model` types. Entities and DTOs never leave the data layer.
- **Threading**: repositories are main-safe. They switch to an injected `@Dispatcher(IO)` or `Default` dispatcher internally. The UI never picks dispatchers.
- **Errors**: expected failures (network, parse, quota) are returned as a `sealed` result type from `:core:common`, not thrown. Only programmer errors are thrown.

## 3. Module map

```
                :app (Android)          :desktop
                          └──────┬──────┘
                              :shell
                          │
     ┌──────────┬─────────┼──────────┬───────────┬──────────┐
 :feature:  :feature:  :feature:  :feature:  :feature:  :feature:
  decks      study      create     browse    analytics  settings
     └──────────┴─────────┼──────────┴───────────┴──────────┘
                          │   (features never depend on each other)
            ┌─────────────┼──────────────┐
         :core:ui   :core:domain   :core:designsystem
                          │
                     :core:data
     ┌──────────┬─────────┼──────────┬──────────┬──────────┐
 :core:     :core:     :core:ai   :core:anki  :core:     :core:
 database   datastore      │          │       ingest     security
     └──────────┴─────────┼──────────┴──────────┘
                          │
      :core:model   :core:scheduler   :core:common      (pure JVM)
```

`:core:domain`, `:core:data`, `:core:database`, `:core:datastore`, `:core:ingest`, `:core:security`, `:core:testing` (D4), `:core:designsystem`, `:core:ui` (D5), the six features and `:shell` (D6) are Kotlin Multiplatform modules with two targets, Android and `desktop` (JVM): shared code in `commonMain`, the platform's in `androidMain` and `desktopMain` (ADR 0010). The pure JVM modules are used from `commonMain` as they are. `:app` and `:desktop` are the only launchers: thin, one per platform.

| Module | Type | Responsibility |
|---|---|---|
| `:app` | Android app | The Android launcher: `MainActivity` (splash, edge-to-edge, the platform's providers around `MnemoRoot`), `MnemoApplication`, Koin start-up (`di/AppModules.kt`), WorkManager setup, the home-screen widget, the licenses JSON as a raw resource |
| `:desktop` | JVM app | The desktop launcher (Windows, macOS, Linux; Compose Multiplatform, ADR 0010). `openCollection` takes the single-instance lock, applies a staged restore and starts Koin on the data layer (D4); `DesktopApp` puts `MnemoRoot` in a window with `ProvideDesktopPlatform`; `Main.kt` adds the window's size and place, menu bar and macOS application menu (D7), and the files the system opens with the app (D8: `OpenRequests` hands them from a second launch to the running one). `nativeDistributions` (installers, file associations, the trimmed runtime) is in `mnemo.desktop.application`. Builds the licenses JSON from its own classpath (`desktop/config` adds what only it bundles) |
| `:shell` | KMP lib | The app shell both launchers share (D6): `MnemoRoot` (theme, onboarding or the app), `MnemoApp` (top bar, bottom bar or rail), `MnemoNavHost` (composes each feature's graph), `TopLevelDestination`, onboarding, `MainViewModel` (appearance, first run, where to open), `shellModule`. The one module that depends on every feature |
| `:feature:decks` | KMP lib | Home/Decks screen, deck create/edit, deck detail |
| `:feature:study` | KMP lib | Study session: card flip, swipe gestures, rating bar, undo, session summary |
| `:feature:create` | KMP lib | Manual editor (all five card types, hints, image/audio attachments), AI Smart Extract, generated-card review queue, AI Co-Author (`coauthor/`) |
| `:feature:browse` | KMP lib | Card browser: search, filter, bulk edit/suspend/move |
| `:feature:analytics` | KMP lib | KPIs, forgetting curve, heatmap, forecast, leeches |
| `:feature:settings` | KMP lib | AI providers, scheduling options, study audio, reminders, appearance, backup/import/export, about and privacy policy |
| `:core:designsystem` | KMP lib | `MnemoTheme`, color/type/shape/spacing tokens, fonts, generic components (buttons, chips, stat tile, rating bar) |
| `:core:ui` | KMP lib | Shared composables that know the domain models: `DeckCard`, `CardFace` renderer (Markdown/LaTeX/cloze), charts |
| `:core:domain` | KMP lib | Use cases. No Android APIs |
| `:core:data` | KMP lib | Repository interfaces and implementations, the transfer jobs. `androidMain`: WorkManager workers and notifications, `AndroidAppDirectories`, `AndroidDocumentAccess`. `desktopMain`: `DesktopAppDirectories`, `DesktopDocumentAccess`, the coroutine job queue, `SingleInstanceLock` |
| `:core:database` | KMP lib | Room database, entities, DAOs, migrations (on Room's driver API, so one code base migrates both targets), schema JSON. Android builds it on the framework SQLite driver, the desktop on the bundled one |
| `:core:datastore` | KMP lib | Preferences DataStore for user settings |
| `:core:ai` | JVM lib | OpenAI-compatible HTTP client, SSE streaming, connection probe, prompt templates, JSON schema, tolerant incremental card parser (ADR 0006) |
| `:core:anki` | JVM lib | `.apkg`/`.colpkg` reader (all three Anki formats) and writer (zip + SQLite via the `androidx.sqlite` driver API, zstd), HTML ↔ Markdown, mapped to Mnemo models (ADR 0003). Depends on `:core:model` and `:core:scheduler` (FSRS replay) |
| `:core:ingest` | KMP lib | Source extraction: PDF → text (PdfBox-Android, Apache PDFBox on the desktop), URL → readable text (jsoup), speech → text (on-device `SpeechRecognizer`, Android only), chunking. No DI library: `:core:data` builds its classes |
| `:core:security` | KMP lib | Encryption for API keys (`SecretCipher`: Android Keystore, or on the desktop the OS keychain with a key-file fallback) and their store outside the database (`SecretStore`, ADR 0005) |
| `:core:scheduler` | JVM lib | FSRS algorithm (scheduling, retrievability, parameter optimizer) |
| `:core:model` | JVM lib | Plain domain types: `Deck`, `Note`, `Card`, `Rating`, `AiProvider`, …, plus the card Markdown parser and its HTML renderer (shared by `:core:ui` and `:core:anki`) |
| `:core:common` | JVM lib | `Result`/error types, dispatchers (`MnemoDispatchers`), time/clock abstraction, `commonModule` (Koin), and the platform seams of §4.3 (`AppDirectories`, `DocumentAccess`) |
| `:core:testing` | KMP lib | Fakes (repositories, clock), test dispatchers, `PlatformTest` (Robolectric on Android, plain JUnit on desktop), `inMemoryDatabase()`, `TestAppDirectories` |

**Dependency rules** (enforced in review, and later with a Gradle check):

1. `feature → core` only. **No `feature → feature` dependencies.** Features navigate to each other through route types declared in `:core:ui/navigation`.
2. `:core:model`, `:core:scheduler`, and `:core:common` depend on nothing but the Kotlin stdlib, coroutines, and kotlinx.serialization.
3. Only `:core:data` depends on `:core:database`, `:core:ai`, `:core:anki`, `:core:ingest`, and `:core:security`. Features never see Room or HTTP.
4. Only `:shell` knows every feature, and only the launchers (`:app`, `:desktop`) wire the dependency graph together.

## 4. Folder structure

```
mnemo/
├── CLAUDE.md
├── build.gradle.kts                  # root: plugin aliases (apply false)
├── settings.gradle.kts               # includeBuild("build-logic"), include(...) all modules, repositories
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml            # the only place versions/dependencies are declared
│   └── wrapper/
│
├── build-logic/                      # Gradle convention plugins (shared module config)
│   ├── settings.gradle.kts
│   └── convention/
│       ├── build.gradle.kts
│       └── src/main/kotlin/
│           ├── AndroidApplicationConventionPlugin.kt   # mnemo.android.application
│           ├── AndroidComposeConventionPlugin.kt       # mnemo.android.compose
│           ├── JvmLibraryConventionPlugin.kt           # mnemo.jvm.library
│           ├── KmpLibraryConventionPlugin.kt           # mnemo.kmp.library (Android + desktop targets)
│           ├── KmpComposeConventionPlugin.kt           # mnemo.kmp.compose (Compose Multiplatform)
│           ├── KmpFeatureConventionPlugin.kt           # mnemo.kmp.feature (lib+compose+koin+core deps; features and :shell)
│           ├── DesktopApplicationConventionPlugin.kt   # mnemo.desktop.application (:desktop)
│           └── com/yahyafati/mnemo/buildlogic/
│               ├── KotlinAndroid.kt                    # compileSdk 37, minSdk 29, Java 11
│               └── ProjectExtensions.kt                # `libs` accessor
│
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── keepRules/*.keep                         # R8 rules (merged by AGP)
│       │   ├── res/                                     # launcher icons, strings, themes.xml (splash)
│       │   └── java/com/yahyafati/mnemo/
│       │       ├── MnemoApplication.kt                  # starts Koin, WorkManager config
│       │       ├── MainActivity.kt                      # splash, edge-to-edge, setContent { MnemoRoot(…) }
│       │       └── widget/TodayWidget.kt                # home-screen widget (RemoteViews) + updater
│       ├── test/                                        # app tests on TestMnemoApplication (first session, navigation, DI graph)
│       └── androidTest/
│
├── shell/src/commonMain/kotlin/com/yahyafati/mnemo/shell/
│   ├── MnemoRoot.kt                                     # settings → theme → onboarding or MnemoApp
│   ├── MnemoApp.kt  MnemoAppState.kt                    # Scaffold + bottom bar (rail on wide windows) + NavHost
│   ├── MainViewModel.kt  ShellModule.kt                 # theme prefs, onboarding state, deep links
│   ├── navigation/ MnemoNavHost.kt TopLevelDestination.kt
│   └── onboarding/OnboardingScreen.kt                   # first run
│
├── desktop/
│   ├── config/                                          # licenses only the desktop bundles (the Java runtime)
│   ├── icons/                                           # PNG, ICO and ICNS for the installers and Anki package files (generate_desktop_icons.py)
│   └── src/main/kotlin/com/yahyafati/mnemo/desktop/     # Main.kt, DesktopApp.kt, Startup.kt (openCollection), DesktopModules.kt,
│                                                        # DesktopMenu.kt, WindowPlacement.kt, DesktopIntegration.kt, OpenRequests.kt
│
├── core/
│   ├── model/src/main/kotlin/com/yahyafati/mnemo/core/model/
│   │   ├── Deck.kt  Note.kt  NoteType.kt  Card.kt  CardState.kt
│   │   ├── Rating.kt  ReviewLog.kt  Media.kt  Transfer.kt
│   │   ├── Stats.kt  RetentionStats.kt                  # analytics aggregates and results (ADR 0007)
│   │   ├── markdown/ Markdown.kt MarkdownHtml.kt        # card Markdown (ADR 0002, 0004)
│   │   ├── AiProvider.kt  AiProviderPreset.kt  AiEndpoint.kt  GeneratedCard.kt
│   │   └── UserSettings.kt
│   │
│   ├── common/src/main/kotlin/com/yahyafati/mnemo/core/common/
│   │   ├── result/MnemoResult.kt  result/MnemoError.kt
│   │   ├── dispatchers/Dispatcher.kt                    # @Dispatcher(IO|Default) qualifier
│   │   └── time/Clock.kt
│   │
│   ├── scheduler/src/
│   │   ├── main/kotlin/com/yahyafati/mnemo/core/scheduler/
│   │   │   ├── Fsrs.kt                                  # next states for each rating
│   │   │   ├── FsrsParameters.kt
│   │   │   ├── Retrievability.kt
│   │   │   ├── SchedulingInfo.kt                        # interval preview for the rating buttons
│   │   │   └── optimizer/FsrsOptimizer.kt               # fits parameters from the ReviewLog (ADR 0007)
│   │   ├── fixtures/make_optimizer_fixtures.py          # py-fsrs reference data for the optimizer
│   │   └── test/                                        # reference-vector tests
│   │
│   ├── database/
│   │   ├── schemas/                                     # exported Room schema JSON (committed)
│   │   └── src/main/kotlin/com/yahyafati/mnemo/core/database/
│   │       ├── MnemoDatabase.kt
│   │       ├── entity/ DeckEntity.kt NoteEntity.kt NoteTypeEntity.kt CardEntity.kt
│   │       │           ReviewLogEntity.kt MediaEntity.kt AiEntities.kt
│   │       ├── dao/    DeckDao.kt NoteDao.kt CardDao.kt ReviewLogDao.kt
│   │       │           MediaDao.kt AiProviderDao.kt StatsDao.kt
│   │       ├── converter/ Converters.kt                 # Instant, lists, JSON maps
│   │       ├── migration/ Migrations.kt
│   │       └── di/DatabaseModule.kt
│   │
│   ├── datastore/src/main/kotlin/com/yahyafati/mnemo/core/datastore/
│   │   ├── UserPreferencesDataSource.kt                 # theme, dynamic color, reminders, limits
│   │   ├── UserPreferencesSerializer.kt
│   │   └── di/DataStoreModule.kt
│   │
│   ├── security/src/main/kotlin/com/yahyafati/mnemo/core/security/
│   │   ├── SecretCipher.kt                              # AES-GCM with a Keystore key
│   │   ├── SecretStore.kt                               # encrypted files in noBackupFilesDir
│   │   └── di/SecurityModule.kt
│   │
│   ├── ai/src/
│   │   ├── main/kotlin/com/yahyafati/mnemo/core/ai/
│   │   │   ├── client/OpenAiCompatibleClient.kt         # /models, /chat/completions (+SSE via okhttp-sse)
│   │   │   ├── client/ProviderConfig.kt                 # baseUrl, key, headers, timeout
│   │   │   ├── dto/ Chat.kt Models.kt                   # requests, responses, stream chunks
│   │   │   ├── probe/ConnectionProbe.kt                 # test connection + capability detection
│   │   │   ├── probe/ModelHeuristics.kt
│   │   │   ├── prompt/ CardGenerationPrompt.kt StudyAssistPrompt.kt
│   │   │   ├── schema/GeneratedCardsSchema.kt           # JSON schemas for response_format
│   │   │   ├── parse/ GeneratedCardParser.kt JsonRepair.kt ParsedCard.kt PlainTextCards.kt
│   │   │   └── generate/ ChatTextRunner.kt              # one request, streamed or not, with fallbacks
│   │   │                 CardGenerationClient.kt StudyAssistClient.kt
│   │   └── test/                                        # MockWebServer + resources/replies/ parser fixtures
│   │
│   ├── anki/src/
│   │   ├── main/kotlin/com/yahyafati/mnemo/core/anki/
│   │   │   ├── ApkgReader.kt  ApkgWriter.kt
│   │   │   ├── AnkiCollection.kt                        # legacy (anki2) and new (anki21b) schemas
│   │   │   └── mapper/AnkiMapper.kt                     # Anki notetypes/revlog → Mnemo models
│   │   └── test/resources/*.apkg                        # sample decks
│   │
│   ├── ingest/src/main/kotlin/com/yahyafati/mnemo/core/ingest/
│   │   ├── PdfTextExtractor.kt                          # PdfBox-Android text layer, no OCR
│   │   ├── WebPageExtractor.kt                          # a link: site extractor, else generic; PDF links
│   │   ├── PageFetcher.kt  SiteExtractor.kt             # the one GET (limits, User-Agent); a site's own rules (ADR 0012)
│   │   ├── GenericExtractor.kt  MarkdownText.kt         # jsoup page → Markdown
│   │   ├── site/WikipediaExtractor.kt  WikipediaArticle.kt  # REST API (Parsoid HTML) → article + sections
│   │   ├── SpeechTranscriber.kt                         # on-device SpeechRecognizer
│   │   └── TextChunker.kt                               # + TextCleanup
│   │
│   ├── data/src/main/kotlin/com/yahyafati/mnemo/core/data/
│   │   ├── repository/
│   │   │   ├── DeckRepository.kt          OfflineDeckRepository.kt
│   │   │   ├── CardRepository.kt          OfflineCardRepository.kt
│   │   │   ├── ReviewRepository.kt        OfflineReviewRepository.kt
│   │   │   ├── StatsRepository.kt         OfflineStatsRepository.kt
│   │   │   ├── AiProviderRepository.kt    DefaultAiProviderRepository.kt
│   │   │   ├── CardGenerationRepository.kt DefaultCardGenerationRepository.kt
│   │   │   ├── StudyAssistRepository.kt   SourceRepository.kt   ProviderConfigs.kt
│   │   │   ├── ImportExportRepository.kt  DefaultImportExportRepository.kt
│   │   │   ├── MediaRepository.kt         FileMediaRepository.kt
│   │   │   └── UserSettingsRepository.kt
│   │   ├── mapper/                                      # Entity/DTO ↔ model
│   │   ├── scheduling/FsrsOptimization.kt               # review log → optimizer → apply if better
│   │   ├── work/ TransferWorkers.kt ReminderWorker.kt OptimizeFsrsWorker.kt
│   │   └── di/DataModule.kt                             # repositories, AI/ingest clients, workers (Koin)
│   │
│   ├── domain/src/main/kotlin/com/yahyafati/mnemo/core/domain/
│   │   ├── BuildStudyQueueUseCase.kt
│   │   ├── AnswerCardUseCase.kt                         # scheduler + review log + card update
│   │   ├── UndoLastAnswerUseCase.kt
│   │   ├── GenerateCardsUseCase.kt                      # parts → AI → validate → dedupe (+ RegenerateCardUseCase)
│   │   ├── AcceptGeneratedCardsUseCase.kt
│   │   ├── GeneratedCardValidator.kt
│   │   ├── GetTodaySummaryUseCase.kt                    # due/new/learning, est. minutes
│   │   ├── ComputeRetentionStatsUseCase.kt              # everything on Analytics
│   │   ├── GetRetentionOverviewUseCase.kt               # Decks: retained, mastered, retention health
│   │   └── FindDuplicateNotesUseCase.kt                 # Co-Author duplicates, on device
│   │
│   ├── designsystem/src/
│   │   ├── commonMain/composeResources/                 # font/ (Newsreader, Hanken Grotesk, JetBrains Mono), drawable/mnemo_logo.xml, files/licenses
│   │   ├── androidMain/ desktopMain/                    # dynamic color, PlatformCapabilities for each platform
│   │   └── commonMain/kotlin/com/yahyafati/mnemo/core/designsystem/
│   │       ├── theme/ Theme.kt Color.kt Type.kt Shape.kt Spacing.kt
│   │       ├── icon/MnemoIcons.kt                       # Material Symbols mapping
│   │       └── component/ MnemoButton.kt MnemoChip.kt StatTile.kt
│   │                      RatingBar.kt ProgressHeader.kt StreakBadge.kt
│   │                      MnemoTopBar.kt MnemoNavigationBar.kt MnemoNavigationRail.kt EmptyState.kt
│   │
│   ├── ui/src/commonMain/kotlin/com/yahyafati/mnemo/core/ui/  # strings in commonMain/composeResources/values
│   │   ├── navigation/Routes.kt                         # @Serializable route types used across features
│   │   ├── card/ CardFace.kt CardInteractions.kt MarkdownText.kt MathText.kt MathPainter.kt
│   │   │         audio/CardAudio.kt                     # the interface; MediaPlayer + TextToSpeech in androidMain, javax.sound in desktopMain
│   │   ├── adaptive/WindowLayout.kt                     # material3-adaptive: wide, short, tabletop
│   │   ├── deck/DeckCard.kt
│   │   └── chart/ ForgettingCurveChart.kt ReviewHeatmap.kt ForecastBars.kt StackedBar.kt
│   │
│   └── testing/src/main/kotlin/com/yahyafati/mnemo/core/testing/
│       ├── repository/Fake*Repository.kt
│       └── MainDispatcherRule.kt  TestClock.kt
│
├── feature/
│   ├── decks/      (structure below)
│   ├── study/
│   ├── create/
│   ├── browse/
│   ├── analytics/
│   └── settings/
│
└── docs/
    ├── PROJECT_OVERVIEW.md
    ├── ARCHITECTURE.md
    ├── release/                                         # store listing, privacy policy, distribution options
    ├── design/                                          # HTML design references (not shipped)
    │   ├── decks.html
    │   ├── active-study-review.html
    │   ├── ai-card-creator.html
    │   └── analytics.html
    └── adr/                                             # architecture decision records
        ├── 0001-fsrs-as-scheduler.md
        ├── 0002-in-house-markdown-renderer.md
        ├── 0003-anki-interop.md
        ├── 0004-media-and-math.md
        ├── 0005-ai-providers-and-secrets.md
        ├── 0006-ai-card-creation.md
        ├── 0007-analytics-and-fsrs-optimizer.md
        ├── 0008-v1-polish.md
        ├── 0009-license-and-distribution.md
        └── 0010-desktop-with-compose-multiplatform.md
```

### 4.1 Feature module layout

Every feature module has the same shape. Example: `:feature:study`.

```
feature/study/
├── build.gradle.kts                    # plugins { id("mnemo.kmp.feature") }, compose.resources { packageOfResClass }
└── src/
    ├── commonMain/kotlin/com/yahyafati/mnemo/feature/study/
    │   ├── navigation/StudyNavigation.kt   # NavGraphBuilder.studyScreen(), NavController.navigateToStudy()
    │   ├── StudyRoute.kt                   # stateful: koinViewModel(), collectAsStateWithLifecycle()
    │   ├── StudyScreen.kt                  # stateless: (uiState, onAction) → UI, with @Previews
    │   ├── StudyViewModel.kt
    │   ├── StudyUiState.kt                 # sealed: Loading | Reviewing | Finished | Empty
    │   ├── StudyAction.kt                  # Flip, Rate(rating), Undo, Bury, Suspend, Star …
    │   └── component/ FlipCard.kt SwipeableCard.kt IntervalButtons.kt
    ├── commonMain/composeResources/values/strings.xml   # `stringResource(Res.string.x)`
    ├── commonTest/                         # ViewModel tests with fake repositories (run on both targets)
    └── androidHostTest/                    # Compose UI and screenshot tests on Robolectric, with their baselines
```

- The **Route / Screen split** keeps `*Screen` composables stateless, previewable, and easy to test.
- Each feature owns its own `navigation/` file and exposes only extension functions. `:shell` composes them.

### 4.2 Package naming

`com.yahyafati.mnemo.<layer>.<module>`, e.g. `com.yahyafati.mnemo.core.database`, `com.yahyafati.mnemo.feature.create`. Android namespaces match.

### 4.3 Platform seams

The desktop app (docs/desktop/ROADMAP.md) shares the domain, data and UI code with Android, so shared code never calls an Android API. Each one sits behind an interface, and its Android implementation lives in a package named `android`, next to the interface's module (`.../android/AndroidXxx.kt`). In a multiplatform module (D4 on) the Android implementations are in `androidMain` and the desktop ones beside them in `desktopMain` (`.../desktop/DesktopXxx.kt`); where a Koin module needs the platform's part it calls an `expect` function (`createDatabase()`, `createSecretCipher()`) or an `expect val` (`dataLayerModules`). `scripts/desktop/check-android-imports.py` (CI) fails on an Android import anywhere else, apart from a short, shrinking list of files that have not moved yet.

| Seam | Interface (module) | Android implementation | Desktop |
|---|---|---|---|
| Where files live | `AppDirectories` (`:core:common`) | `AndroidAppDirectories` (`:core:data`) | `DesktopAppDirectories`: `~/Library/Application Support/Mnemo`, `%LOCALAPPDATA%\Mnemo`, `$XDG_DATA_HOME/mnemo` (`MNEMO_DATA_DIR` overrides) |
| The user's picked files | `DocumentAccess` (`:core:common`) | `AndroidDocumentAccess`: content URIs, document trees | `DesktopDocumentAccess`: paths and `file:` URIs |
| Long transfers | `ImportJob`, `ExportJob`, `BackupJob` (`:core:data/job`): plain suspend functions with a progress callback | `ImportWorker`, `ExportWorker`, `BackupWorker` only adapt them to WorkManager (input, foreground notification, retry) | `TransferQueue`s in an application scope (`DesktopDataTransferRepository`, `DesktopFsrsOptimizationRepository`); media cleanup and the automatic backup are checked at start and then hourly |
| PDF text | `PdfTextExtractor` (`:core:ingest`) | `PdfBoxAndroidTextExtractor` | `PdfBoxTextExtractor` (Apache PDFBox 3) |
| Dictation | `SpeechTranscriber` (`:core:ingest`), with `isAvailable()` | `AndroidSpeechTranscriber` | `NoSpeechTranscriber` (not available) |
| What the platform offers | `PlatformCapabilities` + `LocalPlatformCapabilities` (`:core:designsystem`): dynamic color, reminders, widget, dictation, text-to-speech, run-time permissions | `androidPlatformCapabilities()` | most are off |
| File dialogs | `rememberFilePicker`, `rememberMediaPicker`, `rememberFileSaver`, `rememberFolderPicker` (`:core:ui/files`) | Storage Access Framework contracts | AWT `FileDialog` (Swing `JFileChooser` for a folder off macOS) |
| Run-time permissions | `rememberPermissionRequest(AppPermission)` (`:core:ui/permission`) | `ActivityResultContracts.RequestPermission` | always granted (`runtimePermissions` is off) |
| API key encryption | `SecretCipher` (`:core:security`) | `KeystoreSecretCipher` (AES-GCM, key in the Keystore) | `DesktopSecretCipher`: AES-GCM, key in the OS keychain, or in `secrets.key` (owner-only) if there is none |
| Room's database | `MnemoDatabase` (`:core:database`) | `MnemoDatabase.build(context)` on `AndroidSQLiteDriver` | `MnemoDatabase.build(file)` on `BundledSQLiteDriver` |
| Restarting for a restore | `DataTransferRepository.restartToRestore()` | `restartAndroidApp` | `AppRestarter` (`ProcessAppRestarter`); the new process waits for the lock |
| Math on cards | `MathRenderer` + `LocalMathRenderer` (`:core:ui/card/web`); `MathText` calls it | `KatexMathRenderer` (KaTeX in a WebView) | `JLaTeXMathRenderer`: `MarkdownText` lays out the text and `JLaTeXMathPainter` draws each formula to a bitmap; TeX it cannot parse shows as source |
| Card images | `MediaImageLoader` + `LocalMediaImageLoader` (`:core:ui/card`) | `AndroidMediaImageLoader` (`BitmapFactory`, LRU cache) | `DesktopMediaImageLoader` (Skia, LRU cache, 2048 px cap) |
| Card sound | `CardAudio` + `LocalCardAudio` | `AndroidCardAudio` (`MediaPlayer`, `TextToSpeech`) | `DesktopCardAudio` (`javax.sound`, mp3spi, vorbisspi; no text-to-speech) |

Shared UI reads what it needs from these instead of the operating system: `LocalPlatformCapabilities` for "does this platform have reminders?", `LocalAppVersion` for the version name, `LocalUriHandler` for opening links, `rememberIs24HourFormat()` for the clock format. The launchers provide the platform's values around `MnemoRoot` (`MainActivity` on Android; `ProvideDesktopPlatform` in `:core:ui/desktopMain` on the desktop: capabilities, window layout, image loader, math renderer, audio); unprovided (previews, tests) the capabilities are all on and the renderers and loaders do nothing or fall back (raw TeX, alt text).

### 4.4 The desktop experience

What makes the desktop app a desktop app sits in shared modules, gated by `PlatformCapabilities.keyboardAndMouse`, with the window itself in `:desktop` (ROADMAP D7, ADR 0010 "Findings from D7"):

- **Keyboard.** `Shortcuts` (`:core:ui/keyboard`) is the one table of key combinations; screens handle them with `Modifier.shortcuts`, the menu bar shows them, and `ShortcutsDialog` (`:shell`) lists them. Global ones, and everything a menu item or a dropped file asks for, are `AppCommand`s sent to `AppCommands` and carried out by `MnemoRoot`; the shell keeps the keyboard focus so they work on every screen.
- **Mouse.** `MnemoIconButton` (tooltips), `clickCursor()`, `ContextMenuHost` (right-click menus), `ScrollbarBox` (scrollbars) and `Modifier.fileDropTarget` are `expect` functions or modifiers in `:core:ui` and `:core:designsystem`; on Android they add nothing.
- **Window.** `:desktop` keeps the window's size, place and maximized state (`window.properties`), sets the minimum size and icon, builds the menu bar, and installs the macOS application menu handlers. The body of a wide window stops growing at `LocalMaxContentWidth` (`readingWidth()`).

## 5. Key flows

### 5.1 Study session

```
StudyViewModel
  └─ BuildStudyQueueUseCase(deckIds)            ── CardRepository.observeDue(now, limits)
        → in-memory queue (learning → review → new, interleaved)
  on Rate(Good):
  1. UI advances immediately to the next card      (optimistic, no await)
  2. AnswerCardUseCase(card, Good, elapsedMs)
        ├─ Fsrs.next(card.memoryState, Good, now) → new stability/difficulty/due
        └─ ReviewRepository.recordAnswer(...)     ── one Room @Transaction:
                                                       update Card + insert ReviewLog
  3. Undo keeps the previous Card snapshot and deletes the ReviewLog row
```

The interval labels on the rating buttons ("< 1m", "12h", "2d", "5d") come from `SchedulingInfo`. They are computed for the current card when it is shown.

### 5.2 AI card generation

```
SmartExtractViewModel
  1. SourceRepository (:core:ingest) → text in the editable text box (a link as Markdown; PDF / link / dictation / paste)
  2. AiProviderRepository effective route for AiTask.Extract → provider + model + capabilities
  3. GenerateCardsUseCase(route, ExtractRequest(parts, options, deckId))
       parts = TextChunker (≤ 1,200 words each), one request per part, in order
  4. CardGenerationRepository → ProviderConfigs (decrypted key) → CardGenerationClient (:core:ai)
       POST {baseUrl}/chat/completions: streamed if supported, response_format = json_schema if
       supported, format always in the prompt too; a rejected feature is dropped and retried
  5. Text deltas    → GeneratedCardParser (incremental) → each card as soon as its object closes;
                       no readable card → one repair request
  6. Validation     → GeneratedCardValidator (fields, cloze syntax), dedupe against the deck (locally)
                       and the queue
  7. UI             → review queue (accept / edit / regenerate / discard / Accept All)
  8. AcceptGeneratedCardsUseCase → notes (source = AI) + cards in one transaction
```

Nothing touches the database before step 8 (except the token-usage log). If the network fails partway, the cards already received stay in the review queue and Retry resumes at the failed part. Study-time AI (Explain / Example / Rewrite) goes through `StudyAssistRepository` the same way; a rewrite is a proposal that updates the note's fields only when applied. See ADR 0006.

Books (EPUB, ADR 0011) enter before step 1: `SourceRepository.readBook` → `EpubReader` gives chapters with their text; `CreateBookDecksUseCase` makes the empty `Book::NN Chapter` decks (`BookDeckNames`); a chapter's text then fills the same text box (Smart Extract's Epub source, or "Generate cards" on the import result through the in-memory `BookHandoff`) and goes through steps 2–8 into that chapter's deck, found by computing its name. Sizes use `WordCount`, which counts Japanese and Chinese by character. A book run ("Create decks and generate", B6) is the same path for several chapters in book order: the import hands over a batch offer, and Smart Extract runs one chapter at a time, moving on only when the user does. Nothing about the book is stored.

### 5.3 AI provider management

- API keys are **not** in the database (ADR 0005). `SecretStore` keeps each one AES-GCM-encrypted with a non-exportable Keystore key in `noBackupFilesDir/secrets`, bound to its provider id. A key is decrypted only to build one request's `ProviderConfig`. `AiProvider` only says whether a key exists.
- "Test connection" (`ConnectionProbe`) calls `GET /models`, then a streamed one-token completion and a one-token `json_schema` completion. Saving the provider stores the listed models (`AiModelEntity`) and the tested model's capability flags; the user can override them.
- `AiTaskRouteEntity` maps each `AiTask` (Extract, CoAuthor, Explain, Rewrite) to a provider and optional model. A missing or disabled route falls back to the default provider: the first enabled one with a model. `routeFor` returns null when nothing is usable, and AI entry points show `AiSetupPrompt`.
- Backups, exports and Android Auto Backup **cannot** contain API keys: they live outside every backed-up location.
- `ai_usage` logs reported tokens per provider and task, locally.
- `ai_answers` keeps the last complete Explain and Example answer per note (ADR 0006), so they are asked for once and regenerated on demand.

### 5.4 Import / backup

- `.apkg` import is `ImportJob` (run by `ImportWorker` on Android, with a foreground notification for large decks). It copies the picked file locally, opens it with `:core:anki` (`ApkgReader`), stores media first into `filesDir/media/<hash>`, then maps notes in batches of 500 (`AnkiImportMapper`) and writes each batch with its cards and review logs in one transaction (`AnkiImporter`). Duplicates are skipped by guid. See ADR 0003.
- Export (`ExportJob`): `.apkg` through `AnkiExporter` + `AnkiPackageWriter`, or the whole collection as JSON (`JsonExporter`), streamed page by page to a SAF document.
- Backup (`BackupJob`, manual or daily): a zip with a manifest, a checkpointed database copy (`DatabaseSnapshot`), preferences and media. Restore stages the zip and restarts; `MnemoApplication` applies it (`PendingRestore`) before the database opens. See ADR 0004.
- The UI observes all of these through `DataTransferRepository` as `TransferState`s mapped from WorkManager.

## 6. Data layer details

- **Room** with KSP, on its multiplatform driver API (`SQLiteConnection`, `RoomRawQuery`, `useWriterConnection { immediateTransaction { … } }`): the same entities, DAOs and migrations build the Android database (framework SQLite driver) and the desktop one (bundled SQLite driver, same file format). Schemas are exported to `core/database/schemas/` and committed. Every schema change gets a migration, a `MigrationTest` case (it runs on both targets) and a fixture database of the new version in `core/database/src/commonTest/resources/fixtures`. Destructive migration is never allowed.
- **IDs** are UUID strings. Each row has `createdAt`/`updatedAt` and a `deletedAt` soft-delete column. Nothing is needed for sync today, but this keeps it possible later.
- **Indices** on `Card(due, state, deckId)`, `ReviewLog(cardId, reviewedAt)`, and `Note(deckId)` keep the queue and stats queries fast.
- **Stats** are computed with SQL aggregates in `StatsDao`, not by loading rows into memory. Retrievability, which needs `pow`, comes from per-deck buckets of `elapsed days / stability` (ADR 0007).
- **Media** is content-addressed (`sha256`) and garbage-collected by a worker when no note references it.

## 7. Design system (from `docs/design/`)

The HTML mockups define the look. These tokens are ported into `:core:designsystem`:

| Token group | Values from the mockups | Compose destination |
|---|---|---|
| **Color** (light) | primary `#3525CD`, primary-container `#4F46E5`, secondary `#006C49`, secondary-container `#6CF8BB`, tertiary `#684000`, surface/background `#FFF8F3`, surface-container `#F3EDE7`, on-surface `#1D1B18`, outline `#777587`, error `#BA1A1A` (full M3 role set in the mockups' Tailwind config) | `Color.kt` → `lightColorScheme(...)` |
| **Color** (dark) | Not in the mockups. Generate from seed `#4F46E5` with Material Theme Builder. | `darkColorScheme(...)` |
| **Typography** | Newsreader (display, headlines, study prompt), Hanken Grotesk (body, labels), JetBrains Mono (metrics, counters) | `Type.kt` + custom `MnemoTypography` extras (`studyPrompt`, `metricLg`, `metricSm`) |
| **Type scale** | display-hero 48/56, headline-lg 32/40, headline-md 24/32, headline-sm 20/28, study-prompt 28/38 (22/30 on mobile), body-lg 18/28, body-md 16/24, body-sm 14/20, label-md 14/18 w600, label-sm 12/16 w500, metric-lg 18/22 w500, metric-sm 12/16 | `Typography(...)` |
| **Spacing** | xs 4, sm 8, md 16, lg 24, xl 40; mobile margin 20, gutter 16 | `Spacing.kt` exposed via `CompositionLocal` |
| **Shape** | small 2dp, lg 4dp, xl 8dp, full 12dp | `Shape.kt` → `Shapes(...)` |
| **Icons** | Material Symbols Outlined | `MnemoIcons.kt` (vector assets / `material-icons-extended`) |

Theme decisions:

- **The brand palette is the default.** Material You dynamic color is an opt-in setting in Appearance. This changes the current template, which enables dynamic color by default.
- Fonts are bundled in `res/font/` so the app works offline. No downloadable Google Fonts.
- Navigation matches the mockups' bottom bar: **Decks · Study · Create · Analytics**. Settings is reached from the top-bar avatar.

## 8. Technology stack

| Concern | Library |
|---|---|
| UI | Jetpack Compose (BOM), Material 3, `material3-adaptive` for tablets |
| Navigation | Navigation Compose with type-safe `@Serializable` routes |
| DI | Koin (`koin-android`, `koin-compose-viewmodel`, `koin-androidx-workmanager`); each module exposes a `xxxModule` (ADR 0010) |
| Async | Kotlin Coroutines + Flow |
| Database | Room (KSP) |
| Preferences | DataStore |
| Networking | OkHttp + kotlinx.serialization (SSE via `okhttp-sse`) |
| Background | WorkManager |
| Serialization | kotlinx.serialization (DTOs, routes, JSON fields) |
| Markdown / math | A Compose Markdown renderer, plus KaTeX/MathJax in a lightweight WebView only for cards that contain math |
| PDF | Android `PdfRenderer` for previews; a PDF text-extraction library (e.g. PdfBox-Android) for text |
| Charts | Custom Compose `Canvas` charts (forgetting curve, heatmap), with no heavy chart dependency |
| Testing | JUnit, Turbine, kotlinx-coroutines-test, OkHttp MockWebServer, Room `MigrationTestHelper`, Compose UI test, Roborazzi (screenshot tests against the design) |

Build constraints (from `CLAUDE.md`): AGP 9 with **built-in Kotlin** (no `org.jetbrains.kotlin.android` plugin), everything declared in `gradle/libs.versions.toml`, repositories only in `settings.gradle.kts`, and R8 rules in `src/main/keepRules/`. Pure JVM modules apply `org.jetbrains.kotlin.jvm`. KSP must be added to the catalog for Room and Hilt.

## 9. Testing strategy

| Level | Target | Where |
|---|---|---|
| Unit (JVM) | FSRS math, AI output parser, prompt building, `.apkg` mapping, use cases | `core/*/src/test`; in multiplatform modules `src/commonTest`, which runs on both targets (`testAndroidHostTest`, `desktopTest`) |
| ViewModel | State transitions using `:core:testing` fakes and Turbine | `feature/*/src/commonTest` (both targets) |
| Integration | DAOs, migrations and repositories on Room (in memory, or a file); the transfer, backup and restore flows against real Anki packages and fixture backups made by each platform; AI client against MockWebServer; the desktop collection flow (`DesktopCollectionTest`) | `core/database`, `core/data` `src/commonTest`, `desktop/src/test`, `core/ai/src/test` |
| UI | Stateless `*Screen` composables; screenshot tests compared with the design | `feature/*/src/androidHostTest` (Robolectric, Roborazzi baselines next to them), `shell`; on the desktop `runComposeUiTest` in `desktop/src/test` (`DesktopAppTest`) |
| End-to-end | Onboarding → create deck → study → stats | `app/src/test` (`FirstSessionTest`, Robolectric), `desktop/src/test` (`DesktopAppTest`) |

## 10. Security and privacy

- API keys: encrypted with a non-exportable Android Keystore AES key and decrypted only in memory for each request. `ProviderConfig.toString()` redacts them, and error messages have them removed.
- Network: HTTPS required, except user-marked local providers whose host is a local address (LAN IPs, `localhost`, `.local`). A network security config can't express address ranges, so the rule is enforced in code before every request (`AiEndpoint`); `network_security_config.xml` pins HTTPS for the hosted presets and trusts only system CAs. Redirects are never followed (ADR 0005).
- No analytics or crash-reporting SDKs. Logs never include card content or keys.
- Encrypted keys live in `noBackupFilesDir`, which Android never backs up; the Keystore key does not survive a restore anyway. On the desktop they are in a `secrets` directory that no backup or export reads, and the AES key is in the OS keychain (Windows Credential Manager, macOS Keychain, Secret Service), or, where there is none, in an owner-only `secrets.key` file: Settings says which (D6).

## 11. Migration from the current template

1. Add `build-logic/` convention plugins and KSP, Hilt, Room, and Navigation to the version catalog.
2. Create `:core:designsystem` and move `ui/theme/*` there, updated with the tokens from §7.
3. Create `:core:model`, `:core:common`, `:core:scheduler`, `:core:database`, and `:core:data`.
4. Add `:feature:decks` and `:feature:study`, and replace `Greeting` in `MainActivity` with `MnemoApp()`.
5. Add the remaining modules milestone by milestone (see the roadmap in `PROJECT_OVERVIEW.md`).
