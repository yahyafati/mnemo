# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Mnemo is a single-module Android app (`:app`, package `com.yahyafati.mnemo`) that is still the Android Studio "Empty Compose Activity" template: `MainActivity` renders a `Greeting` inside `MnemoTheme`. There is no app architecture yet (no navigation, DI, persistence, or ViewModels). The repo uses git with a `main` branch.

## Commands

Use the Gradle wrapper from the repo root:

```bash
./gradlew assembleDebug                 # build debug APK
./gradlew installDebug                  # install on connected device/emulator
./gradlew testDebugUnitTest             # JVM unit tests (app/src/test)
./gradlew connectedDebugAndroidTest     # instrumented tests (app/src/androidTest), needs a device
./gradlew lint                          # Android lint

# single unit test class / method
./gradlew testDebugUnitTest --tests "com.yahyafati.mnemo.ExampleUnitTest"
./gradlew testDebugUnitTest --tests "com.yahyafati.mnemo.ExampleUnitTest.addition_isCorrect"

# single instrumented test class
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.yahyafati.mnemo.ExampleInstrumentedTest
```

The Gradle configuration cache is on (`gradle.properties`). `local.properties` holds the SDK path and is machine-specific.

## Build setup (non-obvious bits)

- **AGP 9.x with built-in Kotlin**: there is no `org.jetbrains.kotlin.android` plugin. Only `com.android.application` and `org.jetbrains.kotlin.plugin.compose` are applied. Don't add the Kotlin Android plugin back.
- The new AGP DSL is in use: `compileSdk { version = release(37) }`, and `buildTypes.release.optimization { enable = false }` instead of `isMinifyEnabled`.
- R8 keep rules go in `app/src/main/keepRules/*.keep`. AGP merges every file in that directory. There is no `proguard-rules.pro`.
- minSdk 29, target/compileSdk 37, Java 11 bytecode.
- Dependencies and plugins are declared only in the version catalog `gradle/libs.versions.toml`. Compose library versions come from the Compose BOM, so Compose artifacts are declared without versions.
- `settings.gradle.kts` uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, so repositories must be added there and not in module build files.

## UI

The UI is Jetpack Compose with Material 3. Wrap screens in `MnemoTheme` (`ui/theme/Theme.kt`). It uses dynamic color on Android 12+ and falls back to the static schemes built from `Color.kt`. The activity calls `enableEdgeToEdge()`, so content needs to respect the `Scaffold` inner padding and window insets.
