# F-Droid (release ROADMAP R7)

Mnemo goes to F-Droid from the same source tree and the same Gradle build as Play (ADR 0009): no
flavor, no fork. F-Droid builds it itself from a git tag and signs it with its own key. The repo
side of R7 is done; the boxes that need your GitLab account are marked **(owner)** and ticked in
[ROADMAP.md](ROADMAP.md) when you finish them.

## What is in the repo

| What | Where | Notes |
|---|---|---|
| Listing texts, changelog, graphics | `fastlane/metadata/android/en-US/` | F-Droid reads them from the tagged commit. Texts come from [store-listing.md](store-listing.md), minus Play-only wording. Graphics are copies of [assets/](assets/README.md). |
| Checker / syncer | `scripts/fdroid/fastlane.py` | Limits, the changelog for the current `versionCode`, sizes, and that each graphic still equals its source. `--sync` refreshes the copies. CI runs it. |
| Build recipe | `docs/release/fdroid/com.yahyafati.mnemo.yml` | The file for `fdroiddata`'s `metadata/`. Kept in the canonical form `fdroid rewritemeta` writes (it drops comments), so the reasons are below. |
| FOSS-only check | `scripts/fdroid/check-foss-deps.py` | Fails on Play Services, Firebase, Play Billing, ML Kit and the like in the release dependency graph. CI runs it on `releaseRuntimeClasspath`; `LicensesFlowTest` checks the same from the licenses screen's data. |
| Build tweak | `AndroidApplicationConventionPlugin` | `dependenciesInfo.includeInApk = false` (below). |

## Why the recipe looks the way it does

- **`AntiFeatures: NonFreeNet`.** The AI features talk to whatever OpenAI-compatible provider the
  user configures, and most hosted ones are proprietary services. The description says the AI is
  optional and that local servers (Ollama, LM Studio) work. There is no `Tracking`, `Ads` or
  `NonFreeDep` anti-feature: no analytics, no ads, no proprietary library.
- **Sync adds no anti-feature.** It is optional and off by default, and F-Droid builds offer a folder and WebDAV (a server the user runs or chooses, such as Nextcloud). The Google Drive
  backend needs a Google OAuth client bound to the signing key's SHA-1, so the recipe has **no client id** (`MNEMO_GOOGLE_CLIENT_ID` is a build setting,
  `docs/sync/google-setup.md`) and Drive is hidden in F-Droid builds (decision 2026-10-03, ADR 0013). If that ever changes, Drive would need `NonFreeNet`'s text to name it.
  The store texts in `fastlane/` mention Drive as "where the build offers it" for that reason.
- **`rm: gradle/gradle-daemon-jvm.properties`.** The file pins the Gradle daemon to JDK 25 (the
  test toolchain). F-Droid's build server doesn't have that JDK and shouldn't download one. The
  release build only needs JDK 17 or newer (AGP 9), and no test task runs there.
- **`prebuild: sed … foojay-resolver … settings.gradle.kts`.** F-Droid's scanner refuses the
  `org.gradle.toolchains.foojay-resolver-convention` plugin (it downloads JDKs at build time).
  Developers still use it to fetch JDK 25 locally, so the repo keeps it and the recipe drops it.
- **`dependenciesInfo.includeInApk = false`.** AGP adds a dependency list, encrypted with a Google
  key, to the APK signing block. The scanner flags it as "Dependency metadata". The bundle keeps
  its copy, so Play is unaffected.
- **Version detection.** `versionCode` and `versionName` are in `gradle.properties`, so
  `UpdateCheckData` reads them from there, tag by tag (`^v[0-9.]+$`). `AutoUpdateMode: Version v%v`
  then adds a build entry for every new tag with the commit `v<versionName>`.
- **No `AuthorName`.** Optional; add one if you want your name on the app page.

## Checked from the repo, without an Android SDK

With `fdroidserver` 2.4.5 and `fdroiddata`'s own `config/categories.yml` and `antiFeatures.yml`:

- `fdroid lint` and `fdroid rewritemeta` pass on the recipe (it is unchanged by the rewrite).
- `fdroid scanner` on a checkout of the source reports no problems, with the `rm` and `prebuild`
  above applied. Without the `prebuild` it reported the foojay plugin, which is how that line got
  here.

**Not checked** (needs the Android SDK, which was not available): a real `fdroid build`, the
scanner's pass over the built APK (`dependenciesInfo` was fixed from the scanner's source, not from
an APK), and `fdroid checkupdates` (needs a `v1.0.0` tag). Run these in the merge request (below)
and expect to adjust the recipe once: the usual reasons are the JDK version on the build server
(a `sudo:` step that installs `openjdk-21-jdk-headless` is the fix) and a scanner finding in a
dependency.

## First submission (owner)

Prerequisites: the public repo (R0) and the release commit **tagged** `v1.0.0` with everything
above in it: the tag is what F-Droid checks out (`docs/release/signing.md`, release routine).

1. Create a GitLab account and fork <https://gitlab.com/fdroid/fdroiddata>.
2. In your fork, on a branch `com.yahyafati.mnemo`, add
   `metadata/com.yahyafati.mnemo.yml` (copy `docs/release/fdroid/com.yahyafati.mnemo.yml` unchanged).
3. Test it like their pipeline does. Install `fdroidserver` (`pipx install fdroidserver`, or their
   Docker image), then in the fork:

   ```bash
   fdroid readmeta
   fdroid rewritemeta com.yahyafati.mnemo && git diff --exit-code   # must change nothing
   fdroid lint com.yahyafati.mnemo
   fdroid scanner com.yahyafati.mnemo
   fdroid build -v -l com.yahyafati.mnemo                            # needs the SDK; the pipeline also does this
   ```

4. Push the branch, open a merge request against `fdroid/fdroiddata`, and fill in its template.
   Say in it: GPL-3.0-or-later, no proprietary SDK, the `NonFreeNet` reason, and that fastlane
   metadata is in the app repo.
5. Answer the reviewers until it merges. The build cycle then picks the app up, usually within a few
   days. Tick R7's boxes in ROADMAP.md.

## Every later release

Follow [signing.md](signing.md). The F-Droid-specific steps are:

1. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (at most 500 bytes) for the
   new `versionCode`. `scripts/fdroid/fastlane.py` and CI fail without it.
2. Re-run `python3 scripts/fdroid/fastlane.py --sync` if the screenshots or icon changed.
3. Tag `vMAJOR.MINOR.PATCH` and push the tag. `AutoUpdateMode` does the rest: F-Droid's bot adds the
   build entry and builds it. Nothing to do in `fdroiddata` unless a build fails.

Play-only internal-test builds (extra `versionCode`s that are never tagged) are invisible to F-Droid.

## Two stores, two signatures

F-Droid signs with its own key, so a user can't update a Play install with the F-Droid build, or
the other way round; they reinstall and restore a Mnemo backup (Settings › Data).

Later, once the build is reproducible, F-Droid can publish your Play-signed APK instead
(`Binaries:` and `AllowedAPKSigningKeys:` in the recipe), which lets users move between stores
without a reinstall. It needs a verified reproducible build first: build the tag on two machines,
compare the APKs with `fdroid build`'s verification, and only then change the recipe. Not needed
for v1.0.
