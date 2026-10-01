# Mnemo — Direct distribution roadmap (own website, no stores)

[ROADMAP.md](ROADMAP.md) is the path to Google Play and F-Droid. This one is the other route, chosen
2026-10-01: **share Mnemo directly**, with a download page and no store review. It is free, so the aim
is not reach but "someone can find the page, install the app, and keep it up to date without help".

**One repo, public.** `yahyafati/mnemo` was made public on 2026-10-01. Releases, the download page, the
issue tracker and the source all live in it, and CI runs in it. The plan began as two repos (a private
one for development, a small public one for releases); making the main repo public removed the second
repo, the token that linked them, and the limit on free CI minutes. Every step is compatible with going
to Play or F-Droid later (S7).

**Decisions:**

| Question | Decision |
|---|---|
| Repo | **`yahyafati/mnemo`**, public. Issues, Releases, Actions and Pages are all this repo's. |
| Source (GPL) | The source of a release is the repo at its tag. GitHub attaches `Source code (tar.gz / zip)` of the tag to every release, and the notes and the page say so. That is the "corresponding source" the GPL requires. |
| Where the files live | **GitHub Releases** of this repo, drafted by the release workflows with the repo's own `GITHUB_TOKEN`. |
| The website | A static page on **GitHub Pages** (`https://yahyafati.github.io/mnemo/`), built by `scripts/pages/build.py` and deployed by `pages.yml`. A custom domain is optional. |
| Android | A **release-signed APK**, installed by hand ("install unknown apps"). Updates through Obtainium, or by downloading again. |
| Desktop | The existing installers, **unsigned** for now (SmartScreen and Gatekeeper warnings are in [`docs/desktop/install.md`](../desktop/install.md)). |

Owner-only tasks are marked **(owner)**: they need your accounts, keys or judgment.

**What "public" means here.** The whole history, the branches, the docs and the CI workflows are
readable by anyone, and anyone may fork and redistribute under the GPL. The history was checked on
2026-10-01 (39 commits: no keystore, `local.properties` or `.env` was ever tracked, and the only
key-like strings are two test constants). Secrets never go in the repo: the keystore and its passwords
are Actions secrets (S1).

---

## Overview

| Step | Theme | Outcome | Rough effort |
|---|---|---|---|
| **S0** | Public repo | Repo public, Issues and Pages on, the app's links right, a contact in the privacy policy | done except two settings |
| **S1** | Android signing | Keystore, signed APK that installs over itself | done in the repo; five owner steps left |
| **S2** | One release, every file | One tag builds the APK and the installers and drafts one release with checksums and stable names | done in the repo; not run on GitHub yet (S3) |
| **S3** | Prove it | The workflows run green, the QA runbooks pass on real devices | 2–4 days |
| **S4** | The download page | OS-aware download buttons, install notes, checksums, links | done in the repo; goes live with Pages (S0) |
| **S5** | Updates | Obtainium works, About says where to get a new version | done in the repo; Obtainium not tried on a phone yet |
| **S6** | Share it | First release published, a place to report problems | reporting side done in the repo; publishing and sharing are the owner's |
| **S7** | Later | Signing for Windows and macOS, Intel Mac, F-Droid or Play | as wanted |

```
S0 ──► S1 ──► S2 ──► S3 ──► S6
 │             │             ▲
 └──► S4 ◄─────┘             │
        └──► S5 ─────────────┘
```

S4 can be built as soon as S2 fixes the file names, and it shouldn't be linked from anywhere before S3
shows the links work. The critical path is S3: it needs real machines (Windows, a Mac, a phone).

---

## S0 — Public repo

**Goal:** the repo is public, the settings the plan needs are on, and the app points at it.

- [x] Make `yahyafati/mnemo` public **(owner)**. Done 2026-10-01.
- [x] Secrets check before the switch: ran over all 39 commits, clean (see above). Re-run it on the day
      of the first tag, and after any commit that touches signing.
- [x] A real contact address in `docs/release/privacy-policy.md` (`scripts/pages/build.py` fails while
      it is a placeholder).
- [x] The app's links: `ProjectLinks` (`:core:model`) points at this repo (`SOURCE`, `ISSUES`, and
      the policy). Nothing to change in the code for the single-repo plan except the policy URL once
      the site is live (below).
- [ ] **Issues**: confirm they are on (they are, per the API) and add issue templates, S6.
- [ ] **Pages**: Settings › Pages › Source = **GitHub Actions** **(owner, once)**. `pages.yml` already
      deploys with `deploy-pages`; nothing else is needed, no token and no second repo.
