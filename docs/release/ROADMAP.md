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
| **R1** | Brand identity | Real launcher icon, themed icon, splash, store graphics | 1–2 days after the logo arrives |
| **R2** | Release engineering | Signed AAB, licenses screen, AI report action, 16 KB pages | 2–3 days |
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

- [ ] Add `LICENSE` (GPL-3.0 text) at the repo root, and `NOTICE` listing third-party notices,
      including py-fsrs (MIT), which the FSRS port and optimizer are based on.
- [ ] Publish the source repo (for example GitHub). GPL requires offering the source to anyone who
      gets the binary, and F-Droid builds from it. **(owner: create the remote.)**
- [ ] Check before the first push: `local.properties`, keystores and any API keys are ignored
      (`.gitignore`), and nothing secret is in the history.
- [ ] `docs/adr/0009-license-and-distribution.md`: GPL-3.0, Play and F-Droid, separate signing
      keys (see R7), no proprietary SDKs. Update `distribution.md`, ADR 0008 "Open", and
      PROJECT_OVERVIEW §11.

**Exit:** the license is committed, the repo is public, and the ADR is accepted.

## R1 — Brand identity

**Goal:** replace the Android Studio template robot everywhere a user sees the app.

**Input needed (owner):** the logo as an SVG (or a large PNG), ideally with a symbol-only version
that reads at 24 px and a one-colour version.

- **Launcher icon** (`app/src/main/res`)
  - [ ] Adaptive icon: `ic_launcher_foreground` (the symbol inside the central 66 × 66 dp safe
        zone of the 108 dp canvas) and `ic_launcher_background` (a brand colour or a simple shape).
  - [ ] **Monochrome** layer for Android 13+ themed icons. It currently reuses the robot foreground.
  - [ ] Replace the legacy `mipmap-*/ic_launcher*.webp` (the round and square fallbacks used before
        API 26 are not needed at minSdk 29, so delete them if the adaptive icon covers everything).
- **Splash screen**
  - [ ] Add `androidx.core:core-splashscreen`, a `Theme.Mnemo.Starting` theme (icon + brand
        background, light and dark), and `installSplashScreen()` in `MainActivity`. Keep it on
        screen only until the first frame.
- **Everywhere else the logo appears**
  - [ ] Notification small icons (`core_data_ic_reminder`, `core_data_ic_transfer`): keep them as
        glyphs, or use the one-colour logo for the reminder.
  - [ ] Widget preview (`widget_today_info.xml`): add `previewImage` for launchers before Android 12.
  - [ ] Onboarding and Settings › About: show the logo.
