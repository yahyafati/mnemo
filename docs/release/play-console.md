# Play Console setup (release ROADMAP R4)

Everything you type or click in the Play Console, in order, with the answers ready to paste. The
repo side of R4 is done (this page, the hosted privacy policy, the release notes); the boxes that
need your account, identity or judgment are marked **(owner)** and ticked in
[ROADMAP.md](ROADMAP.md) when you finish them. Texts come from [store-listing.md](store-listing.md),
graphics from [assets/](assets/README.md).

## 0. Order of work

1. Start account verification first (§1): it can take days, and nothing else waits on it less.
2. Choose the contact address and finish the privacy policy (§2), then turn on Pages.
3. Fill in the listing and every App content form (§3–§5) while verification runs.
4. Upload the signed bundle to **Internal testing** (§6) and clear what Play flags.

## 1. Developer account (owner)

- Register at <https://play.google.com/console> as a **Personal** account ($25 one-time): legal
  name, address, a verified contact email and phone. Have a government ID ready.
- A personal account created after 13 Nov 2023 must run the closed test in R5 before production.
- Also verify the address/phone Play shows on the store page. It is public: use a dedicated
  address, not a private one.
- Create the app: name `Mnemo: Spaced Repetition`, default language English (United States),
  **App** (not game), **Free**, and accept the declarations. Free stays free: a paid app can
  never be switched to free (and the reverse) without a new listing.

## 2. Contact address and privacy policy

1. **(owner)** Choose the public contact address. Replace the placeholder in
   [privacy-policy.md](privacy-policy.md) ("Contact") with it, and use the same address as the
   listing's contact email. The in-app policy text (`PrivacyPolicyDialog`,
   `feature/settings/.../AboutSection.kt`) repeats the policy in short: add the address there too
   if you want it shown in the app.