- [ ] Run `pages.yml` once by hand (Actions › Pages › Run workflow) and open
      `https://yahyafati.github.io/mnemo/` and `/privacy/` in a private window.
- [ ] Then set `ProjectLinks.PRIVACY_POLICY` to `https://yahyafati.github.io/mnemo/privacy/` (it is the
      GitHub blob page today, which works but renders the Markdown plainly) and
      fix the sentence in [play-console.md](play-console.md) §2 step 4 only if you take that route.
      Do it in the build you release, and only once the URL loads.
- [ ] Optional: a custom domain **(owner)**: buy it, add a `CNAME` file in the site build, set the DNS
      records, tick "Enforce HTTPS". The site works at `yahyafati.github.io/mnemo` without it.
- [ ] Repo hygiene now that strangers can see it: a description and topics, `CONTRIBUTING.md` and the
      README read as an introduction for outsiders (the README should say what Mnemo is and link the
      download page once S4 is live).

**Exit:** the repo is public, Pages serves the policy, and the app's links resolve.

## S1 — Android signing

**Goal:** an APK that real phones accept now and still accept after every later update.

- [x] Create the keystore **(owner)**: [signing.md](signing.md). It exists at `~/keys/mnemo-upload.jks`
      and `local.properties` points at it (checked 2026-10-01: `assembleRelease` signs with it).
      Keep it outside the repo and back it up in two places. **Android will not update an app signed
      with a different key**, so losing it means everyone uninstalls and loses their data (they can use
      backup first, but most won't). The repo is public now: a keystore or password committed by mistake
      is public forever, and the only remedy is a new key. `*.jks` and `*.keystore` are ignored; keep it so.
- [ ] **(owner)** Confirm the two backups exist and open (`keytool -list -keystore <copy>`), and write
      down the certificate's SHA-256 fingerprint (signing.md): CI pins it as `MNEMO_CERT_SHA256`.
- [ ] **(owner)** Use this keystore as the future Play app-signing key as well, so a later Play release
      can take over from sideloaded installs. Play only lets you pick "use the same key" at the first
      upload, so this is a decision to make now, not later. (F-Droid signs with its own key; its users
      can't update from a sideloaded APK, which is fine.)
- [x] `scripts/check-apk-signature.py` (new): wraps `apksigner verify` and fails for an unsigned APK, the
      debug key, a missing v2+ signature, a signer other than `--cert-sha256`, or, given two APKs, two
      different signers. Tried 2026-10-01 on the real release build (signed, signer reported), an
      unsigned one (rejected), and a build signed with a throwaway key (rejected against the real
      fingerprint; `versionCode` 1 and 2 built with `-Pmnemo.versionCode=2` report the same signer).
      S2's Android job runs it.
- [ ] **(owner)** The install-over test on a real phone ([signing.md](signing.md) "Install-over test"):
      install a build, make a deck, install one with a higher `versionCode` over it, and check the data
      is still there (`scripts/qa/device-checks.sh install` does `adb install -r`). No device was
      attached when this step was written, so it is not run yet.
- [x] ABIs: **universal APK** (arm64-v8a, armeabi-v7a, x86, x86_64; 11.7 MB on 2026-10-01, 6 native
      libraries, all 16 KB aligned). One file is what the page and Obtainium want; an arm64-only split
      would save little and make people choose. Revisit only past about 50 MB.
- [ ] **(owner)** Put the keystore and its passwords in the repo's **Actions secrets**
      (`MNEMO_KEYSTORE_BASE64` as base64, `MNEMO_KEYSTORE_PASSWORD`, `MNEMO_KEY_ALIAS`,
      `MNEMO_KEY_PASSWORD`) and the fingerprint as the **variable** `MNEMO_CERT_SHA256`, so S2 can sign
      in CI (table in signing.md). Secrets are not passed to workflows triggered by pull requests from
      forks, which is what you want: the release workflows run only on tags and by hand, never on
      `pull_request`. Restrict who can push tags (Settings › Rules › tag ruleset for `v*`) so only you
      can start one.

**Exit:** a signed APK installs, runs, and updates over an older one.

## S2 — One release, every file

**Goal:** pushing a tag `vX.Y.Z` gives one **draft** release with everything, named so the website
can link to it forever.

`.github/workflows/release.yml` (was `desktop-release.yml`) is that release now; it has not run on GitHub
yet, which is S3. Its jobs and the script behind it:

- `android` builds and checks the signed APK, `package` (Ubuntu, Windows, macOS) builds the installers,
  and one `release` job (`needs: [android, package]`) names, checksums and drafts.
- `scripts/release/prepare-release.py` does the file work, so it can be tried on fake files: it fails
  naming any installer that is missing or doubled, copies each file under its stable name, writes the
  one `SHA256SUMS.txt` and renders the notes from [`release-notes.md`](release-notes.md) and the version's
  F-Droid changelog (so `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` must exist for
  every release: it fails without it).

- [x] **One `release` job for the whole tag.** Keep the desktop `package` matrix, add an Android job
      (same file, or `android-release.yml` that uploads a workflow artifact), and make the existing
      `release` job `needs: [package, android]`. It downloads every artifact, writes **one**
      `SHA256SUMS.txt` over all of them, and drafts the release. One job at the end means no race
      between two workflows both creating the release or overwriting the checksum file, and the
      "the Android release may already have made this release" branch in the script can go.
      Its `permissions: contents: write` and `GITHUB_TOKEN` are enough; no extra token.
- [x] **The Android job:** check out, decode the keystore from the `MNEMO_KEYSTORE_BASE64` secret into the runner's temp dir,
      run `assembleRelease`, run `scripts/check-16kb-alignment.py` and
      `scripts/check-apk-signature.py --cert-sha256 $MNEMO_CERT_SHA256`, and upload
      `Mnemo-<version>-android.apk`. It must fail, not publish, when the secrets are missing: an
      unsigned APK is useless to users (`ReleaseConfig.kt` falls back to an unsigned build, so check
      the signature check really reports the signer). Delete the decoded keystore at the end
      (`if: always()`), and never `echo` a secret.
- [x] **Source.** GitHub puts `Source code (tar.gz)` of the tag on every release, and since the repo is
      public that is the corresponding source: no extra archive is needed. The `release` job fails if a
      `.gitattributes` `export-ignore` or a `.gitmodules` appears (neither exists today). Still to do, in
      S3: download the archive from a real draft, unpack it and run `./gradlew assembleDebug`.
- [x] **Fixed-name copies.** Installer names contain the version, so
      `releases/latest/download/<file>` would break each release. Upload a second copy of each under
      a stable name (`Mnemo-android.apk`, `Mnemo-windows-x64.msi`, `Mnemo-macos-arm64.dmg`,
      `Mnemo-linux-x64.tar.gz`, `mnemo-amd64.deb`, `mnemo-x86_64.rpm`). The page links to those.
      Keep the versioned files too: they are what the checksums name, and what bug reports mention.
      Put the stable names in `SHA256SUMS.txt` as well. `latest` skips drafts and pre-releases, so a
      draft you have not published (S6) never becomes what the page links to.
- [x] **Release notes:** one file for the whole release (`docs/release/release-notes.md`, moved from
      `docs/desktop/`; it links to `install.md` on `main`). It has the Android install
      steps ("allow installs from this source", the Play Protect message), the changelog for the
      version, and a line saying the source is the tag (GitHub's "Source code" files).
- [x] Keep the tag check (tag equals `mnemo.versionName`), and bump `mnemo.versionCode` for every
      Android release, including re-releases of the same name.
- [x] Optional: tell GitHub to attach build provenance (`actions/attest-build-provenance`), which a
      public repo gets for free; the page can then say how to verify a file with `gh attestation verify`.

**Exit:** a tag produces a draft with the installers, the APK, one `SHA256SUMS.txt` and the
stable-name copies.

## S3 — Prove it

**Goal:** don't hand strangers a file you haven't run. Neither release workflow has run on GitHub yet.

- [ ] **Actions minutes are no longer a worry**: standard runners are free for public repos, so
      `ci.yml` on three systems and the release builds cost nothing. (If the repo ever goes private
      again, this is the first thing to re-check: macOS minutes count ten times.)
- [ ] Run `ci.yml` once and fix what it finds (it has not run on GitHub either). It now runs for pull
      requests from strangers too: confirm it uses no secrets (it must not, only the release
      workflows do) and that `pull_request` is the trigger, never `pull_request_target`.
- [ ] Run `release.yml` by hand (workflow_dispatch). It only builds and keeps the files as
      workflow artifacts, so it is safe to try. Fix whatever the first run on Windows, macOS and
      Linux finds (the WiX step and the smoke test are the likeliest).
- [ ] **Desktop QA (D9)**: [`docs/desktop/qa.md`](../desktop/qa.md) on a real Windows PC, a real Mac and
      a Linux machine. `scripts/qa/desktop-checks.py` does the machine-checkable parts.
      **(owner: needs the machines.)**
- [ ] **Android QA**: [qa.md](qa.md) on the signed release build, especially install-over, startup
      time and airplane mode. The ADR 0008 hardware checks (dictation, TTS, widget) belong here too.
- [ ] Cut a first tag (`v1.0.0`, or lower if you'd rather call it a beta; `mnemo.versionName` must
      stay `MAJOR.MINOR.PATCH`, and macOS needs a major of at least 1). Read the **draft** release
      (only you can see a draft), download each file from it, install it, and unpack
      the source archive and build it. Only then publish.

**Exit:** a draft built entirely by CI that you installed from on each system you own, and whose source
archive builds.

## S4 — The download page

**Goal:** a person who has never seen the repo can get the right file and understand the warnings.

`scripts/pages/build.py` already builds a one-page site and the privacy policy, and `pages.yml` already
deploys them to this repo's Pages with `deploy-pages` (no token, no second repo, no `gh-pages` branch).
Extend the page:

- [x] **Workflow triggers.** `pages.yml` runs on changes to the policy, `scripts/pages/**` and itself.
      Add the install docs it reads, so editing them redeploys, and `published` releases
      (`on: release: types: [published]`) so the page's "current version" line can be baked in too,
      if you choose to build it in at deploy time instead of reading the API in the browser.
- [x] **Download section**: a big button for the visitor's system (a few lines of JS on
      `navigator.userAgent`, with the full list of files below it for everyone else), each linking to
      `https://github.com/yahyafati/mnemo/releases/latest/download/<stable name>`. No JS fallback:
      show every system.
- [x] Show the **current version** and the date: read it from the GitHub API at page load, falling back
      to nothing if the request fails (the page must work with scripts off).
- [x] **Install notes per system**, copied from [`docs/desktop/install.md`](../desktop/install.md) and
      the Android steps: why there is a warning, exactly what to click, how to check the checksum.
- [x] **Trust block**: free, no account, no ads or tracking, everything stays on your device, AI keys are
      yours, GPL-3.0-or-later with the source one click away (the repo, at the release's tag), privacy
      policy link, and the SHA-256 explanation.
- [x] Update `build.py`'s nav and links (it already points at this repo: `REPO`) so "Source" and
      "Report a problem" are right, and add the Releases link.
- [x] Optional: a screenshot or two from `docs/release/assets`, and the logo.
- [x] Link the page from the repo README. (The About screen is left alone: it gets its releases row in S5.)

How it was built: the page is `scripts/pages/index.html` + `download.js`, and `build.py` makes the file
table from the stable names in `prepare-release.py` (it fails when the two disagree). The version line is
read by the visitor's browser, so a release needs no redeploy and the `published` trigger was left out. A
404 from the releases API shows "No release has been published yet" instead of dead links. Tried with
Playwright on seven user agents (Android, Windows, macOS, three Linux flavours, iPhone), with scripts off,
with the API failing, and at phone width.

**Exit:** the page, opened on a phone and on a laptop, leads to a working download in two taps.
Still to check once Pages is on and a release is published: the two taps, on a real phone and laptop.

## S5 — Updates

**Goal:** people on old versions can find the new one without you chasing them.

Sideloaded Android apps don't update themselves, and the desktop installers don't either.

- [x] **Obtainium** (a free app that watches a GitHub Releases page and installs updates): the page's
      Updates section has an "add Mnemo to Obtainium" link (`obtainium://add/<repo>`) and the steps.
      One correction to the plan: the APK is **not** the only `.apk` in a release, because S2 attaches a
      stable-name copy (`Mnemo-android.apk`) next to the versioned one. Obtainium may ask which to take,
      so the page names `Mnemo-android.apk` and the regular expression `^Mnemo-android\.apk$` for its
      APK filter. The deep link carries no filter (its JSON form was not checked against Obtainium's
      docs from the build environment), so the filter is a manual step until it is tried on a phone.
- [x] **About screen**: a "Latest release" row that opens the releases page in the browser
      (`ProjectLinks.RELEASES`, `:core:model`; `AboutSection`, `:feature:settings`). It makes **no
      network call** from the app, so the privacy policy stays true ("nothing is sent by Mnemo"). An
      automatic in-app update check would need a request to GitHub, a privacy-policy change and a
      setting, so it is deliberately not in this roadmap. `LicensesFlowTest` checks the row exists.
- [x] Desktop: the install doc already says how to update (install over the old one; the collection is
      outside the install folder). The page's Updates section says the same in one line.
- [ ] Optional, later: a self-hosted F-Droid repo (`fdroid server`, static files on Pages or a bucket)
      gives Android users real in-client updates. Worth it only if people actually ask.

**Exit:** Obtainium installs a new tag on a phone, and the About screen leads to the releases.
Still to check once a release is published (S3/S6): the Obtainium link and filter on a real phone, with two
releases to update between.

## S6 — Share it

**Goal:** the first release goes out and problems come back to you.

- [ ] Publish the S3 draft. Write the notes for people: what Mnemo is, what to do about the warning,
      what is missing (no sync, Apple Silicon only). **(owner)** The notes are written
      ([`release-notes.md`](release-notes.md): warnings, "Good to know"); publishing needs the S3 passes first.
- [x] Issue templates (`.github/ISSUE_TEMPLATE/`: bug, idea) so reports include the version, the system,
      and for AI problems the provider and model, never a key. The in-app Report button already opens
      a prefilled issue. Issues are public: the templates should say not to paste cards or keys.
      Done as issue forms (`bug.yml`, `idea.yml`, `config.yml`): version, system, steps, an optional AI
      provider/model field, and a required "no key, backup or private cards" checkbox. Blank issues stay on
      because the Report button opens a blank issue with the title and body in the link; a form ignores
      `body`. **(owner)** Once the templates are on `main`, tap Report on an AI output (or open an
      `issues/new?title=x&body=y` link) and confirm the form still arrives prefilled.
- [x] A `SECURITY.md` (how to report a vulnerability privately: GitHub's private vulnerability
      reporting, or the contact address), since the repo is public. Written; **(owner)** turn on
      Settings › Code security › Private vulnerability reporting, or the link in it 404s.
- [x] A contact address on the site (the same one the policy has). Already in the page's "Something went wrong?" section.
- [ ] Share the link with a few people first. Ask what confused them on the page and in the first
      five minutes, and fix that before sharing it widely.
- [x] There is no telemetry, so you hear about crashes only if people report them: say on the page
      how to (GitHub issue or email), and what to include (the version is in About). Already on the page;
      the bug form now asks for the same things.

**Exit:** the page is public, the first release is published, and three real people have installed it
from the page alone.

## S7 — Later (only when you want it)

| Item | Why | Cost |
|---|---|---|
| Windows code signing | Removes the SmartScreen warning. SignPath.io is free for open-source projects and now qualifies, since the repo is public; Azure Trusted Signing is cheap. | Application and CI setup |
| macOS notarization | The only way to remove Gatekeeper's warning | Apple Developer account, $99/yr |
| Intel Mac build | **Done in the repo:** a `macos-15-intel` entry in `release.yml`, `Mnemo-macos-x64.dmg` in `prepare-release.py` and on the page. Not run on GitHub yet (S3) | One more CI job and a name |
| Self-hosted F-Droid repo | Real updates for Android users | Hosting and a signing routine |
| Google Play | The privacy policy is S4's page and the source is public | See [ROADMAP.md](ROADMAP.md) R4–R6 |
| F-Droid | Builds from the public source repo, which it now is | See [fdroid.md](fdroid.md) |
| Reproducible builds, SBOM | Lets technical users verify the binary matches the source | CI work |

---

## What already exists

- GPL license, notices and the open-source licenses screen (R0, R2).
- `assembleRelease` with signing from the environment, the 16 KB check and R8 (R2).
- `release.yml`: the signed APK, the Windows, macOS (Apple Silicon and Intel) and Linux installers, a smoke test, one checksum file,
  stable-name copies and a draft release in this repo (S2).
- `pages.yml` and `scripts/pages/build.py`: the download page (S4) and the privacy policy, deployed with
  `deploy-pages` to this repo's Pages.
- `docs/desktop/install.md`, `docs/desktop/qa.md`, [qa.md](qa.md), [signing.md](signing.md).
- The repo is public with Issues on; `ProjectLinks` points at it.

## Open items

- **(owner)** Pages source set to GitHub Actions (S0), the keystore and its Actions secrets (S1),
  S3's real-machine passes, and S6's last steps: private vulnerability reporting on, the Report-link check,
  publishing the draft and showing the page to a few people.
- Neither `ci.yml`, `release.yml` nor `pages.yml` has run on GitHub yet (S0, S3).
- `release.yml` (S2) has the Android job and one checksum file, but has never run: it needs the Actions
  secrets and variable from S1 before a tag can build.
- The download page is built (S4) but nothing is published until Pages is switched on (S0) and the first release is (S6): until then its links 404, and the page says "No release has been published yet" once the browser sees that.
- ADR 0009, `distribution.md` and release R0 say "public source repo" for the Play and F-Droid route;
  that is now true, so R0's "publish the source repo" item can be ticked. If this plan is the one you
  follow, add a line to ADR 0009 that releases go out directly first.
