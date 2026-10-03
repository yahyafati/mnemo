# Google Drive sync: client ids and build settings

Google Drive is a backend of sync (ADR 0013, roadmap S6). A build offers it only if it was made with a Google OAuth
client; a fork, an F-Droid build or a CI run without the settings below simply has no Drive option and never touches
the owner's Google project. **Nothing here is committed.** Settings come from environment variables or `local.properties`
(ignored by git), like the signing settings (`docs/release/signing.md`).

| Setting | Used by | Secret? |
|---|---|---|
| `MNEMO_GOOGLE_CLIENT_ID` | Android: becomes `BuildConfig.GOOGLE_CLIENT_ID` in `:app` | No (an Android client has no secret) |
| `MNEMO_GOOGLE_DESKTOP_CLIENT_ID` | Desktop: written into `mnemo-google.properties`, a resource of the app | No |
| `MNEMO_GOOGLE_DESKTOP_CLIENT_SECRET` | Desktop: the same file | Google calls it non-confidential for installed apps, but it ships in a GPL binary that can be unpacked, so keep it out of the repository (a CI secret) |

The desktop needs **both** of its settings; the secret is mandatory because Google's token endpoint refuses the
Desktop client without it (ADR 0013, spike 2026-10-03). A value that isn't made of letters, digits, `.`, `_` and `-`
is ignored with a warning.

```properties
# local.properties
MNEMO_GOOGLE_CLIENT_ID=1234-abc.apps.googleusercontent.com
MNEMO_GOOGLE_DESKTOP_CLIENT_ID=1234-def.apps.googleusercontent.com
MNEMO_GOOGLE_DESKTOP_CLIENT_SECRET=GOCSPX-...
```

CI (`.github/workflows/release.yml`): repository **variables** `MNEMO_GOOGLE_CLIENT_ID` and
`MNEMO_GOOGLE_DESKTOP_CLIENT_ID`, repository **secret** `MNEMO_GOOGLE_DESKTOP_CLIENT_SECRET`. Pull-request CI sets none.

## What the owner does in Google Cloud

1. **Android client**: package name `com.yahyafati.mnemo` and the **SHA-1 of the release keystore's certificate**
   (`keytool -list -v -keystore … | grep SHA1`). Under *Advanced settings*, switch **Custom URI scheme** on: the phone
   signs in through the link `com.yahyafati.mnemo:/oauth2redirect`. A second Android client with the *debug* keystore's SHA-1
   lets debug builds sign in (put its id in `local.properties` for local runs).
2. **Desktop client**: the loopback redirect needs no setting (`http://127.0.0.1:<port>`).
3. **Consent screen**: scope `…/auth/drive.appdata` only (non-sensitive). While it is in *Testing*, Google expires refresh
   tokens after seven days: **publish and verify the app before Drive ships.**
4. F-Droid builds get no client id (the signing key differs, so Google would refuse it): Drive is hidden there.

## Still to confirm on a device (S6 exit)

Nothing here could be run without the owner's client ids. Before releasing, on a real phone with the Android client:
sign in (the browser opens, the redirect brings Mnemo back to the front), create sync data, sign out and in again, and
check Google's *Third-party apps with account access* page shows Mnemo and that revoking it there makes the Sync screen
offer "Sign in to Google". If the custom-scheme redirect is refused, the one line to change is
`AndroidOAuthAuthorizer.redirectUri` (and the manifest's `<data android:scheme>` of `OAuthRedirectActivity`).