2. **(owner)** In the GitHub repo: *Settings › Pages › Source: GitHub Actions*. Push the change to
   `main` (or run the *Pages* workflow by hand). `.github/workflows/pages.yml` runs
   `scripts/pages/build.py`, which **refuses to build while the placeholder or a missing email is
   in the policy**, and publishes:
   - `https://yahyafati.github.io/mnemo/`: a one-page site (the listing's website)
   - `https://yahyafati.github.io/mnemo/privacy/`: the privacy policy (the listing's policy URL)
3. Open both URLs in a private window and check the table renders and the address is there.
4. Point the app at the hosted policy: set `ProjectLinks.PRIVACY_POLICY` (`:core:model`) to the
   `/privacy/` URL, and update the sentence in the About section and in
   [privacy-policy.md](privacy-policy.md) if they mention GitHub. Do this in the build you upload
   in §6, and only once the URL loads, so the released app never links to a 404.

Play rejects a policy that is a PDF, an editable doc, or a page that needs a login. The Pages URL is
none of those.

## 3. Store listing (Grow › Store presence › Main store listing)

| Field | Value |
|---|---|
| App name (≤ 30) | `Mnemo: Spaced Repetition` (24) |
| Short description (≤ 80) | `Flashcards that schedule themselves. Offline, private, with optional AI.` (72) |
| Full description (≤ 4,000) | The text in [store-listing.md](store-listing.md) (1,500) |
| App icon | `assets/play-icon-512.png` |
| Feature graphic | `assets/feature-graphic.png` |
| Phone screenshots (2–8) | `assets/screenshots/phone/`: see the picks below |
| 7-inch tablet | `assets/screenshots/tablet-7/` |
| 10-inch tablet | `assets/screenshots/tablet-10/` |
| Category / tags | **Education**; tags: Flashcards, Study, Education, Learning (pick from Play's list) |
| Contact | email: the address from §2; website: the Pages URL; phone: optional |
| Privacy policy | the `/privacy/` URL |

**Phone screenshots, in this order** (eight, best foot first; the other three stay for F-Droid):
`03-study-cloze-light`, `01-home-light`, `05-multiple-choice-light`, `04-ai-explain-light`,
`06-smart-extract-light`, `08-analytics-light`, `07-co-author-light`, `10-analytics-dark`.

Play checks that the screenshots show the real app, that nothing in the graphics claims a rank,
price or "free" badge, and that the feature graphic is not covered by text near the edges. The
existing files follow this. Do not add device frames with a third party's logo.

**Translations:** English only for v1.0. Do not machine-translate the listing.

## 4. App content (Policy and programs › App content)

Every section must show a green tick before Play accepts a release.

| Section | Answer |
|---|---|
| **Privacy policy** | The `/privacy/` URL from §2 |
| **Ads** | No, the app has no ads |
| **App access** | *All functionality is available without special access.* Add instructions (below) |
| **Content rating** | IARC questionnaire (below) → expect **Everyone / PEGI 3** |
| **Target audience and content** | Age groups **13–15, 16–17, 18+**. Not designed for children; no child-directed appeal |
| **Data safety** | Below |
| **Government apps** | No |
| **Financial features** | The app has no financial features |
| **Health** | The app has no health features (not a medical or wellbeing app) |
| **News apps** | No |
| **Advertising ID** | No. Confirm before answering: `aapt2 dump permissions app-release.apk` (or the AAB's merged manifest) must not list `com.google.android.gms.permission.AD_ID`; the repo has no ads SDK that would add it |
| **Foreground service permissions** | Below (`dataSync`) |

### App access instructions

Paste into *App access › Add instructions*:

> Mnemo has no accounts and no login. Every feature works on a fresh install with no setup, except
> the optional AI features (Smart Extract, Explain/Example/Rewrite, Co-Author), which are off
> until the user adds their own OpenAI-compatible AI provider and API key under Settings › AI
> providers. The developer runs no AI service. To review those screens you need a key for any
> provider of your choice; the rest of the app is fully usable without one. To see content
> quickly, on the first-run screen tap "Create deck and add cards", or "Import from Anki (.apkg)"
> if you have a deck file.

### Content rating (IARC) answers

Category **Reference, News or Educational**. Then, for every question about violence, sexual
content, language, controlled substances, gambling or fear: **No**. User-generated content shared
with other users: **No** (cards stay on the device; there is no feed or account. A deck's Share action only hands an `.apkg` file to the Android share sheet, and the user picks the recipient). Location shared:
**No**. Users can purchase digital goods: **No**. Unrestricted internet access: **No**, the app is
not a browser (it fetches a link only when the user pastes one into Smart Extract, and shows no web
pages). Expect *Everyone*.

If the questionnaire asks about AI-generated content: answer **Yes**, the app can show text an AI
model returns from a provider the user configured; it has a Report action on every output
(release R2). Answer this consistently with the data-safety text below.

### Data safety

Play data-safety form, section by section:

- **Does your app collect or share any of the required user data types?** **No.**
  Rationale: the developer receives nothing; all data stays on the device. Text sent to an AI
  provider goes from the device to the endpoint **the user** configured, only after the user sets
  it up and confirms the in-app disclosure ("Play treats user-initiated transfers to a service the
  user chose as not shared"; re-read the current wording of *Data shared* in the form and, if
  unsure, see the fallback below).
- **Is all of the user data collected by your app encrypted in transit?** Yes, HTTPS for hosted
  providers and links; plain HTTP is allowed only for providers the user marked as local and only
  to local addresses (ADR 0005). If the form insists on a yes/no with no "not applicable", answer
  *Yes*, since nothing the developer collects exists.
- **Do you provide a way for users to request that their data be deleted?** Not applicable (no
  data collected). If asked for a URL, use the `/privacy/` page; deletion is uninstalling, or
  deleting decks in the app.
- **Security practices:** data encrypted in transit (Yes), API keys encrypted with the Android
  Keystore, *Committed to follow the Play Families policy* (No), *Independent security review*
  (No).

**Fallback** if Play's reviewer decides that the AI transfers are "sharing": declare **Shared** for
*App activity › Other user-generated content* and *Files and docs* (card text / source text), purpose
*App functionality*, **optional** (users choose), not required, recipient: the AI provider the user
configures, no advertising and no data sold. Everything else stays as above. The privacy policy
already states this in its AI table.

Also mention for your own answers to review questions: with Android's own device backup on, Android
copies the collection into the user's Google backup (`allowBackup`); that is Google's transfer, not
the developer's, and API keys are excluded (`backup_rules`, `data_extraction_rules`).

### Foreground service declaration (`dataSync`)

The manifest declares `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC`
(`core/data/src/main/AndroidManifest.xml`), used by WorkManager's `SystemForegroundService` for
import, export, backup and media cleanup (`TransferNotifications`, `tryForeground`). Play asks for
this per foreground-service type on release.

- **Type:** Data sync.
- **Describe the task and why the user notices if it is interrupted** (paste):

  > A user-initiated transfer of the user's own files. When the user imports an Anki deck (.apkg
  > or .colpkg), exports a deck, or creates a backup or restore of their collection, Mnemo copies
  > and converts the file, which can take from several seconds to a few minutes for a collection
  > with thousands of cards and images. The transfer runs as a foreground service with a visible,
  > ongoing progress notification so that it finishes if the user leaves the app or turns the
  > screen off. If Android stopped it, the import or backup would be left half-done and the user
  > would have to start again. The service starts only after the user taps Import, Export or Back
  > up; it never runs on its own, ends as soon as the file is written, and the notification is
  > the user's cue that it is running. Nothing is uploaded to any server.

- **User impact if the task is deferred or interrupted:** *Data corruption or loss of the user's
  action; the operation would have to be redone.*
- **Deferrable via WorkManager instead?** Explain that it *is* WorkManager work, promoted with
  `setForeground` only while it runs after a user action.
- **Video (required):** a short screen recording, uploaded to YouTube as **unlisted** (or a Drive
  link with view access) and pasted in the form. Record it on the release build:

  1. Start on the Decks tab with a real `.apkg` on the device
     (`scripts/qa/device-checks.sh push-apkg <file>` puts one in Downloads).
  2. Decks tab › **Import Anki (.apkg)**; pick the file. Show the "Show import progress?"
     dialog (the permission rationale), allow notifications, then the import starting.
  3. Pull down the notification shade: the *Importing Anki deck…* progress notification is visible.
  4. Press Home to leave the app; show the notification is still there and progress advances.
  5. Return to the app; show the imported deck. About 40–60 seconds in total.
  6. Repeat for a backup (Settings › **Back up now**) if it fits in the same video.

  Show the whole screen, keep the notification in frame, and narrate or caption "user taps
  Import → foreground notification → done". A large file makes the notification easy to catch;
  use a real collection, not a 10-card deck.

Android 15 caps `dataSync` foreground services at ~6 hours per 24 hours; imports run for minutes,
so this is far inside the limit. `tryForeground` also copes with the app being in the background
when work starts on Android 12+ (it runs as ordinary work, without the notification).

## 5. Pricing, countries, and store settings

- **Free.** No in-app purchases, subscriptions or ads (nothing to declare in Monetize).
- Countries: all where you may legally distribute; there is no server, so none are excluded by
  service availability.
- *Store settings › Category* Education; *Tags* as above; *Contact details* as above.
- *Release › Setup › App integrity:* leave Play Integrity unused. Mnemo makes no integrity calls.
- Enrol in **Play App Signing** (default) at the first upload; keep the upload keystore as in
  [signing.md](signing.md).

## 6. First upload: Internal testing

1. Bump `mnemo.versionCode` if any earlier upload used it, follow the release routine in
   [signing.md](signing.md) up to the signed `bundleRelease`, and run the 16 KB check on the AAB.
2. **Testing › Internal testing › Create new release**. Accept Play App Signing at the prompt, then
   upload `app/build/outputs/bundle/release/app-release.aab`. Upload
   `mapping.txt` (Play takes it from the bundle) for readable crashes.
3. Release name: the version name (for example `1.0.0 (1)`). Release notes (per language):

   ```
   <en-US>
   First public test build of Mnemo: FSRS-6 study sessions, Anki import and export, cloze,
   type-in and multiple-choice cards, analytics, and optional AI card creation with your own
   provider. Please report problems at https://github.com/yahyafati/mnemo/issues
   </en-US>
   ```

4. **Testers › Create email list** with your own address(es), and open the opt-in link on a device
   signed into that account. Install from Play, not from `adb`.
5. Open **Pre-launch report** (Release › Testing) after Play finishes it (a few hours). It runs
   the app on Firebase Test Lab devices with a robo crawler. Treat as work: crashes, ANRs, and
   the accessibility results (touch-target size, contrast, missing labels). Fix, bump
   `versionCode`, re-upload. Ignore findings that are only a crawler unable to pick a file.
6. Look at **Policy status** and **Publishing overview**: warnings there (permissions, target
   API level, 16 KB pages, missing declarations) must be fixed before R5.

### Checks after the first install from Play

Different from the `bundletool` install in R3, because Play delivers split APKs signed with the
app-signing key:

- [ ] The app starts, onboarding shows, the launcher icon and splash look right.
- [ ] Import an `.apkg`, run a study session, back up and restore (proves the WorkManager
      foreground path and file pickers work on the Play build).
- [ ] Settings › About › Open-source licenses opens, and the privacy policy link loads.
- [ ] Airplane mode: study and import still work.
- [ ] Update path: upload a second build with `versionCode + 1`, update through Play, and check the
      data survived (R3's migration check, this time through Play).

## 7. Exit checklist (R4)

- [ ] Verified developer account, contact details public and correct
- [ ] Contact email in the privacy policy; policy live at the Pages URL and linked from the app
- [ ] Main store listing complete (texts, icon, feature graphic, ≥ 2 phone shots, tablet shots)
- [ ] Every **App content** section green: privacy policy, ads, app access, content rating, target
      audience, data safety, foreground service, and the "not applicable" declarations
- [ ] Internal test build installed from Play on a real device; pre-launch report reviewed
- [ ] Policy status shows no warnings

When this list is done, tick the boxes in [ROADMAP.md](ROADMAP.md) R4 and start R5.
