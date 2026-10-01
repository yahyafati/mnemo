# Mnemo — Direct distribution roadmap (own website, no stores, private repo)

[ROADMAP.md](ROADMAP.md) is the path to Google Play and F-Droid. This one is the other route, chosen
2026-10-01: **share Mnemo directly**, with a download page and no store review. It is free, so the aim
is not reach but "someone can find the page, install the app, and keep it up to date without help".

**The development repo stays private for now.** What is public is a second, small repo that holds the
releases, the download page, the issue tracker and the source of each release as an archive. Every
step is compatible with opening the main repo or going to Play or F-Droid later (S7).

**Decisions:**

| Question | Decision |
|---|---|
| Development repo | `yahyafati/mnemo` stays **private**: history, work in progress and the docs for this plan stay there. CI runs there. |
| Public repo | **`yahyafati/mnemo-releases`** (name can change; set it once, in S0). Holds GitHub Releases, the Pages site and the issues. No code history. |
| Source (GPL) | Every release carries `Mnemo-<version>-source.tar.gz`, a `git archive` of the tag. That is the "corresponding source" the GPL requires, and the licenses screen and About point at it. |
| Where the files live | **GitHub Releases of the public repo**, published by the private repo's CI. |
| The website | A static page on **GitHub Pages of the public repo**, built by the private repo's CI (`scripts/pages/build.py`) and pushed there. A custom domain is optional. |
| Android | A **release-signed APK**, installed by hand ("install unknown apps"). Updates through Obtainium, or by downloading again. |
| Desktop | The existing installers, **unsigned** for now (SmartScreen and Gatekeeper warnings are in [`docs/desktop/install.md`](../desktop/install.md)). |

Owner-only tasks are marked **(owner)**: they need your accounts, keys or judgment.

**What "private" does and doesn't mean here.** Anyone who downloads a build can download that
release's source and share it: that is what the GPL grants, and the source archive is public because
the download is. What stays private is the history, the branches and the unreleased work. If you
would rather the source reach only the people you hand the app to, don't publish the downloads
publicly: send the files directly, plus the source archive, and skip the website.

---

## Overview

| Step | Theme | Outcome | Rough effort |
|---|---|---|---|
| **S0** | Public releases repo | `mnemo-releases` with Pages and issues on, a token for CI, the app's links pointing at it, a contact in the privacy policy | ½ day |
| **S1** | Android signing | Keystore, signed APK that installs over itself | ½ day |
| **S2** | One release, every file | One tag in the private repo builds the APK and the installers and publishes them, with source and checksums, to the public repo | 1–2 days |
| **S3** | Prove it | The workflows run green, minutes are within budget, the QA runbooks pass on real devices | 2–4 days |
| **S4** | The download page | OS-aware download buttons, install notes, checksums, links | 1 day |
| **S5** | Updates | Obtainium works, About says where to get a new version | ½ day |
| **S6** | Share it | First release published, a place to report problems | ½ day |
| **S7** | Later | Signing for Windows and macOS, Intel Mac, going public, F-Droid or Play | as wanted |

```
S0 ──► S1 ──► S2 ──► S3 ──► S6
 │             │             ▲
 └──► S4 ◄─────┘             │
        └──► S5 ─────────────┘
```

S4 can be built as soon as S2 fixes the file names, and it shouldn't go public before S3 shows the
links work. The critical path is S3: it needs real machines (Windows, a Mac, a phone).

---

## S0 — Public releases repo

**Goal:** a public place to hold files, the site and issues, and an app that points at it.

- [ ] Create the public repo `yahyafati/mnemo-releases` **(owner)**. Turn **Issues** on. A short README
      (what Mnemo is, link to the page, "the source of each release is attached to it") is enough.
- [ ] Pages in that repo: Source = "Deploy from a branch", branch `gh-pages` (CI creates it in S4).
      **(owner, once)**
