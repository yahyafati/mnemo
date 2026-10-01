# Mnemo — Direct distribution roadmap (own website, no stores)

[ROADMAP.md](ROADMAP.md) is the path to Google Play and F-Droid. This one is the other route, chosen
2026-10-01: **share Mnemo directly**, with a download page and no store review. It is free, so the aim
is not reach but "someone can find the page, install the app, and keep it up to date without help".

Nothing here replaces the store roadmap. Every step is compatible with doing Play or F-Droid later
(S1 keeps that door open on purpose).

**Decisions:**

| Question | Decision |
|---|---|
| Where the files live | **GitHub Releases** (already built by CI, with `SHA256SUMS.txt`). No second host. |
| The website | A static page on **GitHub Pages** (`pages.yml` and `scripts/pages/build.py` already publish the privacy policy and a one-page site). A custom domain is optional. |
| Android | A **release-signed APK**, installed by hand ("install unknown apps"). Updates through Obtainium, or by downloading again. |
| Desktop | The existing installers, **unsigned** for now (SmartScreen and Gatekeeper warnings are documented in [`docs/desktop/install.md`](../desktop/install.md)). |
| Source | Public repo, as the GPL requires (ADR 0009). |

Owner-only tasks are marked **(owner)**: they need your accounts, keys or judgment.

---

## Overview

| Step | Theme | Outcome | Rough effort |
|---|---|---|---|
| **S0** | Public repo and Pages | Repo public, Pages on, privacy policy has a contact | ½ day |
| **S1** | Android signing | Upload-grade keystore, signed APK that installs over itself | ½ day |
| **S2** | One release, every file | One tag builds the APK and all desktop installers, with one `SHA256SUMS.txt` and fixed-name copies | 1 day |
| **S3** | Prove it | The workflows run green on GitHub; the QA runbooks pass on real devices | 2–4 days |
| **S4** | The download page | OS-aware download buttons, install notes, checksums, links | 1 day |
| **S5** | Updates | Obtainium works, About says where to get a new version | ½ day |
| **S6** | Share it | First release published, a place to report problems | ½ day |
| **S7** | Later | Signing for Windows and macOS, Intel Mac, F-Droid or Play | as wanted |

```
S0 ──► S1 ──► S2 ──► S3 ──► S6
 │             │             ▲
 └──► S4 ◄─────┘             │
        └──► S5 ─────────────┘
```

S4 can be built as soon as S2 fixes the file names, and it shouldn't go public before S3 shows the
links work. The critical path is S3: it needs real machines (Windows, a Mac, a phone).

---

## S0 — Public repo and Pages

**Goal:** the source and the site have a public home before anyone gets a binary.

- [ ] Make `github.com/yahyafati/mnemo` public **(owner)**. Re-run the secrets check from release
      R0 first: no `local.properties`, keystore or key-like strings in the history.
- [ ] Settings › Pages › Source = **GitHub Actions** **(owner, once)**.
- [ ] Put a real contact address in `docs/release/privacy-policy.md`. `scripts/pages/build.py`
      deliberately fails while it is a placeholder, so Pages can't deploy until then. **(owner)**
- [ ] Optional: a custom domain **(owner)**: buy it, add a `CNAME` file in the site build, set the
      DNS records, tick "Enforce HTTPS". The site works at `yahyafati.github.io/mnemo` without it.

**Exit:** the repo is public, and `https://yahyafati.github.io/mnemo/` shows the privacy policy.

## S1 — Android signing

**Goal:** an APK that real phones accept now and still accept after every later update.

