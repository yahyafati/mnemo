# Contributing to Mnemo

Thanks for your interest in Mnemo. Bug reports, ideas, documentation fixes and code are all welcome.

By contributing you agree that your work is released under the project's license,
[GPL-3.0-or-later](LICENSE).

## Reporting bugs and suggesting features

Open an issue at <https://github.com/yahyafati/mnemo/issues>. Search first to avoid duplicates.

For a bug, include the app version, Android version and device, the steps to reproduce, and what
you expected to happen. Attach a screenshot if it is a visual problem.

**Never paste an API key, a keystore or its passwords** into an issue, a log or a pull request.

For a security problem, or anything that involves your private data, don't open a public issue.
Use the contact address in the [privacy policy](docs/release/privacy-policy.md).

For a larger feature, open an issue to discuss it before writing code, so you don't spend time on
something that doesn't fit the [roadmap](docs/ROADMAP.md).

## Getting set up

You need Android Studio (or the Android SDK) and a JDK 21 for Gradle. Gradle downloads the JDK 25
toolchain the tests run on. `local.properties` (your SDK path) is machine-specific and ignored by
git.

```bash
git clone https://github.com/yahyafati/mnemo.git
cd mnemo
./gradlew assembleDebug testDebugUnitTest lint   # what CI runs
./gradlew installDebug                           # on a connected device or emulator
```

Read these before a non-trivial change:

- [docs/PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md): the product and its principles
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): modules, layers and dependency rules
- [docs/adr/](docs/adr/): why things are the way they are
- [CLAUDE.md](CLAUDE.md): a dense list of the project's conventions and non-obvious build details
  (written for an AI assistant, but useful to anyone)

## Making a change

1. Fork the repository and create a branch from `main`.
2. Make a focused change. One topic per pull request is easier to review.
3. Run the checks below.
4. Open a pull request that says what changed and why. Link the issue it closes.

### Checks

Every pull request must pass CI:

```bash
./gradlew assembleDebug testDebugUnitTest lint
./gradlew :core:ai:test :core:anki:test :core:model:test :core:scheduler:test :core:common:test
```

Desktop changes (`desktop/`) are checked with `./gradlew :desktop:test`; CI runs it on Linux, Windows
and macOS. `./gradlew :desktop:run` opens the window (see
[docs/desktop/ROADMAP.md](docs/desktop/ROADMAP.md)).

Run a single test with, for example,
`./gradlew :app:testDebugUnitTest --tests "com.yahyafati.mnemo.ui.MnemoAppNavigationTest"`.

If you change how a screen looks, verify and update the Roborazzi screenshot baselines:

```bash
./gradlew verifyRoborazziDebug
./gradlew recordRoborazziDebug   # only after an intended visual change; compare with docs/design/
```

Commit the updated images together with the change.

## Conventions

The full list is in [CLAUDE.md](CLAUDE.md) and the architecture doc. The ones that trip people up:

**Architecture**
- Layers are UI → Domain → Data. Room and DataStore are only visible to `:core:data`; entities never
  leave it. Repositories map to `:core:model` types.
- **Feature modules never depend on other features.** They navigate through the routes in
  `:core:ui/navigation/Routes.kt`.
- Screens follow the Route/Screen split: a stateless `*Screen` with a `@Preview`, and a
  `navigation/*Navigation.kt` file exposing `NavGraphBuilder.xxxScreen()` and
  `NavController.navigateToXxx()`.
- Keep `:core:model` classes immutable (`val`, read-only collections).
- Use `Clock` for time, and `StudyDay` for "today".

**Build**
- All dependencies and plugins go in `gradle/libs.versions.toml`. Repositories go in
  `settings.gradle.kts`, never in a module.
- Shared Gradle configuration goes in the convention plugins in `build-logic/`, so module build
  files stay a few lines. Don't set `namespace` in library modules.
- Don't add the `org.jetbrains.kotlin.android` plugin (AGP 9 has built-in Kotlin). Upgrade Kotlin,
  and KSP together.

**Database**
- A schema change needs a version bump, a migration in `migration/Migrations.kt` whose SQL matches
  the exported schema JSON, and a `MigrationTest` case. Destructive migration is never allowed.
- Rows use UUID ids and soft deletes: every query filters `deletedAt IS NULL`.

**UI**
- Jetpack Compose and Material 3. Use `MnemoTheme`, `MnemoIcons` (not `Icons.*`) and the
  components in `:core:designsystem`. When you add a component, add it to the catalog screenshot
  test.
- The app is edge-to-edge; mind the insets rules described in CLAUDE.md.

**Privacy and AI**
- No analytics, telemetry or proprietary SDKs. No Google Play Services or Firebase libraries: the
  app must stay buildable and free of non-free dependencies for F-Droid.
- API keys never go in Room, logs, backups, exports or `SavedStateHandle`. They live in
  `SecretStore`.
- Every AI entry point shows `AiSetupPrompt` when no provider is configured and the disclosure
  dialog before the first request. Every piece of AI output shows a `ReportAiButton`. Keep
  the disclosure text in sync with what is actually sent.
- Ask for a permission with `PermissionRationaleDialog` first.

**Third-party code**
- If you bundle or link new third-party code (a library, font or data file), add its notice to
  [NOTICE](NOTICE). Files that Gradle doesn't know about also get an entry in
  `app/config/libraries`. The license must be compatible with GPL-3.0-or-later.

### Tests

- Add or update tests with your change. Fakes for every repository are in `:core:testing`;
  ViewModel tests build real use cases on top of them.
- In Android modules use `org.junit.Test`. `kotlin.test` assertions are fine.
- The FSRS port (`:core:scheduler`) and the Anki reader (`:core:anki`) are checked against reference
  vectors and fixtures written by the real implementations. If you change their math or format,
  regenerate them with the scripts named in CLAUDE.md.

### Code style

Match the surrounding code: naming, comment density and idiom. Prefer small, single-purpose
functions and classes. Comments explain why, not what.

### Commit messages and pull requests

- Write commit messages in the imperative mood ("Add card browser filters"), with a short subject
  and, when the reason isn't obvious, a body that explains it.
- Keep pull requests small enough to review. Say what you tested, and how, on a device or emulator
  if the change touches the UI.
- Don't include unrelated formatting changes, generated files you didn't need to regenerate, or
  changes to `local.properties`.

## Documentation

Docs live in `docs/`. If you change behaviour that a doc describes, update the doc in the same pull
request. A significant design decision gets a short record in `docs/adr/`.

## Translations

Mnemo's strings are currently in English only. If you'd like to help translate, open an issue first
so we can agree on how to do it.