- **Store graphics** (in `docs/release/assets/`, reused in R7's fastlane folder)
  - [ ] Play icon: 512 × 512 PNG, 32-bit, ≤ 1 MB. It's the full square: Play applies the mask.
  - [ ] Feature graphic: 1024 × 500 JPG or 24-bit PNG, no transparency.
  - [ ] Phone screenshots (1080 × 2400): Decks, a study card, multiple choice, Smart Extract
        queue, Co-Author and Analytics, light theme with one dark shot. 7" and 10" tablet shots
        of the Decks grid and study. Use realistic demo decks, not test data.
- [ ] Record the Roborazzi baselines again (`recordRoborazziDebug`) wherever the logo shows up.

**Exit:** the icon looks right on a Pixel launcher (default and themed icons), on Samsung One UI,
and on the splash in light and dark. All store graphics are exported at the required sizes.

## R2 — Release engineering

**Goal:** a signed, reproducible, policy-safe release bundle.

- **Signing and packaging**
  - [ ] Create an upload keystore **(owner)**, stored outside the repo and backed up in two places.
        Losing it means a key reset through Play support.
  - [ ] Add a `release` `signingConfig` in `mnemo.android.application`, read from
        `local.properties` or environment variables. If they are missing, fall back to
        unsigned, so CI and F-Droid can still build.
  - [ ] Build with `./gradlew bundleRelease` (AAB). Enrol in **Play App Signing** at the first upload.
  - [ ] Versioning: `versionCode` goes up by one with every upload (keep a single source, such as
        `gradle.properties`). `versionName` follows `1.0.0`. Tag every release in git (`v1.0.0`).
- **Legal in the app**
  - [ ] Open-source licenses screen under Settings › About. Use **AboutLibraries** (Apache-2.0,
        generated at build time), **not** Google's `oss-licenses-plugin`, which pulls in Play
        Services and would break the F-Droid build. Include the bundled fonts, KaTeX and py-fsrs,
        which Gradle metadata doesn't know about.
  - [ ] About shows "GPL-3.0", a link to the source repo, and a link to the privacy policy URL (R4).
- **Play policy readiness**
  - [ ] **Report action for AI output** (Play's AI-generated content policy): a "Report" item on
        Smart Extract cards, Explain/Example/Rewrite and Co-Author suggestions. It can open an email
        or a GitHub issue with the text, after the user confirms. Nothing is sent automatically.
  - [ ] **16 KB page size**: Play requires native libraries in apps targeting Android 15+ to be
        16 KB-aligned. Check the zstd-kmp `.so` files in the AAB (APK Analyzer, or
        `zipalign -c -P 16 -v 4`), and upgrade zstd-kmp if they aren't aligned.
  - [ ] Check that every permission prompt has an in-context explanation first (`RECORD_AUDIO`
        from the microphone button, `POST_NOTIFICATIONS` from the reminder toggle).
- **Nice to have for v1.0**
  - [ ] A Baseline Profile for startup and the study loop (`androidx.baselineprofile`).
  - [ ] CI (GitHub Actions): `assembleDebug testDebugUnitTest lint`, the JVM `test` tasks and
        `verifyRoborazziDebug` on every push. An unsigned `bundleRelease` as a build artifact.

**Exit:** `bundleRelease` produces a signed AAB that installs through `bundletool`. The licenses
screen and the report action are covered by tests. The native libraries pass the 16 KB check.

## R3 — Pre-launch QA

**Goal:** close the open manual checks from Phases 1–6 on the **release** build.

- [ ] ADR 0008 hardware checks: 60/120 fps in the study loop and Analytics on a mid-range device,
      TTS voices and audio focus, dictation, and the widget on Pixel and Samsung launchers.
- [ ] Phase 3/4 checks with real providers: "Test connection" with one hosted provider and one
      local provider (Ollama or LM Studio), and "1,000 words in under 30 s" in Smart Extract.
- [ ] Release smoke test (R8): fresh install → onboarding → import a real `.apkg` → study with
      every card type → backup → uninstall → reinstall → restore. Also an upgrade from the
      previous internal build (a migration path check).
- [ ] Airplane mode: everything except AI works.
- [ ] TalkBack pass and 200 % font on one phone, plus the tablet/foldable layout on an emulator.
- [ ] Triage: no open P0/P1 bugs.

**Exit:** every check is recorded (device, OS, result) in ADR 0008's "Open" section, which then
becomes empty.

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

- [ ] `fastlane/metadata/android/en-US/`: `title.txt`, `short_description.txt`,
      `full_description.txt`, `changelogs/<versionCode>.txt`, and `images/` (icon, featureGraphic,
      phoneScreenshots) from R1. Play-specific lines, such as the data-safety note, stay out.
- [ ] Check the build is FOSS-only: no Play Services, and no Firebase or GMS in the dependency
      tree (`./gradlew :app:dependencies`). Pre-built natives from Maven (zstd-kmp) are normally
      allowed, but check with the F-Droid scanner.
- [ ] Expect the **NonFreeNet** anti-feature, since the AI features can talk to proprietary
      services. Say in the description that they are optional and that local servers work.
- [ ] Signing: F-Droid signs with its own key by default, so users can't switch between the Play
      and F-Droid builds without reinstalling (backups carry the data over). Later, a reproducible
      build lets F-Droid publish the developer-signed APK instead.
- [ ] Open a merge request to `fdroiddata` on GitLab with the build recipe **(owner: GitLab
      account)**. Answer reviewer questions until it's merged.

**Exit:** Mnemo appears in the F-Droid client and updates automatically from new git tags.

---

## Open items

| Item | Owner | Needed by |
|---|---|---|
| Logo file (SVG) | owner | R1 |
| Public source repo URL | owner | R0 (needed for About, privacy policy hosting, F-Droid) |
| Contact email for the listing and privacy policy | owner | R4 |
| Upload keystore, created and backed up | owner | R2 |
| Play developer account verified | owner | R4 (start early) |