- [ ] Create the keystore **(owner)**: [signing.md](signing.md). Keep it outside the repo and back it
      up in two places. **Android will not update an app signed with a different key**, so losing it
      means everyone uninstalls and loses their data (they can use backup first, but most won't).
- [ ] Use this keystore as the future Play upload/app-signing key as well, so a later Play release
      can take over from sideloaded installs. (F-Droid signs with its own key; its users can't
      update from a sideloaded APK, which is fine.)
- [ ] `./gradlew assembleRelease` with `MNEMO_KEYSTORE_*` set; verify with `apksigner verify`. Install
      it on a real phone, then install a second build with a higher `versionCode` over it and
      check the data is still there (`scripts/qa/device-checks.sh` has the install-over step).
- [ ] Decide on ABIs: the universal APK is simplest. If its size bothers you, an arm64-only APK is
      what almost every current phone needs, but keep the universal one for emulators and old devices.
- [ ] Put the keystore and its passwords in GitHub **Actions secrets** (`MNEMO_KEYSTORE_FILE` as
      base64, `MNEMO_KEYSTORE_PASSWORD`, `MNEMO_KEY_ALIAS`, `MNEMO_KEY_PASSWORD`) so S2 can sign in CI. **(owner)**

**Exit:** a signed APK installs, runs, and updates over an older one.

## S2 — One release, every file

**Goal:** pushing a tag `vX.Y.Z` gives one draft release with everything, named so the website can
link to it forever.

Today `desktop-release.yml` builds the desktop installers and attaches them to a draft release. The
Android side does not exist yet (its comment already expects it to create the release first).

- [ ] Add an **Android job** (same file, or `android-release.yml`): decode the keystore from the
      secret, run `assembleRelease`, run `scripts/check-16kb-alignment.py` and `apksigner verify`,
      upload `Mnemo-<version>-android.apk`. It must fail, not publish, when the secrets are missing:
      an unsigned APK is useless to users.
- [ ] **One checksum file.** The desktop `release` job writes `SHA256SUMS.txt` from its own files. Make
      the last step of the workflow recompute it from *every* asset on the release, so it covers the
      APK too, whichever job finished first.
- [ ] **Fixed-name copies.** Installer names contain the version, so
      `releases/latest/download/<file>` would break each release. Upload a second copy of each under
      a stable name (`Mnemo-android.apk`, `Mnemo-windows-x64.msi`, `Mnemo-macos-arm64.dmg`,
      `Mnemo-linux-x64.tar.gz`, `mnemo-amd64.deb`, `mnemo-x86_64.rpm`). The page links to those.
      Keep the versioned files too: they are what the checksums name, and what bug reports mention.
- [ ] Release notes: one file for the whole release (today `docs/desktop/release-notes.md` is the
      desktop half). Add the Android install steps ("allow installs from this source", the Play
      Protect message) and a changelog for the version.
- [ ] Keep the tag check (tag equals `mnemo.versionName`), and bump `mnemo.versionCode` for every
      Android release, including re-releases of the same name.

**Exit:** a tag produces a draft with six or seven installers, one APK, one `SHA256SUMS.txt`, and the
stable-name copies.

## S3 — Prove it

**Goal:** don't hand strangers a file you haven't run. Neither release workflow has run on GitHub yet.

- [ ] Run `desktop-release.yml` by hand (workflow_dispatch). It only builds and keeps the files as
      workflow artifacts, so it is safe to try. Fix whatever the first run on Windows, macOS and
      Linux finds (the WiX step and the smoke test are the likeliest).
- [ ] Run `ci.yml` once on the public repo and fix what it finds (it has not run either).
- [ ] **Desktop QA (D9)**: [`docs/desktop/qa.md`](../desktop/qa.md) on a real Windows PC, a real Mac and
      a Linux machine. `scripts/qa/desktop-checks.py` does the machine-checkable parts.
      **(owner: needs the machines.)**
- [ ] **Android QA**: [qa.md](qa.md) on the signed release build, especially install-over, startup
      time and airplane mode. The ADR 0008 hardware checks (dictation, TTS, widget) belong here too.
- [ ] Cut a first tag (`v1.0.0`, or lower if you'd rather call it a beta; `mnemo.versionName` must
      stay `MAJOR.MINOR.PATCH`, and macOS needs a major of at least 1). Read the **draft** release, download each
      file from it and install it. Only then publish.

**Exit:** a draft release built entirely by CI that you installed from on each system you own.

## S4 — The download page

**Goal:** a person who has never seen the repo can get the right file and understand the warnings.

`scripts/pages/build.py` already builds a one-page site and the privacy policy. Extend it:

- [ ] **Download section**: a big button for the visitor's system (a few lines of JS on
      `navigator.userAgent`, with the full list of files below it for everyone else), each linking to
      `https://github.com/yahyafati/mnemo/releases/latest/download/<stable name>`. No JS fallback:
      show every system.
