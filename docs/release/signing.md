# Signing and releasing

Two routes use this page:

- **Direct distribution** ([sideload-roadmap.md](sideload-roadmap.md)): the APK you publish on GitHub
  Releases is signed with **your keystore**, and that key is what every phone checks on every update.
  **Android will not update an app whose signing key changed**: lose the key, or replace it, and every
  user has to uninstall (losing their data unless they backed it up first). Treat it as the app's identity.
- **Google Play**: Play signs what users install with the *app-signing key*, which Google holds (Play
  App Signing), and you sign the bundle you upload with the *upload key*. F-Droid signs its own build with
  its own key (ADR 0009).

The same keystore serves both: it is the key sideloaded users trust now, and it can be registered with
Play as the app-signing key at the first upload so Play installs can take over from sideloaded ones
(Play's "use the same key" option, chosen only at that first upload: decide before it). This page is the
part that only the owner can do, and the routine for every release after that.

## Create the keystore (once, owner)

Keep it **outside the repository**. The repo is public: a keystore or password committed by mistake is
public forever and the only remedy is a new key (`.gitignore` blocks `*.jks` and `*.keystore`; keep it so).

```bash
keytool -genkeypair -v -keystore ~/keys/mnemo-upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

- Choose a strong password. Write down the alias, the store password and the key password.
- **Back it up in two places** (for example a password manager's file storage and an encrypted
  drive), and test the backup by listing it: `keytool -list -keystore <copy>`.
- Never commit it, paste it in an issue, or put its passwords in a file that is tracked.
- Write down the certificate's **SHA-256 fingerprint** (it is not a secret): `keytool -list -v -keystore
  ~/keys/mnemo-upload.jks -alias upload | grep SHA256`. Every APK you ship must show this one.

## Tell the build about it

The build reads four settings from **environment variables**, or the same names in your
`local.properties` (which is ignored by git):

```properties
MNEMO_KEYSTORE_FILE=/Users/you/keys/mnemo-upload.jks
MNEMO_KEYSTORE_PASSWORD=...
MNEMO_KEY_ALIAS=upload
MNEMO_KEY_PASSWORD=...          # optional: defaults to the store password
```

Without them, `bundleRelease` and `assembleRelease` still build, but **unsigned** (the APK is named
`app-release-unsigned.apk`). That is what pull-request CI and F-Droid use. If `MNEMO_KEYSTORE_FILE` points
to a missing file, Gradle warns and the build stays unsigned, so check the output before you upload.
Environment variables win over `local.properties`.

**Check every APK before it leaves your machine** (needs the SDK's build-tools, found through
`ANDROID_HOME` or `sdk.dir`):

```bash
python3 scripts/check-apk-signature.py app/build/outputs/apk/release/app-release.apk
python3 scripts/check-apk-signature.py --cert-sha256 <fingerprint> app/build/outputs/apk/release/app-release.apk
```

It fails for an unsigned APK, one signed with the debug key or without a v2+ signature, and one whose
signer isn't the fingerprint you give. With two APKs it also fails when their signers differ, which is
the offline form of "will this update the installed one". The release workflow runs the same script.

## The APK (direct distribution)

```bash
./gradlew assembleRelease
python3 scripts/check-apk-signature.py --cert-sha256 <fingerprint> app/build/outputs/apk/release/app-release.apk
python3 scripts/check-16kb-alignment.py app/build/outputs/apk/release/app-release.apk
```

It is a **universal APK** (arm64-v8a, armeabi-v7a, x86 and x86_64; about 12 MB on 2026-10-01). Splitting
it by ABI would save a few MB per phone but gives people four files to choose from, which is the opposite
of "someone can install it without help", and Obtainium and the download page both want a single `.apk`.
Revisit only if the universal APK passes about 50 MB.

**Install-over test** (once per new key, and whenever signing changes), on a real phone or emulator:

```bash
scripts/qa/device-checks.sh install old-build.apk      # versionCode N, signed with the release key
# open the app, make a deck and a card
scripts/qa/device-checks.sh install new-build.apk      # versionCode N+1, same key: adb install -r
```

The deck must still be there. Build the second one with `./gradlew assembleRelease
-Pmnemo.versionCode=<N+1>` (no file edit needed). A different key shows up as
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

## Put the key in GitHub Actions (owner, once)

The release workflow signs on a GitHub runner, so the keystore has to be there as secrets: Settings ›
Secrets and variables › Actions › *New repository secret*.

| Secret | Value |
|---|---|
| `MNEMO_KEYSTORE_BASE64` | the keystore, base64: `base64 -i ~/keys/mnemo-upload.jks \| pbcopy` (macOS) or `base64 -w0 …` (Linux) |
| `MNEMO_KEYSTORE_PASSWORD` | the store password |
| `MNEMO_KEY_ALIAS` | `upload` |
| `MNEMO_KEY_PASSWORD` | the key password (it may equal the store password; set it anyway) |

And one repository **variable** (not a secret) `MNEMO_CERT_SHA256` with the fingerprint, so the workflow
refuses an APK signed with a wrong or leaked-and-replaced key.

The workflow decodes `MNEMO_KEYSTORE_BASE64` into the runner's temp directory, exports
`MNEMO_KEYSTORE_FILE` and the other `MNEMO_*` settings for Gradle, and deletes the file at the end
(S2). Secrets are not given to workflows run for pull requests from forks, and the release workflows
run only on tags and by hand, never on `pull_request`.

**Restrict who can start a release**: Settings › Rules › Rulesets › *New tag ruleset*, target `v*`,
restrict creations, updates and deletions (add yourself to the bypass list if the owner needs to push
the tag). A tag is what starts a signed build, so anyone who can push one can make CI sign something.
Also keep **Settings › Actions › General › Workflow permissions** on *Read repository contents* and
require approval for workflows from outside contributors.


## Release routine (Play bundle)

For a direct release, the same version steps apply (1–3 and the tag in 8) and the workflow builds the APK.

1. Bump `mnemo.versionCode` (+1 for **every** upload, including internal and closed-test builds) and,
   for a new public version, `mnemo.versionName` (`MAJOR.MINOR.PATCH`) in `gradle.properties`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (F-Droid's release notes, at
   most 500 bytes) and run `python3 scripts/fdroid/fastlane.py`; CI fails without the changelog.
3. Run the exit check: `./gradlew assembleDebug testDebugUnitTest lint` and the JVM `test` tasks
   (see CLAUDE.md).
4. Build the signed bundle:

   ```bash
   ./gradlew bundleRelease
   ```

   Output: `app/build/outputs/bundle/release/app-release.aab`. Verify the signature with
   `jarsigner -verify -certs app/build/outputs/bundle/release/app-release.aab`: it should say
   `jar verified` and show your certificate, not a debug one.
5. Check the native libraries:
   `python3 scripts/check-16kb-alignment.py app/build/outputs/bundle/release/app-release.aab`.
6. Test what Play will deliver: `bundletool build-apks --bundle=… --ks=… --output=mnemo.apks`, then
   `bundletool install-apks --apks=mnemo.apks` on a device (R3's smoke test runs on this build).
7. Upload the `.aab` to the track (R4: internal, R5: closed, R6: production). Keep
   `app/build/outputs/mapping/release/mapping.txt` with the release: Play also takes it when you
   upload the bundle, so crashes are readable.
8. Commit the version bump, then tag the commit and push the tag (F-Droid builds from tags, see [fdroid.md](fdroid.md)):

   ```bash
   git tag -a v1.0.0 -m "Mnemo 1.0.0"
   git push origin v1.0.0
   ```

## Which key is which

| Key | Held by | Used for |
|---|---|---|
| Your keystore | You | Signing the APK on GitHub Releases (what sideloaded users trust), and the `.aab` you upload to Play |
| App-signing key | Google (Play App Signing, set up at the first upload) | Signing the APKs users install from Play |
| F-Droid key | F-Droid | Signing the APK F-Droid builds |

A user can't update from one store's build to the other's without reinstalling; Mnemo's backup
(Settings › Data) carries their collection over.