- [ ] A **fine-grained personal access token** limited to *only* `mnemo-releases` with "Contents:
      read and write", stored as the secret `RELEASES_REPO_TOKEN` in the private repo. **(owner)**
      It lets the private repo's CI create releases and push the site there, and nothing else. Give
      it an expiry and note the date to renew it; an expired token fails S2 and S4 with a 401.
- [ ] Put a real contact address in `docs/release/privacy-policy.md`. `scripts/pages/build.py`
      deliberately fails while it is a placeholder. **(owner)**
- [ ] Point the app at the public repo: `ProjectLinks` in `:core:model`
      (`SOURCE`, and the issues and policy URLs that derive from it) and the About "source" label,
      so the Report button opens an issue where you can read it. Update the strings that name the
      repo and the privacy policy's text if it says "this repository". Tests: `AiReportIssueTest`,
      `LicensesFlowTest`.
- [ ] Re-run the secrets check from release R0 on the private repo before the first tag: nothing in the
      archive may carry `local.properties`, a keystore or a key (`git archive` includes only tracked files).
- [ ] Optional: a custom domain **(owner)**: buy it, add a `CNAME` file in the site build, set the DNS
      records, tick "Enforce HTTPS". The site works at `yahyafati.github.io/mnemo-releases` without it.

**Exit:** the public repo exists, the token works (`gh release list --repo …` from the private repo's
CI), and the app's links no longer name the private repo.

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
- [ ] Put the keystore and its passwords in the private repo's **Actions secrets** (`MNEMO_KEYSTORE_FILE`
      as base64, `MNEMO_KEYSTORE_PASSWORD`, `MNEMO_KEY_ALIAS`, `MNEMO_KEY_PASSWORD`) so S2 can sign in CI. **(owner)**

**Exit:** a signed APK installs, runs, and updates over an older one.

## S2 — One release, every file

**Goal:** pushing a tag `vX.Y.Z` to the private repo gives one **draft** release in the public repo
with everything, named so the website can link to it forever.

Today `desktop-release.yml` builds the desktop installers and attaches them to a draft release *in the
repo it runs in*. For a private repo that is the wrong place: nobody could download from it. The
Android side does not exist yet.

