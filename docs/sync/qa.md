# Sync: QA runbook (sync ROADMAP S8)

The pass that decides whether sync is announced in a release. It is the sync twin of the desktop runbook
([../desktop/qa.md](../desktop/qa.md)) and the Android one ([../release/qa.md](../release/qa.md)): same layout, same
results-log rules. What the tests already prove (`SyncMergeTest`'s scenarios, `SyncConvergenceTest`'s random
histories, every store against its contract on MockWebServer and a temporary directory) is **not** repeated here. This pass
is for what no test can show: two real devices, real file-sync tools, a real Google account and a real
Nextcloud, a phone's clock and battery, and a person reading what the screens say.

Record every result in [the results log](#results-log). Never put a passphrase, a Google token, a WebDAV password or a
file's contents in an issue, a screenshot or this file: describe them ("a 12-character passphrase with a space").

**Not bugs** (known limits, listed in the release notes): two devices that each have their own collection cannot be merged (one
joins the other and is replaced, after a copy is saved); an answer given on top of one that another device undoes before
they sync keeps the schedule computed on top of it (every review is kept, and all devices agree; ADR 0013 "As built (S3)");
AI providers and keys, appearance, the reminder and backup settings do not sync; a folder is only as fast as the tool carrying it.

## 0. Set up

**Devices** (the minimum is one row of each kind; "a second desktop" can be a second data folder on one machine, see below, for the
quick pass, but the pass that counts uses separate hardware):

| Role | What | Used for |
|---|---|---|
| Phone | Android 10+ with the **release** build from the draft release (R8), **and** a second phone or tablet if you have one | §2, §5 (clock, airplane mode), §6 (battery, data), §8 (Drive sign-in) |
| Desktop A | The installer of one OS (any), real display | §1–§4 |
| Desktop B | A different OS from A if you can (Windows ↔ macOS ↔ Linux): file-name rules, keychains and file-tool behaviour differ | §1, §3 |
| Quick-pass stand-in | One machine, two data folders: `MNEMO_DATA_DIR=~/mnemo-a <app>` and `MNEMO_DATA_DIR=~/mnemo-b <app>` (two windows, each with its own database, lock and keychain entry) | A first sweep of §1, §3, §4, §7, §9–§11 before the hardware pass |

You also need: a Google account that is **not** the owner's day-to-day one, with the app's consent screen in a state that allows it
(Testing status expires refresh tokens after seven days: for the real pass the screen must be published and verified, see
`google-setup.md`) and a build with the client ids; a Nextcloud (or other WebDAV) test account **with an app password**, ideally behind the
reverse proxy you really use; a file-sync tool between the phone and a computer (Syncthing, or the Nextcloud app on both); a
12,000-card package (`scripts/qa/make-large-apkg.py`, see `desktop/qa.md` §2.2); and a collection of your own with real review history if you have one.

**Get the build.** The same draft release as the other runbooks: the APK and the installers, `desktop-checks.py verify-sums`
on them. Check Settings › About on every device: **all devices must run the same build** for every check below except §11, which
needs an older one.

**Automated exit check, first** (on any one machine, from the commit being released). The sync tests are in these:

```bash
./gradlew assembleDebug testDebugUnitTest testAndroidHostTest lint verifyRoborazziAndroidHostTest
./gradlew :core:ai:test :core:anki:test :core:model:test :core:scheduler:test :core:common:test :core:sync:test
./gradlew desktopTest :desktop:test
MNEMO_SYNC_SEEDS=250 ./gradlew :core:data:desktopTest --tests "*SyncConvergenceTest*"   # more random histories than CI runs
python3 scripts/desktop/check-android-imports.py
python3 scripts/fdroid/check-foss-deps.py
python3 scripts/fdroid/fastlane.py
```

**Helpers** (all print what they did; none changes a collection or a sync folder):

```bash
python3 scripts/qa/sync-checks.py folder <sync folder>        # files by kind and size, every file's length and checksum, each device's change numbers
python3 scripts/qa/sync-checks.py compare <data dir A> <data dir B>   # the same decks, notes, cards, review logs, media?
python3 scripts/qa/sync-checks.py watch <sync folder>         # size and file count over time (data use)
python3 scripts/qa/desktop-checks.py counts --data <data dir> # integrity and counts of one collection
scripts/qa/device-checks.sh battery reset | battery report <label> | data <label> | sync-work | airplane on|off | frames …
```

`compare` takes two data folders (each holds `mnemo.db`; the desktop's is in `install.md`, or whatever `MNEMO_DATA_DIR` points at). A phone's
collection isn't reachable that way without root: for the phone, compare **Decks** and **Browse** counts and a few cards' due dates by eye against
`counts` on the desktop, and write the numbers down. Wherever this runbook says "`compare` matches", that is the check for two desktops, and the by-eye one for a phone.

**Reset between runs.** A device that left sync and a scratch data folder start clean. A *location* is reset by Settings › Sync ›
Delete the sync data, or by emptying the folder. Never rename or move a sync folder while a device uses it.

**"Offline"** in this runbook means: Wi-Fi off (Drive and WebDAV), or the file-sync tool **paused** on that device (folder). For a folder that
two windows on one machine share, there is no offline: use two data folders and *Stop syncing*/*Sync now* order instead, or skip the row.

## 1. Folder sync, desktop and desktop

Start on Desktop A with a collection that has real content (the 12,000-card package or your own) and Desktop B **empty** (a fresh data folder).

- [ ] **Create.** A: Settings › Sync › **Use a folder** › pick a new empty folder. The dialog says to use a folder just for this, offers a passphrase
      (with 8 characters at least, entered twice, Show/Hide), and **Start syncing** begins. The status reads *Up to date* with a last-synced time;
      the Settings row says *On. Last synced …*. `sync-checks.py folder` shows `sync.json`, `encryption on`, a snapshot, and every file's checksum intact.
- [ ] **Join.** B (empty): the same folder (through the sync tool, or the same shared drive) › it says the folder holds sync data, asks for the passphrase
      (if encrypted) and joins. No "replace" warning on an empty B. B shows the collection: `compare` says the same rows, review history and
      media open, the scheduling settings (retention, limits) equal A's, and the AI providers are **not** there.
- [ ] **A wrong passphrase** at the join dialog: *That passphrase is wrong.* at once, nothing is written to B, and the right one then works.
- [ ] **Each direction.** Edit a note on A, study 10 cards on B, add a deck on A: after the next sync (it runs about 5 s after an edit; or **Sync now**,
      ⌘⇧S / Ctrl+Shift+S, File › Sync now) both show the same, and **Undo** of an answer on B shows as undone on A.
- [ ] **Sync now** with sync off opens Settings › Sync; with sync on it shows a snackbar (done, or "didn't finish"). The `?` sheet lists the shortcut and
      File › Sync now exists on Windows and Linux's menu bar and on macOS's.
- [ ] **The device list** (Settings › Sync › Devices) names both computers (the OS's host name), their platform and "seen …"; this one says *(this device)*.
- [ ] **Join with data.** C (a third data folder, or B after *Stop syncing*) with a deck of its own joins the folder: it first says it will **replace** what is there and that a
      copy is saved first, and after joining shows where (`<files>/sync-backups/`). Open that copy through Restore or look at the file: the old decks are in it.
- [ ] **Strangers' files.** Put a `notes.txt` and a `.DS_Store` in the sync folder, and (if you can make one) a conflict copy such as `devices__x__changes__3 (1).mnc`
      or Syncthing's `.sync-conflict-` file: Mnemo ignores them, and a folder with *only* other files is treated as empty (the create dialog's warning applies).
- [ ] The Decks banner **does not appear** while everything works, however many syncs ran.

## 2. Folder sync, phone and desktop

Through the file-sync tool, on real hardware. Do §1's Create/Join/each-direction rows with the **phone as the joining device**, then once with the desktop joining a
phone-created folder (the folder picker on Android uses the storage access framework: pick a folder the tool also watches, not the app's own folder).

- [ ] The picker keeps working after a **phone restart** (the permission to the folder is kept: `keepAccess`), and after the sync tool restarted.
- [ ] A phone-created folder holds the same file names a desktop-created one does (`sync-checks.py folder` on the folder, from the computer).
- [ ] **Time.** Edit on the phone with the screen on, stopwatch until it appears on the desktop (tool included) and back: record both. The app's part is the 5 s debounce plus a
      round; the rest is the tool.
- [ ] **The phone studies in a session** (20 cards) and the reviews are on the desktop afterwards, with the same next-due dates for those cards on both.
- [ ] **A big first upload on a phone** (the 12,000-card package imported on the *phone*, then Start syncing): the foreground notification or progress is sensible, the phone stays
      usable, it finishes, and the desktop joins it (record both durations).

## 3. The merge scenarios by hand

`SyncMergeTest` runs these with in-memory devices. Do them once with two **real** devices (one phone, one desktop; or two desktops on different OSes) and the same
folder or Drive. Go offline on both, make the changes, bring A online and sync, then B and sync, then A again: each scenario ends with the check named.

| # | On both offline | Expect after both have synced |
|---|---|---|
| 1 | Review the **same card** on A and on B (different ratings) | Both reviews are in Browse › card history on both; the card's due date and stability are the same on both and equal what answering both in time order gives (note the card, the two answers, and the result in the log) |
| 2 | A edits a note's **front**, B its **back** | Both edits on both |
| 3 | Both edit the **same** field; B edits *last* by the wall clock | The later edit wins on both; the other is gone from the field (not duplicated) |
| 4 | Set A's clock **a day ahead** (§5), edit on A, sync, put the clock right, then edit the same field on B (after syncing) | B's edit wins: B had seen A's change |
| 5 | A **deletes** a note, B edits it | Deleted on both |
| 6 | Create "Spanish" on A and "spanish" on B, each with notes | One deck on both with all the notes (the lower id wins, the other is deleted) |
| 7 | Online: A answers a card and syncs, B syncs (it has the review). Then offline: A **undoes** the answer | After both sync, the review shows as undone on B too, and B's schedule for the card is the one from before that review |
| 8 | Kill the app during a sync (§5) | Nothing lost or doubled |
| 9 | Import the 12,000-card package on A | B gets all of it; the app's time on B (from "Syncing…" to "Up to date") is in the log |

- [ ] 1–9 each, recorded one by one. A mismatch between the devices after both synced twice is a P0.
- [ ] A **deck's exam date**, a **flag** and a **suspend** set on one device show on the other.
- [ ] **A media card** (an image and a sound) made on A shows the image on B (the placeholder may show for a few seconds until its file arrives, then the image).
- [ ] **Settings:** change *Desired retention* on A, sync, B shows the new value; B's own **appearance** and **reminder** stay as they were; run **Optimize** on A and B takes the
      fitted weights.
- [ ] **Same history twice.** Run the whole of 3 once more with **3 devices** (A, B and the phone) and a 3-way edit of one field: all three agree.

## 4. Passphrases, locations and the Decks banner

- [ ] **Unencrypted location:** the create dialog's passphrase switch, off, says what it costs; Settings › Sync says *Not encrypted: anyone who can open the sync location can read the files*;
      `sync-checks.py folder` says `encryption OFF`, and every file's envelope is intact (the bodies are compressed, so card text isn't greppable even here, which is not a protection).
- [ ] **Encrypted location:** `grep -r "<a word from a card>"` over the folder finds **nothing** (a change file, a snapshot and a media file). The passphrase appears in no file of the
      folder, in `<data>/sync/config.json`, in a backup or in any log line (`grep -r` for it in the data folder, a backup unzipped and the logs).
- [ ] **A device that lost its key** (on a scratch desktop data folder, quit the app and delete its `secrets` folder, which also removes AI keys; or restore a backup of a syncing device onto a device that never had the key): the status is *The sync data is encrypted and this device doesn't have its passphrase*,
      **Enter passphrase** asks for it, a wrong one is refused, the right one resumes without rejoining.
- [ ] **Passphrase forgotten** (a new device that cannot join): follow [help.md](help.md) "I forgot the passphrase" as a user would, from the screens alone. It must be possible to
      get everything working again from a device that still has the collection, and the doc must match the screens. Fix the doc or the app.
- [ ] **A newer format:** make a *copy* of a sync folder, edit `sync.json`'s `formatVersion` to `99`, and let a fresh data folder join the copy: *The sync data is from a newer version of Mnemo. Update Mnemo on this device to keep syncing.*
      Nothing was written to the copy, and `Sync now` on the other devices (which use the real folder) is unaffected.
- [ ] **A damaged file:** in another copy, cut a change file short with `truncate -s -20` (or edit one byte). `sync-checks.py folder` flags it; a fresh device joining the copy
      ignores it, shows no crash, and keeps going (the data of that file is missing, and says so in the logs if any). Do not do this to a folder in use.
- [ ] **A missing file:** delete one change file of a device (copy again): the same, no crash.
- [ ] **The banner.** With sync on, make a problem that only a person can fix (stop the folder's tool and **delete** the folder, or change the passphrase's key as above): the Decks banner appears
      **at once** with its action and the status card says the same. A problem that may mend itself (offline) shows the banner only after a day: leave one device offline with sync on for 24 h
      (airplane mode overnight) and look; before that nothing is shown on Decks, only in Settings.
- [ ] **Stop and rejoin.** B › Stop syncing (asks first) › B keeps its cards and A keeps syncing; A's device list still shows B only until it ages out. B edits, A edits, B › join again: it warns that the collection is replaced
      and saves a copy first; afterwards B equals A, and B's offline edits are in the copy (and not in the collection: say so in the log).
- [ ] **Delete the sync data** on A (asks first, names the other devices): the folder is emptied of Mnemo's files (`sync-checks.py folder`), A's cards stay, B's status says the location has no sync data, and B can
      *Stop syncing*, and both can start again from scratch.
- [ ] **A refused or deleted folder** (the sync tool's folder removed, or its permission revoked on Android): a readable message with *Stop syncing*, no crash, no loop of notifications.

## 5. Clocks, interruptions and the network

- [ ] **A phone with a wrong clock.** Settings › System › Date & time on the phone: switch automatic time off and set it **one day ahead**, then a day **behind**. In each case: edit on the phone, sync, edit the same field on
      a desktop **after** it has synced, and check the later change (by the real time) wins as in §3 row 3/4 describes. Study on the phone: the next-due dates in Decks on both devices agree after syncing. Put the clock
      back. (The logical clock moves past what the phone has seen, so the *order of edits* is right after the first sync; what a wrong clock still changes is the phone's own wall-clock timestamps in the history.)
- [ ] **Airplane mode during a sync** (phone, Drive or WebDAV): start Sync now with a big change pending (just after an import), switch airplane mode on after a second (`device-checks.sh airplane on`). The status becomes *Can't reach the sync location right now. Mnemo will try again.*, no error
      dialog, and with the network back (`airplane off`) it finishes by itself, nothing doubled or missing (`compare`).
- [ ] **Kill mid-sync.** Desktop: `kill -9` the process during a big sync; start again, it resumes and `compare` matches. Phone: swipe the app away or `adb shell am force-stop com.yahyafati.mnemo` during one. In a folder: `sync-checks.py folder` flags no file *damaged*
      afterwards (a partial file may be there, `incomplete`, and the owner device replaces it on the next round).
- [ ] **Low storage on the phone** while joining a big collection: a readable message, the old collection untouched (the copy it makes first is the large part; note the free space needed).
- [ ] **Sleep and wake** a laptop with sync on: it syncs after waking without a restart.
- [ ] **Two syncs at once** (press Sync now on A and B at the same moment, five times): no error, `compare` matches after both are idle.
- [ ] **A big edit while the other device is mid-study:** the study screen on B is not interrupted (the card on screen stays; the queue may change at the next card).
- [ ] **An old device comes back** (optional, a full pass of the 90-day rule needs a scratch clock): the status says to join again, and joining saves a copy first. `SyncLifecycleTest` covers the rule; the manual check is the wording.

## 6. A large collection, and battery and data use on Android

- [ ] **12,000 cards.** A imports `large.apkg`, then Start syncing; B joins. Record, for A's first upload and B's join, the time from the dialog to *Up to date* and the folder's size
      (`sync-checks.py folder`): files, MB, and how many are change files and snapshots. Repeat with the **phone** as A and as B. The proposed target (S3 noted "decided after measuring"): the app's own part under
      60 s on a desktop and under 3 min on a mid-range phone for 12,000 cards; slower is a P2, a frozen UI a P1. (ADR 0013: 1.6 s to pack and 1.3 s to apply on the author's desktop, so what you see is mostly the tool or the network.)
- [ ] **Memory.** The phone and the desktop while joining the large collection: no out-of-memory, the desktop's process stays under 1.5 GB (as in `desktop/qa.md`).
- [ ] **Steady state.** After the first sync, add 20 notes and study 50 cards on A; the sync that carries them is small: `sync-checks.py watch` shows well under 1 MB of new files (record it).
- [ ] **Compaction.** Study a few cards every day for a few days (or run many Sync-now rounds) so that the change files pass the snapshot threshold; the folder then gets a snapshot and the oldest change files go (only once every device has applied them): `sync-checks.py folder` before and after, written down. The folder does not grow without bound over two weeks of normal use.

### Background sync: battery and data (Android, record in the log)

The helpers talk to `adb`, so they need a device with USB debugging, and the numbers are only meaningful on a device that is **unplugged** in between (the helper's `battery reset` tells Android to treat it as unplugged;
the report puts it back). None of these helpers has been run on hardware yet (they were written for this pass): if a command prints nothing, read the raw `adb shell dumpsys batterystats` and `dumpsys netstats detail` yourself and fix the script.

1. Release build, sync on (a folder, then Drive, then WebDAV, one day each), the screen off, Wi-Fi on, normal use: nothing else.
2. `scripts/qa/device-checks.sh sync-work`: the periodic job exists, has a **network constraint** and a period of hours (not minutes).
3. `scripts/qa/device-checks.sh battery reset`, wait 24 h, `battery report "folder, 24 h"`; and the same with sync **off** (the baseline: Mnemo's share should be about zero).
4. `scripts/qa/device-checks.sh data "folder, 24 h"` before and after: the bytes Mnemo sent and received in the background.
5. Add the figures to the log. The proposal, to be settled by what the numbers say: **under 1 % of the battery and under 5 MB for a day on which nothing changed**, and a normal study day within a few MB (Drive and WebDAV list every
   file each round; the folder backend reads only the local storage). Anything over is a P2 for the interval and the list; a wake-up every few minutes is a P1.
6. Data saver, battery saver and "restricted" background usage (Settings › Apps › Mnemo › Battery): the periodic sync may not run, and the app must not complain: on the next open it syncs (the Settings row's *Last synced* shows it).
- [ ] No import/export-style foreground notification appears for a routine sync (only a big first upload, if at all; write down what you saw).

## 7. Google Drive

Needs the build with the client ids (`google-setup.md`) and the consent screen verified; do it on the **release-signed APK** (the Android client is bound to its certificate's SHA-1) and on a desktop installer. A debug build only works with its own
client. An F-Droid build must show **no** Drive option.

- [ ] **Sign-in on the phone.** Settings › Sync › **Use Google Drive**: the browser (a Custom Tab) opens Google's account chooser, the consent page names **Mnemo** and asks for exactly one permission (*See, create and delete its own configuration data in your Google Drive*), and after consent
      the redirect brings Mnemo to the front by itself (no "no app can open this link"). Cancelling in the browser says *Sign-in was cancelled* and offers nothing broken. (If the custom-scheme redirect is refused: ADR 0013's Android spike, the one line is in `google-setup.md`.)
- [ ] **Sign-in on the desktop** (loopback): the system browser opens, Mnemo says it is waiting, and returns when the browser shows its "you can close this tab" page; the window comes to the front. A **Cancel** while waiting frees the port (try again right away). Two sign-ins in a row work.
- [ ] **Create** on one, **join** on the other (the same Google account): passphrase on by default for Drive; the screen's wording does not name an email address (the scope has none). The Google Drive web UI shows **no new file** (the app-data folder is hidden), and
      Drive's *Manage apps* page (drive.google.com › Settings › Manage apps) lists Mnemo with *Hidden app data*, the size roughly equal to the folder's in §6.
- [ ] **Everything in §1 and §3** once with Drive as the location (a short pass: create, join, edit both ways, scenarios 1, 5, 6 and 9 by hand).
- [ ] **Revoked access.** myaccount.google.com › Security › *Third-party apps with account access* › Mnemo › **Remove access**. On both devices the next sync fails with *Mnemo is no longer signed in to Google*, the Decks banner appears **at once**, and **Sign in to Google** brings it back
      without losing anything (and without a rejoin). The token can also expire by itself while the consent screen is in Testing status (after seven days): if you can, leave a Testing build a week once and note what the app says.
- [ ] **Sign out** by Stop syncing: the token is forgotten (check Google's page: Mnemo stays listed until revoked, which is expected; the privacy policy says so). Joining again signs in again.
- [ ] **A full Drive.** If you have an account you can fill (a Workspace account with a small quota, or a free one with about 15 GB used): sync with less than the collection's size free: *Your Google Drive is full…*, the banner at once, nothing half-applied, and freeing space resumes it.
      If you can't fill one, mark ➖ and keep the MockWebServer test (`GoogleDriveSyncStoreTest`'s quota case) as the evidence.
- [ ] **Network off during a big upload** (resumable above 5 MB): airplane mode, then back: the upload finishes or restarts cleanly, no duplicated media, `compare` matches.
- [ ] **Delete the sync data** on Drive: the hidden folder's files are gone (Manage apps shows a size of about zero), and the device is signed out.
- [ ] A Drive account that is also used for a **synced folder** (Google Drive for Desktop): nothing of Mnemo's REST backend appears in that folder, and a folder inside it works as in §1.
- [ ] Network: with Drive sync on, the only hosts the app talks to (§9) are Google's.

## 8. WebDAV (Nextcloud and others)

Use the real server and the proxy in front of it. Create an **app password** in Nextcloud (Settings › Security) for the test and delete it afterwards.

- [ ] **The form.** *Use a WebDAV server*: `https://…/remote.php/dav/files/<user>/Mnemo`, the user name and the app password. **Test connection** says *Connected. Mnemo will make the folder.* for a new folder and *Connected. The folder is there.* for an existing one;
      a wrong password says the server refused it; a wrong folder one level too deep says the folder above it doesn't exist; `http://` to a public host is refused as you type, and `http://192.168…` is accepted (and works) for a server on the LAN.
- [ ] **Create / join / each direction** (§1) with WebDAV in place of the folder, on a desktop and the phone. The Nextcloud web UI shows the one folder with Mnemo's `devices__…`, `snapshots__…`, `media__…` and `sync.json` files, and no other file in your account is touched.
- [ ] **The password is kept right:** after joining, Settings › Sync shows the address and user but **never** the password; it is not in `<data>/sync/config.json`, a backup or the logs (`grep`); *Stop syncing* forgets it (joining again asks for it).
- [ ] **Wrong or revoked app password** (delete the app password in Nextcloud): *The WebDAV server no longer accepts…* with **Enter password**; the form has the address and user fixed; the new password resumes syncing without a rejoin; a wrong new one is **not kept** (still the old problem).
- [ ] **Conditional create.** Two devices **Start syncing** into the same new folder at the same moment: one creates, the other gets *already holds sync data* and is offered the join, or *can't use this folder*. Never two collections in one folder. (Needs `If-None-Match: *` to be honoured: Nextcloud and Apache do; if your server answers `204` instead of `201`
      to a second create, write that in the log: ADR 0013 describes the consequence.)
- [ ] **Maintenance mode** (`occ maintenance:mode --on`): a sync during it shows the offline/"will try again" state, not an error that needs action, and it recovers by itself after `--off` (Nextcloud answers 503, which the store retries with a pause).
- [ ] **A body-size limit.** Behind a reverse proxy with a small `client_max_body_size` (for example 5 MB) sync the 12,000-card collection: the snapshot or the largest media file is refused with `413`; Mnemo says the server refuses a file this large and does not loop. Write down the limit that works.
      Also with **no** limit: the first upload finishes (record the time and the folder's size).
- [ ] **A server with redirects** (HTTP → HTTPS, or a trailing-slash redirect): Mnemo does not follow them with the password; it says to use the final address; the corrected address works.
- [ ] **Self-signed or expired certificate:** refused with a readable message (Mnemo never accepts one), and a proper certificate works.
- [ ] **Network:** with WebDAV on, the app talks to that host only (§9).

## 9. Privacy: what leaves the device

- [ ] **Sync off = no connection.** With sync never turned on, the desktop process has no connection of its own after a study session (`desktop/qa.md` §6's `lsof`/`ss`/`netstat`), and on the phone Settings › Sync says *Off*. After **Stop syncing**, the same: no connection, no periodic job
      (`device-checks.sh sync-work` shows no SyncWorker; give WorkManager a minute).
- [ ] **Folder:** Mnemo itself opens no connection; the sync tool does. **Drive:** the app's connections go only to Google (`accounts.google.com` in the browser for sign-in; `oauth2.googleapis.com` and `www.googleapis.com` from the app). **WebDAV:** only the one host.
- [ ] **What is written.** Unpack nothing, but list the folder: only `sync.json`, `devices__…`, `snapshots__…`, `media__…`. In an encrypted location `sync.json` holds the format version, a random collection id, a creation time, the salt and a check value, and **nothing else** (read it).
- [ ] The privacy policy (`docs/release/privacy-policy.md`, "Optional sync"), the in-app text in Settings › Privacy and the Sync screen's own sentence all say the same as what you saw; the Play data safety form matches (owner).
- [ ] **No key or token in a backup.** Back up with sync on (Drive and WebDAV), unzip: no refresh token, no WebDAV password, no passphrase key (`grep` for each, without pasting them anywhere), and the restored copy comes back with sync **paused**, not signed in.

## 10. Restore, backup and the collection around sync

- [ ] **Restore on one device.** B: Settings › Data › Restore a backup made *before* some changes. After the restart Settings › Sync says *Sync is paused* and the Decks banner shows at once. The two choices:
      **Upload this collection as the new sync data** names the devices that must join again and, when confirmed, replaces the location (A's status becomes "another device started the sync data again…", and **Join again** replaces A's collection after saving a copy);
      **Use the sync data again** discards the restore after saving a copy. Try both (restore twice).
- [ ] **A restored backup on a second device does not pose as the first:** restore the same backup on a spare device and look at Devices: two entries, not one.
- [ ] **Backup restore from before sync existed** (a backup file of an older version, from the previous release): it restores, sync is off, and turning it on works.
- [ ] **Automatic backup** keeps working with sync on; a backup made on a syncing device contains no sync location secrets (§9) but does keep the location's address, and the restored device asks to choose (the paused state), as above.
- [ ] **Import into a syncing collection:** import the 12,000-card `.apkg` on A while B and the phone are online: all three converge, no duplicates of imported cards (Anki's guid skips a second import).
- [ ] **Anki export** of a synced deck on B opens in Anki with the notes, media and scheduling of both devices' reviews.
- [ ] **Update over an older release** (previous APK/installer with sync not yet existing): the app migrates the database (schema 7), sync is off, nothing changes for the user, and onboarding does not appear.

## 11. Docs and wording

- [ ] [help.md](help.md) walks through to the same screens and button names the app shows, on one desktop and one phone: set up, join, new phone, forgot the passphrase, restored a backup, joined the wrong folder, stop and delete.
- [ ] `docs/desktop/install.md` "Moving a collection" names sync and still lists backup and `.apkg` as the other paths; its data-location table mentions `sync/config.json` (`<data>/sync`).
- [ ] The release notes (`docs/release/release-notes.md`), the F-Droid changelog for this `versionCode`, the store texts and the F-Droid page (`docs/release/fdroid.md`) all describe sync with what this pass found (Drive on the phone and desktop builds that have a client id, hidden in F-Droid; folder and WebDAV everywhere), and the
      "Good to know" list has this pass's P2 and P3 findings.
- [ ] `CLAUDE.md`, ARCHITECTURE and ADR 0013 say what was built, including what this pass changed.
- [ ] The desktop strings that say "this computer" and the phone's "this phone" are right where the Sync screens use them (`PlatformCapabilities`), and none says "folder" on a Drive or WebDAV screen.
- [ ] **Accessibility** on the phone with TalkBack once through Settings › Sync: the status, the buttons, the dialogs and the device list are announced and reachable; the Decks banner's action too. Dark theme and a large font on the Sync screen: nothing clipped (a screenshot of the dialogs is enough).

## 12. Triage

- [ ] Every failure above is a GitHub issue labelled `P0`–`P3` and `sync`, naming the devices, OS, build and backend. P0: a review or note lost, devices that don't converge after both synced twice, a corrupted collection, a secret in a file or log, or a crash at launch;
      P1: a backend's core flow broken (create, join, sync, sign in, unlock), a sync that never finishes, a wrong "everything is fine" status, or a background sync that drains the battery; P2: wrong but recoverable (wording, a slow first sync, a banner that comes late); P3: polish.
- [ ] **No open P0 or P1.** P2 and P3 are listed under "Good to know" in the release notes or deferred.
- [ ] Only then: the release notes say sync is in this release (owner), and the store listing, F-Droid texts and Play's data safety form (owner) are updated.

---

## Results log

One row per check per device or backend. **Result:** ✅ pass, ❌ fail (link the issue), ➖ not applicable. Build = the APK or installer's file name and the commit or tag.

| Check | Devices / backend | Build | Result | Notes |
|---|---|---|---|---|
| 1 Create, join, each direction (folder) | | | | |
| 1 Wrong passphrase at join | | | | |
| 1 Join replaces data, copy saved | | | | |
| 1 Other files in the folder ignored | | | | |
| 2 Phone and desktop through a sync tool | | | | |
| 2 Folder permission survives restart | | | | |
| 2 Propagation time (phone → desktop / desktop → phone): s / s | | | | |
| 2 Big first upload from a phone (time: ) | | | | |
| 3 Scenario 1: same card reviewed on both | | | | |
| 3 Scenario 2: front and back | | | | |
| 3 Scenario 3: same field, later wins | | | | |
| 3 Scenario 4: clock a day ahead | | | | |
| 3 Scenario 5: delete vs edit | | | | |
| 3 Scenario 6: "Spanish" twice | | | | |
| 3 Scenario 7: undo after sync | | | | |
| 3 Scenario 8: killed mid-sync | | | | |
| 3 Scenario 9: 12,000 cards (time on B: ) | | | | |
| 3 Media card, flags, exam date, settings | | | | |
| 3 Three devices, one field | | | | |
| 4 Unencrypted location | | | | |
| 4 Encrypted: nothing readable, no passphrase on disk | | | | |
| 4 Lost key: Enter passphrase | | | | |
| 4 Passphrase forgotten (help.md works) | | | | |
| 4 Newer format refused | | | | |
| 4 Damaged and missing files | | | | |
| 4 Decks banner: at once / after a day | | | | |
| 4 Stop, rejoin, delete the sync data | | | | |
| 5 Phone clock a day ahead / behind | | | | |
| 5 Airplane mode during a sync | | | | |
| 5 Kill mid-sync, desktop and phone | | | | |
| 5 Low storage, sleep and wake, two syncs at once | | | | |
| 6 12,000 cards: times and folder size (desktop / phone) | | | | |
| 6 Steady state size, compaction | | | | |
| 6 Battery, 24 h: sync on / off (% and mAh) | | | | |
| 6 Data, 24 h background (MB) | | | | |
| 7 Drive sign-in, phone | | | | |
| 7 Drive sign-in, desktop | | | | |
| 7 Drive create, join, scenarios | | | | |
| 7 Drive revoked access, sign in again | | | | |
| 7 Drive full | | | | |
| 7 Drive delete sync data | | | | |
| 8 WebDAV form, test connection | | | | |
| 8 WebDAV create, join, scenarios | | | | |
| 8 WebDAV wrong or revoked app password | | | | |
| 8 WebDAV maintenance mode, redirects, certificates | | | | |
| 8 WebDAV body-size limit (limit found: ) | | | | |
| 9 Sync off: no connection | | | | |
| 9 Hosts contacted per backend | | | | |
| 9 No key, token or passphrase in a file or backup | | | | |
| 10 Restore on one device, both choices | | | | |
| 10 Restore doesn't pose as another device | | | | |
| 10 Import on a syncing collection | | | | |
| 10 Update over the previous release | | | | |
| 11 help.md and install.md walked | | | | |
| 11 TalkBack, dark, large font | | | | |
| 12 No open P0/P1 | | | | |

## Dry run (automated, before the manual pass)

What the machine could already show, on the author's computer, so the manual pass starts from known ground. It is **not** a substitute for any row above: no second device, no real provider, no eyes on a screen.

| What | Machine | Result |
|---|---|---|
| `sync-checks.py folder` on a synthetic folder (good files, a file cut short, a gap in the numbers, a stranger's file) | macOS 26 arm64 | flags the cut file as incomplete (exit 1), reports the gap and leaves the stranger's file out; exits 0 on the repaired folder |
| `sync-checks.py compare` on two copies of the v7 fixture, then with one deck renamed | same | "same rows" (exit 0); `differs: d1 in name` (exit 1) |
| `:core:sync:test`, `:core:data:desktopTest` and `:core:data:testAndroidHostTest` (the sync tests among them) | same | pass |
| `SyncConvergenceTest` at `MNEMO_SYNC_SEEDS=250`, first run | same | **failed at seed 120 in the test harness** (a note whose deck's file hadn't arrived left no live deck to move it to); fixed, see ADR 0013 "As built (S8)" |
| `SyncConvergenceTest` at `MNEMO_SYNC_SEEDS=250` and `1000` after the fix | same | pass: 1,000 seeds of two devices in 31 s and of three devices in 46 s; every device ends with the same rows and every review |
| `check-android-imports.py`, `fastlane.py` | same | ok |
| Not run by a machine | | the whole manual runbook above; `device-checks.sh battery`, `data` and `sync-work` have never run on a phone |
