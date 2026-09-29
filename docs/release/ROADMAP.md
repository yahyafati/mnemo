# Mnemo — Release roadmap (v1.0 on Google Play and F-Droid)

The product roadmap ([../ROADMAP.md](../ROADMAP.md)) ends with Phase 6: the app is feature-complete.
This roadmap covers everything between that build and a public v1.0: brand, release
engineering, store paperwork, testing and launch. The store texts and data-safety answers are in
[store-listing.md](store-listing.md), and the privacy policy is [privacy-policy.md](privacy-policy.md).

**Decisions already made (2026-09-29):**

| Question | Decision |
|---|---|
| License | **GPL-3.0-or-later** (like Anki/AnkiDroid). Every dependency (Apache-2.0, MIT, OFL-1.1) is compatible. |
| Distribution | **Google Play and F-Droid**, both free. No paid tier, ads or tracking. |
| Logo | The owner supplies it (SVG preferred); R1 turns it into every asset. |
| Play closed test | The owner has 12+ testers for the 14-day closed test. |

Owner-only tasks are marked **(owner)**, because they need your Play account, identity, payment or
judgment. Everything else can be done in the repo.

---

## Overview

| Step | Theme | Outcome | Rough effort |
|---|---|---|---|
| **R0** | Legal and source | GPL-3.0 `LICENSE`, public source repo, ADR 0009 | ½ day |
| **R1** | Brand identity | Real launcher icon, themed icon, splash, store graphics (done in the repo) | 1–2 days after the logo arrives |
| **R2** | Release engineering | Signed AAB, licenses screen, AI report action, 16 KB pages (repo work done; keystore is the owner's) | 2–3 days |
| **R3** | Pre-launch QA | Hardware checks from ADR 0008, release build smoke test | 1–2 days |
| **R4** | Play Console setup | Account, listing, policy forms, internal test | 1–2 days + account verification |
| **R5** | Closed test | 12+ testers for 14 consecutive days, fixes, production access | ≥ 14 days + review (~7 days) |
| **R6** | Production launch | Staged rollout to 100 %, vitals watched | ~1 week |
| **R7** | F-Droid | Metadata, fdroiddata merge request, first F-Droid build | 1–4 weeks (their review queue) |

```
R0 ──► R1 ──┐
            ├──► R3 ──► R4 ──► R5 (14 days) ──► R6 ──► v1.0 on Play
R0 ──► R2 ──┘
R0 ──► R7 (after R1 and R2; runs in parallel with R4–R6)
```

The critical path is the closed test (R5): it cannot start before R4, and it takes at least 14
days. Start R4's account verification early, since it can take several days on its own.

---

## R0 — Legal and source

**Goal:** make the GPL decision real before anyone outside receives a build.

- [x] Add `LICENSE` (GPL-3.0 text) at the repo root, and `NOTICE` listing third-party notices,
      including py-fsrs (MIT), which the FSRS port and optimizer are based on.
- [ ] Publish the source repo (for example GitHub). GPL requires offering the source to anyone who
      gets the binary, and F-Droid builds from it. **(owner: create the remote.)**
- [x] Check before the first push: `local.properties`, keystores and any API keys are ignored
      (`.gitignore`), and nothing secret is in the history. (Checked 2026-09-29: 13 commits, no
      keystores, `local.properties` or key-like strings. Re-run the check on the day of the push.)
- [x] `docs/adr/0009-license-and-distribution.md`: GPL-3.0, Play and F-Droid, separate signing
      keys (see R7), no proprietary SDKs. Update `distribution.md`, ADR 0008 "Open", and
      PROJECT_OVERVIEW §11.

**Exit:** the license is committed, the repo is public, and the ADR is accepted.

**Status:** everything that can be done in the repo is done. Only the public remote is left
**(owner)**: it is the one unchecked box above, and R2's About links and R7 need its URL.

## R1 — Brand identity

**Goal:** replace the Android Studio template robot everywhere a user sees the app.

**Input:** the owner's logo, `assets/logo.svg`. Everything below is generated from it and
documented in [assets/README.md](assets/README.md).

- **Launcher icon** (`app/src/main/res`)
  - [x] Adaptive icon: `ic_launcher_foreground` (the cards and check inside the central 66 × 66 dp
        safe zone of the 108 dp canvas) and `ic_launcher_background` (the logo's `#1C1A17`).
  - [x] **Monochrome** layer for Android 13+ themed icons (`ic_launcher_monochrome`): one shape,
        the check cut out, a gap between the cards.
  - [x] The legacy `mipmap-*/ic_launcher*.webp` and the round variant are deleted: minSdk is 29, so
        the adaptive icon covers every launcher.
- **Splash screen**
  - [x] `androidx.core:core-splashscreen`, `Theme.Mnemo.Starting` (logo disc on the window
        background, light and dark) and `installSplashScreen()` in `MainActivity`. It stays until
        the settings have loaded, so the first frame is in the right theme.
- **Everywhere else the logo appears**
  - [x] Notification small icons (`core_data_ic_reminder`, `core_data_ic_transfer`) stay Material
        glyphs: a status-bar icon must be a one-colour silhouette, and two overlapping cards don't
        read at 24 px.
  - [x] Widget preview: `previewImage` (`drawable-nodpi/widget_preview.png`) for launchers before
        Android 12.
  - [x] Onboarding's first page and Settings › About show the logo (`MnemoLogo` in
        `:core:designsystem`).
- **Store graphics** (in `docs/release/assets/`, reused in R7's fastlane folder)
  - [x] Play icon: `play-icon-512.png`, 512 × 512, 32-bit, the full square.
  - [x] Feature graphic: `feature-graphic.png`, 1024 × 500, no transparency.
  - [x] Phone screenshots (`screenshots/phone/`, 1080 × 2400): home, decks, a cloze card, AI
        Explain, multiple choice, Smart Extract queue, Co-Author, Analytics (two) and two dark
        shots. Real screens from an emulator with a demo collection (`assets/demo/`). 7" and 10"
        tablet shots of the Decks grid and study are in `screenshots/tablet-7/` and `tablet-10/`.
        Choose and order up to eight phone shots when filling in R4.
- [x] Roborazzi baselines re-recorded: onboarding, the component catalog (new Logo section) and
      Settings.

**Exit:** the icon looks right on a Pixel launcher (default and themed icons), on Samsung One UI,
and on the splash in light and dark. All store graphics are exported at the required sizes.

**Status:** everything that can be done in the repo is done; the icon and splash were checked on a
Pixel 8 emulator (API 35, themed icon on, dark splash). Still open **(owner)**, and part of R3's
hardware pass: look at the launcher icon and the light-theme splash on a real Pixel and on Samsung
One UI.

## R2 — Release engineering

**Goal:** a signed, reproducible, policy-safe release bundle.

- **Signing and packaging**
  - [ ] Create an upload keystore **(owner)**, stored outside the repo and backed up in two places.
        Losing it means a key reset through Play support. Steps: [signing.md](signing.md).
  - [x] `release` `signingConfig` in `mnemo.android.application` (`ReleaseConfig.kt`), read from
        `MNEMO_KEYSTORE_*` environment variables or `local.properties`. Without them the release
        build is unsigned, so CI and F-Droid can still build. Checked with a throwaway keystore:
        `bundleRelease` and `assembleRelease` verify with `jarsigner`/`apksigner`, and both build
        unsigned without one.
  - [x] `./gradlew bundleRelease` builds the AAB (R8 on). Still open **(owner, R4)**: enrol in
        **Play App Signing** at the first upload.
  - [x] Versioning: `mnemo.versionCode` and `mnemo.versionName` in `gradle.properties` are the
        single source (`1` and `1.0.0`). Bump `versionCode` by one with every upload and tag every
        release (`v1.0.0`): the routine is in [signing.md](signing.md).
- **Legal in the app**
  - [x] Open-source licenses screen under Settings › About, built with **AboutLibraries**
        (Apache-2.0, generated at build time, offline), **not** Google's `oss-licenses-plugin`.
        It lists the 120+ libraries of the release build with their full license texts, plus the
        fonts, KaTeX and py-fsrs from `app/config`. `LicensesFlowTest` checks that those are
        present, that the texts are complete, and that no Play Services, Firebase or Billing
        library is in the build (R7).
  - [x] About shows "GPL-3.0-or-later" (links to the license), the source repo link, and the privacy
        policy (in-app text, and a "Read online" link). URLs are in one place, `ProjectLinks`
        (`:core:model`). The policy link points at the file in the repo until R4 hosts it.
- **Play policy readiness**
  - [x] **Report action for AI output**: a "Report" button on Smart Extract cards, Explain and
        Example answers, Rewrite proposals, and Co-Author replies, suggested cards and rewrites
        (`ReportAiButton`, `:core:ui/ai`). It asks first, then opens a prefilled GitHub issue in
        the browser (the output, the feature and the model name only); nothing is sent by Mnemo.
        The privacy policy says so. Tests: `AiReportIssueTest`, `AssistSheetReportTest`,
        `SmartExtractScreenTest`. Email is not offered: no contact address yet (R4).
  - [x] **16 KB page size**: all `.so` files in the release AAB and APK are aligned
        (`libzstd-kmp`, `libandroidx.graphics.path`, `libdatastore_shared_counter`, on arm64-v8a
        and x86_64: LOAD segments at 16384, `zipalign -c -P 16` passes). No upgrade was needed.
        `scripts/check-16kb-alignment.py` repeats the check on any build, and CI runs it.
  - [x] Permission prompts: the reminder toggles (Settings, onboarding) sit next to their
        explanation, and the microphone and import-progress notification requests now show a
        short "why" dialog first (`PermissionRationaleDialog`). Declining the notification still
        runs the import.
- **Nice to have for v1.0**
  - [ ] A Baseline Profile for startup and the study loop (`androidx.baselineprofile`): not done.
        It needs a macrobenchmark module and a device or emulator to generate the profile.
  - [x] CI (GitHub Actions, `.github/workflows/ci.yml`): `assembleDebug testDebugUnitTest lint`,
        the JVM `test` tasks, an unsigned `bundleRelease` artifact with the 16 KB check, and
        `verifyRoborazziDebug` as a non-blocking job (baselines are recorded on macOS). The
        workflow has not run yet: it starts with the first push to the public repo.

**Exit:** `bundleRelease` produces a signed AAB that installs through `bundletool`. The licenses
screen and the report action are covered by tests. The native libraries pass the 16 KB check.

**Status:** everything that can be done in the repo is done. Still open **(owner)**: create and back
up the upload keystore, then run the signed build from [signing.md](signing.md) and install it
with `bundletool` (not installed here, so that last step is unchecked), and see the workflow go
green on the first push.

## R3 — Pre-launch QA

**Goal:** close the open manual checks from Phases 1–6 on the **release** build.

The runbook, with pass criteria for every check and a results log, is [qa.md](qa.md). The parts
`adb` can do are in `scripts/qa/device-checks.sh` (device info, installing the release build over the
old one, cold-start time, frame statistics, airplane mode, pushing an `.apkg`, a mock AI server).

- [ ] ADR 0008 hardware checks: 60/120 fps in the study loop and Analytics on a mid-range device,
      TTS voices and audio focus, dictation, and the widget on Pixel and Samsung launchers
      (qa.md §1; the launcher icon and light splash from R1 are in §1.5).
- [ ] Phase 3/4 checks with real providers: "Test connection" with one hosted provider and one
      local provider (Ollama or LM Studio), and "1,000 words in under 30 s" in Smart Extract
      (qa.md §2).
- [ ] Release smoke test (R8): fresh install → onboarding → import a real `.apkg` → study with
      every card type → backup → uninstall → reinstall → restore. Also an upgrade from the
      previous internal build (a migration path check) (qa.md §3).
- [ ] Airplane mode: everything except AI works (qa.md §4). Code review done: network code exists
      only in `:core:ai`, the link importer and the Report button (browser); the card WebView
      blocks network loads. The on-device pass is still open.
- [ ] TalkBack pass and 200 % font on one phone, plus the tablet/foldable layout on an emulator
      (qa.md §5).
- [ ] Triage: no open P0/P1 bugs (qa.md §6).

**Exit:** every check is recorded (device, OS, result) in qa.md's results log and summarised in
ADR 0008's "Open" section, which then becomes empty.

**Status:** the runbook and the `adb` helpers are written. Nothing has been run: all of it needs
devices **(owner)**, and the pass needs the signed release build from R2 (the upload keystore is
still the owner's). Do it once the R2 build exists; fix P0/P1s, then move to R4.

## R4 — Play Console setup

**Goal:** the listing and every policy declaration are complete, with a build on internal testing.

- **Account (owner)**
  - [ ] Personal developer account ($25 one-time), identity verification, and a verified contact
        email and phone. A personal account requires the closed test in R5.
- **Privacy policy**
  - [ ] Add a contact email to `privacy-policy.md` **(owner: choose the address)**.
  - [ ] Host it at a stable public URL (for example GitHub Pages from the public repo), and link
        it in the listing and in the app (R2).
- **Store listing** (texts from `store-listing.md`, graphics from R1)
  - [ ] App name, short and full description, category Education, contact email, and website
        (the repo or Pages site).
- **App content forms**
  - [ ] Data safety: collected **none**, shared **none**, encrypted in transit yes, deletion by
        uninstalling (answers in `store-listing.md`).
  - [ ] Content rating (IARC) questionnaire → Everyone. Ads: no. Target audience: 13+ (so the
        Families policy doesn't apply), not designed for children.
  - [ ] **Foreground service declaration**: `dataSync` for import, export, backup and media
        cleanup. The justification is user-started transfers of the user's own files, with a short
        screen recording of an import that shows the progress notification.
  - [ ] App access: all features work without login. AI features need the reviewer's own
        provider key, so say so, and that the rest of the app is fully usable without one.
  - [ ] Government / financial / health declarations: none.
- **First upload**
  - [ ] Upload the AAB to **internal testing**. Fix anything that Play's pre-launch report and
        policy checks flag.

**Exit:** every "App content" section is green and the internal test installs from Play.

**Status:** the repo side is done. [play-console.md](play-console.md) has every Console field, form
answer (data safety, content rating, app access, foreground-service text and the screen-recording
script), the screenshot order and the internal-test steps, ready to paste. The privacy policy is
published to GitHub Pages by `.github/workflows/pages.yml` (`scripts/pages/build.py`), which
**refuses to build until the policy has a contact address**. Everything else needs your account
**(owner)**: register and verify, choose the contact email, enable Pages, fill in the Console,
and upload the signed bundle. After that, point `ProjectLinks.PRIVACY_POLICY` at the Pages URL in
the build you upload.

## R5 — Closed test and production access

**Goal:** meet Play's requirement for new personal accounts and learn from real users.

- [ ] Create a **closed testing** track and add the 12+ testers **(owner)**, by email list or a
      Google Group. They must **opt in and stay opted in for 14 consecutive days**. Ask for a
      few more than 12 in case some drop out.
- [ ] Give testers a short brief: what to try (import from Anki, a week of real reviews, Smart
      Extract if they have a key) and where to send feedback (a Google Form or GitHub issues).
- [ ] Ship at least one update during the test (bump `versionCode`). Google looks for an
      active test.
- [ ] Watch Android vitals (crashes, ANRs) and the pre-launch report for each build.
- [ ] After 14 days, apply for **production access** **(owner)**. The form asks how you recruited
      testers, what feedback you got, and what you changed. Keep notes during the test to answer it.

**Exit:** production access granted (review usually takes up to about 7 days).

## R6 — Production launch

- [ ] Promote the tested build to production with a **staged rollout** (for example 20 % → 50 % →
      100 % over about a week). Pause it if the crash rate goes above ~1 %.
- [ ] Tag `v1.0.0`, publish release notes, and add the Play badge to the README.
- [ ] Watch vitals, reviews and the reporting inbox daily for the first two weeks.
- [ ] Start a routine: a patch release when needed, and yearly target SDK updates (Play's
      deadline is usually August 31).

**Exit:** v1.0 at 100 % with a user-perceived crash rate under Play's bad-behavior threshold (1.09 %).

## R7 — F-Droid (in parallel with R4–R6)

**Goal:** the same app, built from source by F-Droid.

- [x] `fastlane/metadata/android/en-US/`: `title.txt`, `short_description.txt`,
      `full_description.txt`, `changelogs/1.txt`, and `images/` (icon, feature graphic, 11 phone
      and 2 + 2 tablet screenshots) from R1. Play-specific wording stays out.
      `scripts/fdroid/fastlane.py` checks it (limits, the changelog for the current
      `versionCode`, that each graphic equals its source) and CI runs it.
- [x] Check the build is FOSS-only: no Play Services, and no Firebase or GMS in the dependency
      tree. `scripts/fdroid/check-foss-deps.py` reads `:app:dependencies` and runs in CI, next to
      `LicensesFlowTest`. The scanner's rules turned up two real problems, both fixed: the APK's
      encrypted dependency-metadata block (`dependenciesInfo.includeInApk = false`) and the foojay
      JDK-download plugin (dropped by the recipe's `prebuild`). zstd-kmp's natives come from Maven
      Central; the scanner's source pass has no finding for them, and its pass over the built APK
      runs in the merge request's pipeline (not run yet).
- [x] Expect the **NonFreeNet** anti-feature, since the AI features can talk to proprietary
      services. It is in the recipe with an explanation, and the description says the AI is
      optional and that local servers work.
- [x] Signing: F-Droid signs with its own key by default, so users can't switch between the Play
      and F-Droid builds without reinstalling (backups carry the data over). Later, a reproducible
      build lets F-Droid publish the developer-signed APK instead
      ([fdroid.md](fdroid.md), last section).
- [x] The build recipe for `fdroiddata` is `fdroid/com.yahyafati.mnemo.yml`. `fdroid lint`,
      `rewritemeta` and `scanner` pass on it.
- [ ] Open a merge request to `fdroiddata` on GitLab with the build recipe **(owner: GitLab
      account)**. Answer reviewer questions until it's merged. Steps: [fdroid.md](fdroid.md).

**Exit:** Mnemo appears in the F-Droid client and updates automatically from new git tags.

**Status:** everything that can be done in the repo is done; [fdroid.md](fdroid.md) has the recipe's
reasoning, the local test commands and the submission steps. No Android SDK was available while
this was written, so a full `fdroid build` and the APK-level scan have **not** been run: expect to
adjust the recipe once in the merge request. Still open **(owner)**: the public repo with a
`v1.0.0` tag, a GitLab account, and the merge request.

---

## Open items

| Item | Owner | Needed by |
|---|---|---|
| Public source repo URL | owner | R0 (needed for About, privacy policy hosting, F-Droid) |
| Contact email for the listing and privacy policy | owner | R4 |
| Upload keystore, created and backed up | owner | R2 |
| GitLab account (for the `fdroiddata` merge request) | owner | R7 |
| Play developer account verified | owner | R4 (start early) |