- [ ] **Publish to the public repo.** Change the `release` job's `gh release create/upload` calls to
      `--repo yahyafati/mnemo-releases` with `GH_TOKEN: ${{ secrets.RELEASES_REPO_TOKEN }}`
      (the job's own `contents: write` is no longer needed). The release stays a **draft** until you
      read it and press Publish.
- [ ] Add an **Android job** (same file, or `android-release.yml`): decode the keystore from the
      secret, run `assembleRelease`, run `scripts/check-16kb-alignment.py` and `apksigner verify`,
      upload `Mnemo-<version>-android.apk`. It must fail, not publish, when the secrets are missing:
      an unsigned APK is useless to users.
- [ ] **The source archive.** `git archive --format=tar.gz --prefix=mnemo-<version>/ -o
      Mnemo-<version>-source.tar.gz <tag>`. Check it builds: unpack it and run
      `./gradlew assembleDebug` once, because `export-ignore` rules or a missing file are what break
      the "corresponding source" promise.
- [ ] **One checksum file.** The desktop `release` job writes `SHA256SUMS.txt` from its own files. Make
      the last step of the workflow recompute it from *every* asset on the release (APK and source
      included), whichever job finished first.
- [ ] **Fixed-name copies.** Installer names contain the version, so
      `releases/latest/download/<file>` would break each release. Upload a second copy of each under
      a stable name (`Mnemo-android.apk`, `Mnemo-windows-x64.msi`, `Mnemo-macos-arm64.dmg`,
      `Mnemo-linux-x64.tar.gz`, `mnemo-amd64.deb`, `mnemo-x86_64.rpm`). The page links to those.
      Keep the versioned files too: they are what the checksums name, and what bug reports mention.
- [ ] Release notes: one file for the whole release (today `docs/desktop/release-notes.md` is the
      desktop half). Add the Android install steps ("allow installs from this source", the Play
      Protect message), the changelog for the version, and a line saying where the source is.
- [ ] Keep the tag check (tag equals `mnemo.versionName`), and bump `mnemo.versionCode` for every
      Android release, including re-releases of the same name.

**Exit:** a tag in the private repo produces a draft in the public one with the installers, the APK,
the source archive, one `SHA256SUMS.txt` and the stable-name copies.

## S3 — Prove it

**Goal:** don't hand strangers a file you haven't run. Neither release workflow has run on GitHub yet.

- [ ] **Check the Actions minutes before anything else.** Private repos on the free plan get a monthly
      allowance (2,000 minutes at the time of writing; check your plan), and macOS minutes count ten
      times and Windows two times. `ci.yml` runs the desktop tests on three systems for every push and
      pull request, and `desktop-release.yml` builds on three more. I haven't measured either, so run
      each once and read the billed minutes in Settings › Billing. If they don't fit:
      - make `ci.yml`'s Windows and macOS jobs run only on tags or by hand (keep Linux for pushes), and
      - build releases only on a tag, never on a push.
      Or run the macOS and Windows builds yourself and upload their installers by hand. Public repos
      have no such limit, which is the one real argument for opening the main repo later.
- [ ] Run `desktop-release.yml` by hand (workflow_dispatch). It only builds and keeps the files as
      workflow artifacts, so it is safe to try. Fix whatever the first run on Windows, macOS and
      Linux finds (the WiX step and the smoke test are the likeliest).
- [ ] Run `ci.yml` once and fix what it finds (it has not run either).
- [ ] **Desktop QA (D9)**: [`docs/desktop/qa.md`](../desktop/qa.md) on a real Windows PC, a real Mac and
      a Linux machine. `scripts/qa/desktop-checks.py` does the machine-checkable parts.
      **(owner: needs the machines.)**
- [ ] **Android QA**: [qa.md](qa.md) on the signed release build, especially install-over, startup
      time and airplane mode. The ADR 0008 hardware checks (dictation, TTS, widget) belong here too.
- [ ] Cut a first tag (`v1.0.0`, or lower if you'd rather call it a beta; `mnemo.versionName` must
      stay `MAJOR.MINOR.PATCH`, and macOS needs a major of at least 1). Read the **draft** release in
      the public repo (only you can see a draft), download each file from it, install it, and unpack
      the source archive and build it. Only then publish.

**Exit:** a draft built entirely by CI that you installed from on each system you own, and whose source
archive builds.

## S4 — The download page

**Goal:** a person who has never seen the repo can get the right file and understand the warnings.

`scripts/pages/build.py` already builds a one-page site and the privacy policy. Extend it:

- [ ] **Deploy to the public repo.** Replace `pages.yml`'s `deploy-pages` steps with: build `site/`, then
      push it to the `gh-pages` branch of `mnemo-releases` with the token (a plain `git push --force`
      of a one-commit branch is enough). Pages in the public repo serves that branch. Trigger it on the
      same paths as today plus the install docs, so editing them redeploys.
- [ ] **Download section**: a big button for the visitor's system (a few lines of JS on
      `navigator.userAgent`, with the full list of files below it for everyone else), each linking to
      `https://github.com/yahyafati/mnemo-releases/releases/latest/download/<stable name>`. No JS fallback:
      show every system.
- [ ] Show the **current version** and the date: read it from the GitHub API at page load, falling back
      to nothing if the request fails (the page must work with scripts off).
- [ ] **Install notes per system**, copied from [`docs/desktop/install.md`](../desktop/install.md) and
      the Android steps: why there is a warning, exactly what to click, how to check the checksum.
- [ ] **Trust block**: free, no account, no ads or tracking, everything stays on your device, AI keys are
      yours, GPL-3.0-or-later with the source of every release attached, privacy policy link, and the
      SHA-256 explanation.
- [ ] Optional: a screenshot or two from `docs/release/assets`, and the logo.

**Exit:** the page, opened on a phone and on a laptop, leads to a working download in two taps.

