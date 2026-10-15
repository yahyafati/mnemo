# Releasing Mnemo

Mnemo is GPL-3.0-or-later and free (ADR 0009). The source of a release is the public repo
(`yahyafati/mnemo`) at its tag.

## Channels

| Channel | State | How |
|---|---|---|
| **GitHub Releases + download page** (the first route) | Built, not yet run on GitHub | A `v*` tag runs `.github/workflows/release.yml`: the signed universal APK, the desktop installers (`.msi`, `.dmg` for Apple Silicon and Intel, `.deb`, `.rpm`, Linux `.tar.gz`), stable-name copies, one `SHA256SUMS.txt` and a draft release (`scripts/release/prepare-release.py`; notes from [release-notes.md](release-notes.md) + the F-Droid changelog). `pages.yml` deploys the download page and the privacy policy (`scripts/pages/`) to `https://yahyafati.github.io/mnemo/`. Android updates through Obtainium (filter `^Mnemo-android\.apk$`); the desktop installs over itself. |
| **F-Droid** | Recipe ready, merge request not opened | [fdroid.md](fdroid.md), recipe [fdroid/com.yahyafati.mnemo.yml](fdroid/com.yahyafati.mnemo.yml), texts in `fastlane/metadata/android/en-US/`. F-Droid signs with its own key. |
| **Google Play** | Prepared, account not set up | [play-console.md](play-console.md) (every form, in order) and [store-listing.md](store-listing.md) (texts, data safety). Needs a 14-day closed test with 12+ testers before production. |

## Files here

| File | What it is |
|---|---|
| [signing.md](signing.md) | The keystore, the Actions secrets, the release routine, which key is which |
| [qa.md](qa.md) | Android pre-launch QA runbook and results log (the desktop's is [`../desktop/qa.md`](../desktop/qa.md), sync's [`../sync/qa.md`](../sync/qa.md)) |
| [privacy-policy.md](privacy-policy.md) | The policy; the app links to it (`ProjectLinks.PRIVACY_POLICY`), so don't move it |
| [release-notes.md](release-notes.md) | The template of every GitHub release's notes |
| [assets/](assets/README.md) | Logo, icons, store graphics, screenshots and the scripts that make them |

## Every release

1. Bump `mnemo.versionCode` and `mnemo.versionName` (`MAJOR.MINOR.PATCH`) in `gradle.properties` and add
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
2. Run the QA runbooks that the change touches.
3. Tag `v<versionName>`, read the draft release, install from it, then publish ("Release day" in
   [`../desktop/qa.md`](../desktop/qa.md)).

## Open (owner)

- **GitHub:** Settings › Pages › Source = GitHub Actions, then run `pages.yml` once and switch
  `ProjectLinks.PRIVACY_POLICY` to `https://yahyafati.github.io/mnemo/privacy/` once it loads; turn on private
  vulnerability reporting (`SECURITY.md` links to it); a tag ruleset so only the owner can push `v*`.
- **Signing:** check both keystore backups open and record the SHA-256 fingerprint; put the keystore and passwords
  in the Actions secrets and the fingerprint in `MNEMO_CERT_SHA256` ([signing.md](signing.md)); decide to reuse the
  key as Play's app-signing key (only possible at the first Play upload); the install-over test on a phone.
- **First runs:** `ci.yml` and `release.yml` (by hand) on GitHub, fixing what the first Windows/macOS/Linux run finds.
- **Hardware passes:** [qa.md](qa.md) on a phone (frame rate, TTS, dictation, widget, airplane mode, real sources),
  [`../desktop/qa.md`](../desktop/qa.md) on Windows, macOS and Linux, [`../sync/qa.md`](../sync/qa.md) with a real
  Drive account and a Nextcloud, plus Android sync battery and data figures.
- **First release:** tag, publish the draft, check Obtainium updates between two releases, share with a few people.
- **Later, as wanted:** the F-Droid merge request; Play (account, forms, closed test, staged rollout);
  signed desktop installers; a Baseline Profile.
