# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Mnemo is a local-first spaced-repetition Android app (package `com.yahyafati.mnemo`). Read `docs/PROJECT_OVERVIEW.md` (product), `docs/ARCHITECTURE.md` (modules, layers, rules) and `docs/ROADMAP.md` (phases). Decisions are recorded in `docs/adr/`.

**Phase 0 (Foundation) is done**: multi-module skeleton, design system, and the navigation shell. The four tabs (Decks · Study · Create · Analytics) and Settings are placeholder screens. There is no persistence, scheduler, or ViewModel yet; that is Phase 1.

Modules today: `:app`, `:core:{common,designsystem,model,testing,ui}`, `:feature:{analytics,create,decks,settings,study}`. Add new ones to `settings.gradle.kts`.

## Commands

Use the Gradle wrapper from the repo root:

```bash
./gradlew assembleDebug testDebugUnitTest lint   # the phase exit check
./gradlew installDebug                           # install on connected device/emulator
./gradlew connectedDebugAndroidTest              # instrumented tests, needs a device

# single unit test class / method
./gradlew :app:testDebugUnitTest --tests "com.yahyafati.mnemo.ui.MnemoAppNavigationTest"
./gradlew :core:common:test --tests "com.yahyafati.mnemo.core.common.result.MnemoResultTest"

# design-system screenshot baseline (Roborazzi, committed in core/designsystem/src/test/screenshots)
./gradlew :core:designsystem:verifyRoborazziDebug
./gradlew :core:designsystem:recordRoborazziDebug   # after an intended visual change
```

JVM modules (`:core:model`, `:core:common`) use `test`, not `testDebugUnitTest`. The Gradle configuration cache is on. `local.properties` is machine-specific.

## Build setup (non-obvious bits)

- **Convention plugins** in `build-logic/` (included build) hold all shared config: `mnemo.android.application`, `.library`, `.compose`, `.feature`, `.room`, `mnemo.hilt`, `mnemo.jvm.library`. Module build files should be a few lines; put shared settings in the plugins (`build-logic/convention/src/main/kotlin`).
- Library namespaces are derived from the module path (`:core:designsystem` → `com.yahyafati.mnemo.core.designsystem`). Don't set `namespace` in library modules.
- `mnemo.android.feature` = library + Compose + Hilt + serialization + `:core:designsystem` + `:core:ui`. Features must never depend on other features. They navigate through the `@Serializable` routes in `:core:ui/navigation/Routes.kt`.
- **AGP 9.x with built-in Kotlin**: there is no `org.jetbrains.kotlin.android` plugin. Don't add it. Pure JVM modules use `org.jetbrains.kotlin.jvm` (via `mnemo.jvm.library`).
- Kotlin is pinned to 2.3.21 (KSP 2.3.x, Hilt 2.60.x). Lint suggests 2.4.x. Upgrade Kotlin, KSP and Hilt together and rerun the build.
- The new AGP DSL is in use: `compileSdk { version = release(37) }`, and `buildTypes.release.optimization { enable = false }` instead of `isMinifyEnabled`.
- R8 keep rules go in `app/src/main/keepRules/*.keep`. There is no `proguard-rules.pro`.
- minSdk 29, target/compileSdk 37, Java 11 bytecode.
- All dependencies and plugins live in `gradle/libs.versions.toml`. Compose artifacts take versions from the BOM, except `material-icons-extended`, which is no longer in the BOM.
- `settings.gradle.kts` uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, so repositories go there, never in module build files. Type-safe project accessors are on (`projects.core.designsystem`).
- Unit tests run on the JDK 25 toolchain. The Android convention adds the `--add-opens`/`--add-exports` flags Robolectric needs, and turns off `failOnNoDiscoveredTests` for modules with no tests yet. Robolectric runs on SDK 36 (`src/test/resources/robolectric.properties`) because it doesn't ship 37.

## UI

- Jetpack Compose + Material 3. Wrap screens in `MnemoTheme` (`:core:designsystem`, `theme/Theme.kt`). The brand palette from `docs/design/` is the default. Dynamic color is opt-in (`dynamicColor = true`).
- M3 slots cover colors, shapes, and most type. Extra tokens come from `MnemoTheme.typography` (`studyPrompt`, `metricLg`, `metricSm` …) and `MnemoTheme.spacing`. Fonts (Newsreader, Hanken Grotesk, JetBrains Mono) are bundled in `res/font/`.
- Use `MnemoIcons`, not `Icons.*`, and the components in `core/designsystem/.../component/`. Keep the Roborazzi catalog test in sync when you add a component.
- **Insets**: the app runs edge-to-edge. On tab screens, `MnemoApp` shows `MnemoTopBar`/`MnemoNavigationBar`, which pad themselves for the system bars, and the Scaffold uses `contentWindowInsets = WindowInsets(0)`. Tab screens just fill the padding they are given. Non-tab screens (like Settings) get the whole window and own a `Scaffold` + `MnemoTopBar` with a back button. The shell hides its bars for them.
- Feature screens follow the Route/Screen split described in ARCHITECTURE §4.1: a stateless `*Screen` with `@Preview`, and a `navigation/*Navigation.kt` exposing `NavGraphBuilder.xxxScreen()` and `NavController.navigateToXxx()`.