## S5 — Updates

**Goal:** people on old versions can find the new one without you chasing them.

Sideloaded Android apps don't update themselves, and the desktop installers don't either.

- [ ] **Obtainium** (a free app that watches a GitHub Releases page and installs updates): add a
      "Get updates automatically" note and a one-tap link on the page, pointing at the public repo.
      This is zero work for you.
- [ ] **About screen**: a "Latest release" row that opens the public releases page in the browser
      (`ProjectLinks`, `:core:model`). It makes **no network call** from the app, so the privacy policy
      stays true ("nothing is sent by Mnemo"). An automatic in-app update check would need a request
      to GitHub, a privacy-policy change and a setting, so it is deliberately not in this roadmap.
- [ ] Desktop: the install doc already says how to update (install over the old one; the collection is
      outside the install folder). Make the page say the same in one line.
- [ ] Optional, later: a self-hosted F-Droid repo (`fdroid server`, static files on Pages or a bucket)
      gives Android users real in-client updates. Worth it only if people actually ask.

**Exit:** Obtainium installs a new tag on a phone, and the About screen leads to the releases.

## S6 — Share it

**Goal:** the first release goes out and problems come back to you.

- [ ] Publish the S3 draft. Write the notes for people: what Mnemo is, what to do about the warning,
      what is missing (no sync, Apple Silicon only).
- [ ] Issue templates in the public repo (bug, idea) so reports include the version, the system, and for
      AI problems the provider and model, never a key. The in-app Report button already opens a
      prefilled issue.
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
| Make `yahyafati/mnemo` public | Removes the second repo, the token, the cross-repo steps and the minutes limit; lets Pages and releases live in one place. Needs the secrets check and a decision about the history. | One afternoon; ADR 0009's plan applies as written |
| Windows code signing | Removes the SmartScreen warning. SignPath.io is free for open-source projects, but its application asks for a public repo; Azure Trusted Signing is cheap. | Application and CI setup |
| macOS notarization | The only way to remove Gatekeeper's warning | Apple Developer account, $99/yr |
| Intel Mac build | Needs an Intel runner (`macos-15-intel`) in `desktop-release.yml`; see its comment | One more CI job and a name (and more private minutes) |
| Self-hosted F-Droid repo | Real updates for Android users | Hosting and a signing routine |
| Google Play | Doesn't need a public repo, but a hosted privacy policy: S4's page is it | See [ROADMAP.md](ROADMAP.md) R4–R6 |
| F-Droid | Builds from a public source repo, so it needs the main repo public | See [fdroid.md](fdroid.md) |
| Reproducible builds, SBOM | Lets technical users verify the binary matches the source | CI work |

---

## What already exists

- GPL license, notices and the open-source licenses screen (R0, R2).
- `assembleRelease` with signing from the environment, the 16 KB check and R8 (R2).
- `desktop-release.yml`: Windows, macOS and Linux installers, a smoke test, checksums, a draft release
  (in the repo it runs in: S2 changes that).
- `pages.yml` and `scripts/pages/build.py`: the privacy policy and a one-page site (deployed with
  `deploy-pages` from the repo it runs in: S4 changes that).
- `docs/desktop/install.md`, `docs/desktop/qa.md`, [qa.md](qa.md), [signing.md](signing.md).

## Open items

- **(owner)** S0 (public releases repo, token, contact address), S1 (the keystore), and S3's
  real-machine passes.
- Neither `ci.yml` nor `desktop-release.yml` has run on GitHub yet, and the private plan's minutes
  are unmeasured (S3).
- There is no Android job in any release workflow, and the desktop one publishes to its own repo (S2).
- The site has no download links, and `pages.yml` deploys to the private repo's Pages (S4).
- ADR 0009, `distribution.md` and release R0 still say "public source repo" for the Play and F-Droid
  route; they stay true for that route. If this plan is the one you follow, add a line to ADR 0009
  that source is released per release, and the main repo stays private for now.
