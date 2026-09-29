# Signing and releasing

Play signs what users install with the **app-signing key**, which Google holds (Play App Signing).
You sign the bundle you upload with the **upload key**, which only you hold. F-Droid signs its own
build with its own key (ADR 0009). This page is the part of R2 that only the owner can do, and the
routine for every release after that.

## Create the upload keystore (once, owner)

Keep it **outside the repository** (`.gitignore` also blocks `*.jks` and `*.keystore`):

```bash
keytool -genkeypair -v -keystore ~/keys/mnemo-upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

- Choose a strong password. Write down the alias, the store password and the key password.
- **Back it up in two places** (for example a password manager's file storage and an encrypted
  drive). Losing the upload key means asking Play support for a key reset, which takes days.
- Never commit it, paste it in an issue, or put its passwords in a file that is tracked.

## Tell the build about it

The build reads four settings from **environment variables**, or the same names in your
`local.properties` (which is ignored by git):

```properties
MNEMO_KEYSTORE_FILE=/Users/you/keys/mnemo-upload.jks
MNEMO_KEYSTORE_PASSWORD=...
MNEMO_KEY_ALIAS=upload
MNEMO_KEY_PASSWORD=...          # optional: defaults to the store password
```

Without them, `bundleRelease` and `assembleRelease` still build, but **unsigned**. That is what CI
and F-Droid use. If `MNEMO_KEYSTORE_FILE` points to a missing file, Gradle warns and the build
stays unsigned, so check the output before you upload.

## Release routine

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
| Upload key | You (this keystore) | Signing the `.aab` you upload |
| App-signing key | Google (Play App Signing, set up at the first upload) | Signing the APKs users install from Play |
| F-Droid key | F-Droid | Signing the APK F-Droid builds |

A user can't update from one store's build to the other's without reinstalling; Mnemo's backup
(Settings › Data) carries their collection over.