- [ ] Show the **current version** and the date: read it from the GitHub API at page load, falling back
      to nothing if the request fails (the page must work offline-cached and with scripts off).
- [ ] **Install notes per system**, copied from [`docs/desktop/install.md`](../desktop/install.md) and
      the Android steps: why there is a warning, exactly what to click, how to check the checksum.
- [ ] **Trust block**: free, no account, no ads or tracking, everything stays on your device, AI keys are
      yours, GPL source link, privacy policy link, and the SHA-256 explanation.
- [ ] The Pages workflow triggers on `docs/release/privacy-policy.md`, `scripts/pages/**` and its own
      file only: add the install docs to its `paths`, so editing them redeploys.
- [ ] Optional: a screenshot or two from `docs/release/assets`, and the logo.

**Exit:** the page, opened on a phone and on a laptop, leads to a working download in two taps.

## S5 — Updates

**Goal:** people on old versions can find the new one without you chasing them.

Sideloaded Android apps don't update themselves, and the desktop installers don't either.

- [ ] **Obtainium** (a free app that watches a GitHub Releases page and installs updates): add a
      "Get updates automatically" note and a one-tap link on the page. This is zero work for you.
- [ ] **About screen**: a "Latest release" row that opens the Releases page in the browser (`ProjectLinks`,
      `:core:model`). It makes **no network call** from the app, so the privacy policy stays true
      ("nothing is sent by Mnemo"). An automatic in-app update check would need a request to GitHub,
      a privacy-policy change and a setting, so it is deliberately not in this roadmap.
- [ ] Desktop: the install doc already says how to update (install over the old one; the collection is
      outside the install folder). Make the page say the same in one line.
- [ ] Optional, later: a self-hosted F-Droid repo (`fdroid server`, static files on Pages or a bucket)
      gives Android users real in-client updates. Worth it only if people actually ask.

**Exit:** Obtainium installs a new tag on a phone, and the About screen leads to the releases.

## S6 — Share it

**Goal:** the first release goes out and problems come back to you.

- [ ] Publish the S3 draft. Write the notes for people: what Mnemo is, what to do about the warning,
      what is missing (no sync, Apple Silicon only).
- [ ] Issue templates (bug, idea) so reports include the version, the system, and for AI problems the
      provider and model, never a key. The in-app Report button already opens a prefilled issue.
- [ ] A contact address on the site (the same one S0 puts in the policy).
- [ ] Share the link with a few people first. Ask what confused them on the page and in the first
      five minutes, and fix that before sharing it widely.
- [ ] There is no telemetry, so you hear about crashes only if people report them: say on the page
      how to (GitHub issue or email), and what to include (the version is in About).

**Exit:** the page is public, the first release is published, and three real people have installed it
from the page alone.

## S7 — Later (only when you want it)

| Item | Why | Cost |
|---|---|---|
| Windows code signing | Removes the SmartScreen warning. SignPath.io is free for open-source projects; Azure Trusted Signing is cheap. | Application and CI setup |
| macOS notarization | The only way to remove Gatekeeper's warning | Apple Developer account, $99/yr |
| Intel Mac build | Needs an Intel runner (`macos-15-intel`) in `desktop-release.yml`; see its comment | One more CI job and a name |
| Self-hosted F-Droid repo | Real updates for Android users | Hosting and a signing routine |
| Google Play or F-Droid | The whole of [ROADMAP.md](ROADMAP.md); R0–R3 above are already done in the repo | See that file |
| Reproducible builds, SBOM | Lets technical users verify the binary matches the source | CI work |

---

## What already exists

- GPL license, notices and the open-source licenses screen (R0, R2).
- `assembleRelease` with signing from the environment, the 16 KB check and R8 (R2).
- `desktop-release.yml`: Windows, macOS and Linux installers, a smoke test, checksums, a draft release.
- `pages.yml` and `scripts/pages/build.py`: the privacy policy and a one-page site.
- `docs/desktop/install.md`, `docs/desktop/qa.md`, [qa.md](qa.md), [signing.md](signing.md).

## Open items

- **(owner)** S0 (public repo, Pages, contact address), S1 (the keystore), and S3's real-machine passes.
- Neither `ci.yml` nor `desktop-release.yml` has run on GitHub yet.
- There is no Android job in any release workflow (S2).
- The site has no download links yet (S4).
