# Mnemo — sync roadmap

This roadmap adds **sync between a user's devices** (phone, tablet, desktop) through **storage the user
owns**. There is no Mnemo server. Each device writes its own changes as files to a shared place, and reads
and merges the other devices' files. The shared place is a **backend** behind one small interface:

1. **A folder** the user picks (first). On the desktop it can sit inside Google Drive for Desktop,
   Dropbox, Nextcloud or Syncthing, so those users are covered from the first release.
2. **Google Drive** through its REST API (second), for phones, where a synced folder is not practical.
3. **WebDAV** (Nextcloud and others), optional and after Drive.

Most of the work is the **merge**, not the backends: two devices that studied and edited offline must
end up with the same collection, losing no review. It is written for agents: each step lists what to
build, where, and how to know it is done. The product roadmap is [../ROADMAP.md](../ROADMAP.md) (where
sync is "Later"); the architecture rules are [../ARCHITECTURE.md](../ARCHITECTURE.md) and `CLAUDE.md`;
the related work is the desktop app ([../desktop/ROADMAP.md](../desktop/ROADMAP.md)), backup and restore
(`BackupManager`) and the licence rules (ADR 0009). Read those first.

**Defaults (proposed 2026-10-03, confirmed by the owner the same day; ADR 0013):**

| Question | Proposed default |
|---|---|
| Server | **None.** Sync goes through storage the user owns (`PROJECT_OVERVIEW.md` §6: "file-based sync through user-owned storage"). A self-hosted Mnemo sync server is out of scope. |
| Sync model | **Change files per device, never the database file.** Each device appends its own numbered change files and never writes another device's files, so the backend needs no locking and has no write conflicts. Copying the SQLite file loses one device's reviews when both studied offline, and SQLite files in synced folders get corrupted. |
| Conflicts | **Merged automatically, no conflict dialog.** Review logs are unioned. The schedule of a card reviewed on two devices is **replayed** through FSRS. Other fields are last-writer-wins **per field**, ordered by a hybrid logical clock, so that a phone with a slow clock still orders after what it has already seen. Delete wins over a concurrent edit. Rules per table are in S3. |
| What syncs | Decks, note types, notes, cards, review logs, media, saved AI answers (`ai_answers`), and the **scheduling** settings (retention, limits, steps, fitted FSRS weights). **Not synced:** AI providers, models, routes and usage (keys never leave the device, ADR 0005; a provider without its key is useless), appearance, the reminder, backup settings and onboarding, which are per device. |
| Joining | A device joins either **empty** (it downloads the collection) or by **uploading** its collection to an empty sync location. Merging two unrelated collections is **not supported in v1**. A device with data that joins an existing location backs itself up first and is then replaced. |
| Encryption | **End-to-end encryption, on by default for Drive and WebDAV, optional for a folder.** A passphrase derives the key (PBKDF2 from `javax.crypto`, no new dependency), files are encrypted with AES-GCM, and the key is kept in `SecretStore`. Without the passphrase a new device cannot join. |
| Google Drive access | **REST v3 over OkHttp, OAuth 2 with PKCE in the browser, scope `drive.appdata`** (a hidden folder only Mnemo sees). No Google Sign-In, no Play Services, no Google client library (ADR 0009, `LicensesFlowTest`). Full-Drive scopes are "restricted" and need a paid security assessment, so they are never used. |
| Google client id | **Configured at build time** (`MNEMO_GOOGLE_CLIENT_ID`, environment or `local.properties`, like the signing settings in `ReleaseConfig.kt`). Without it the Drive backend is hidden, so forks and builds without it still work and never use the owner's quota. Whether F-Droid builds get it is decided in S0. |
| Dependencies | **AppAuth-Android (Apache-2.0) at most**, for the Android browser sign-in, if hand-written PKCE turns out worse. WebDAV and Drive use OkHttp (already in `:core:ai` and `:core:ingest`), compression uses `zstd-kmp` (already in `:core:anki`). Anything new goes into `NOTICE`, `app/config` and the FOSS check. |
| When it runs | On app start, after a study session ends, a few seconds after an edit, every few hours in the background (WorkManager with a network constraint on Android, the desktop's application scope), and on "Sync now". |

Owner-only tasks are marked **(owner)**.

---

## What is already in the codebase (verified 2026-10-03)

| Piece | Where | Relevance |
|---|---|---|
| Row identity | Every entity (`:core:database/entity`) has a UUID `id`, `createdAt`, `updatedAt`, `deletedAt`; `MediaEntity.id` is the file's SHA-256; `ai_answers` is keyed by `(noteId, kind)`, `ai_models` by `(providerId, modelId)`, `ai_task_routes` by `task` | Ready for sync, as planned in ARCHITECTURE §6. But `updatedAt` is **wall-clock** time and covers the **whole row**, so it cannot order concurrent edits to different fields, or edits from devices whose clocks differ (S1 adds a logical clock and per-field clocks). |
| Undo | `OfflineReviewRepository.undoAnswer` restores the previous card and calls `ReviewLogDao.delete(id)` (`DELETE FROM review_logs`), the **only hard delete** in the DAOs | A hard delete leaves nothing to sync: the undone review would come back from the other device. S1 makes it a soft delete. |
| Review log | `ReviewLogEntity`: `rating`, `stateBefore`, `reviewedAt`, `elapsedDays`, `scheduledDays`, `durationMs`, `stabilityAfter`, `difficultyAfter` | It has no `stateAfter`, `stepAfter` or `dueAfter`, so a review is not a full snapshot of the schedule it produced. S1 adds them, so replay (S3) can start from any review. |
| Scheduler | `StudyScheduler.answer` (`:core:domain`); fuzz is seeded with `card.id.hashCode() * 31 + reps`; `lapses` and `reps` are computed there | Replay is deterministic (`String.hashCode` is specified), so two devices that replay the same reviews get the same schedule. `:core:data` cannot see `:core:domain`, so the replay goes behind an interface (S3). |
| Upserts | `@Upsert` in `DeckDao`, `MediaDao`, `AiAnswerDao`, `AiProviderDao` | Room's upsert is an insert, then an update on conflict, not `REPLACE`: insert and update triggers (S1) both fire correctly. No DAO uses `OnConflictStrategy.REPLACE`, which would delete and reinsert. |
| Decks | `DeckRepository.saveDeck(path)` returns the existing deck when the path exists (by name, **ignoring case**, under the same parent) | Two devices that create "Spanish" offline get two ids for one name. S3 merges them with the same rule. |
| Media | Content-addressed files in `filesDir/media/<sha256>` (`MediaRepository`); garbage collection **soft-deletes** rows (`MediaDao`, `UPDATE media SET deletedAt`) and keeps files younger than a day | Media files never change, so they sync as files named by hash without merging. Remote clean-up is S4. |
| Backup | `BackupManager`: a zip with `manifest.json`, `database/`, `preferences/`, `media/`; `writeToFolder` keeps the newest N in a `DocumentAccess` folder | The "back up first" step before a device is replaced (S4) calls it. A backup is not a sync format: it is a whole-collection copy. |
| Restore | `PendingRestore.applyIfPresent` swaps the database files at startup, before Koin builds the database | A restored collection is a different history: S4 turns sync off after a restore and asks what to do. |
| Folders | `DocumentAccess`: `createInFolder`, `listFolder`, `openInput`, `openOutput`, `delete`, `keepAccess` (`:core:common`); `rememberFolderPicker` (`:core:ui`) | Enough for the folder backend: there is no rename, no "create if absent" and no subfolders, so the folder store (S2) flattens the layout into one folder and relies on the file checksum instead of a rename. On Android, Google Drive's own document provider is slow and unreliable for listing folders, so the phone needs the REST backend for Drive. |
| Background work | WorkManager workers in `:core:data/androidMain/work` (`workerOf`, `KoinWorkerFactory`); on desktop `TransferQueue`s in an application scope, with daily backup and weekly cleanup checked at start and hourly (`DesktopDataTransferRepository`) | `SyncWorker` and the desktop sync timer follow the same patterns. |
| Settings | `UserSettings` (`:core:model`, DataStore): scheduling fields, appearance, `backup`, `fsrsWeights`, `reminder`, `autoPlayAudio`, `onboardingCompleted` | Only the scheduling fields sync (proposed defaults). |
| Secrets | `SecretStore` (`:core:security`): AES-GCM, Keystore key on Android, OS keychain on desktop | Holds the OAuth refresh token, WebDAV password and sync passphrase key. Never in Room, logs or backups. |
| Schema | Room v5, exported to `core/database/schemas/` | S1 is v6: migration, `MigrationTest` case, fixture database. |
| Device identity | None today | S1 adds a random device id, kept out of backups (so a restored backup on a second device does not pose as the first). |
| Desktop runtime | The jlink module list in `DesktopApplicationConventionPlugin.kt` | A loopback OAuth redirect on desktop needs an HTTP listener: use a plain `ServerSocket` (`java.base`) or add `jdk.httpserver` to that list and rerun the smoke test. |

---

## Working rules

1. **Android stays shippable and desktop stays working.** Every step ends with the Android exit check
   (`./gradlew assembleDebug testDebugUnitTest testAndroidHostTest lint verifyRoborazziAndroidHostTest`),
   the JVM module tests, and `desktopTest` / `:desktop:test` for what it touches. Sync is behind a
   setting that is off until S5, so no step changes behaviour for users who don't turn it on.
2. **No Android imports in shared code**; run `python3 scripts/desktop/check-android-imports.py`. The
   OAuth browser flow is a platform seam (`AndroidXxx.kt` / `DesktopXxx.kt`).
3. **Never lose a review.** A review log row is never dropped by a merge, only soft-deleted by an
   undo. Every merge rule has a test with two devices, and the convergence test (S3) must pass before
   any backend is wired to the UI.
4. **Merges are deterministic.** Two devices that have seen the same change files end up with the same
   rows, whatever order the files arrived in. No merge rule reads the local wall clock.
5. **Nothing leaves the device without the user turning sync on**, and the privacy policy says what
   is sent where before the first release that has it. Keys, tokens and passphrases stay in
   `SecretStore`.
6. **No live network in tests.** Backends are tested with an in-memory store, a temporary folder and
   MockWebServer (WebDAV, Drive and the OAuth token endpoint), with their base URLs as constructor
   parameters.
7. **The format is versioned from day one.** Every remote file carries a format version. A device
   refuses a newer version with a clear message ("Update Mnemo on this device to keep syncing") and
   never rewrites files it cannot read.
8. **Docs move with the code.** At the end of each step tick the boxes here and update `CLAUDE.md` (a
   "Sync" section once S3 lands), `docs/ARCHITECTURE.md` (§6's "nothing is needed for sync today" line,
   and `:core:ai` as "the only network client") and the ADR.
9. **Commit per step**, small diffs.

---

## Overview

| Step | Theme | Outcome | Rough effort |
|---|---|---|---|
| **S0** | Decisions, ADR, Google spike | ADR 0013; the open questions answered; a Google Cloud project | 1–2 days |
| **S1** | Schema v6: recording changes | Device id, change outbox filled by triggers, logical clock, full review snapshots, Undo as a soft delete | 2–3 days |
| **S2** | `:core:sync`: format and stores | `SyncStore` interface; change files, compression, encryption; in-memory and folder stores. **Done** | 2–3 days |
| **S3** | Merge engine | Pack local changes, apply remote ones, rules per table, schedule replay, deck merge; convergence tests | 4–6 days |
| **S4** | Lifecycle | Create, join, leave; snapshots and compaction; restore; background sync | 3–4 days |
| **S5** | Settings › Sync + folder backend | First usable sync (desktop folder, Android folder); status and errors | 2–3 days |
| **S6** | Google Drive | OAuth seam, Drive REST store, tokens | 3–4 days |
| **S7** | WebDAV (optional) | Nextcloud and other WebDAV servers | 1–2 days |
| **S8** | Polish and QA | Two-device runbook on real hardware, large collections, docs, privacy policy, release notes | 2 days |

```
S0 ──► S1 ──► S3 ──► S4 ──► S5 ──► S6 ──► S8
  └──► S2 ──┘                └───► S7
```

S2 has no schema dependency and can be done in parallel with S1. S5 is the first point where a user
can turn sync on; S6 and S7 are only new backends.

---

## S0 — Decisions, ADR and the Google spike

**Goal:** confirm the defaults, and find out early whether Google's rules allow the Drive plan.

- [x] **(owner)** Confirm or change the proposed defaults above (confirmed 2026-10-03), especially: no merging of unrelated
      collections in v1, which settings sync, encryption on by default for cloud backends, and whether
      Drive is wanted at all or the folder backend is enough for the first release.
- [ ] **Spike: Google OAuth on both platforms** (half a day, throwaway code, outside `main`). The desktop
      half has run (2026-10-03: the Desktop client needs its secret at the token endpoint, so it is build config;
      `drive.appdata` create/list/read/delete and revocation work). The Android half (AppAuth, custom scheme,
      on a device) is S6's first task. Details in ADR 0013:
  - Android: an Android OAuth client is tied to the package name **and the signing certificate's
    SHA-1**. Find out which client type works for the owner-signed sideload/Play APK **and** for an
    F-Droid build (F-Droid signs with its own key unless the build is reproducible and published with
    the developer's signature). If no type works for F-Droid, the Drive backend is hidden there and
    the recipe gets no client id.
  - Desktop: a "Desktop app" client with a loopback redirect and PKCE. Check whether Google still
    accepts it without a client secret, or whether the (non-secret) secret ships in the build.
  - Check `drive.appdata` against `drive.file` today: verification requirements, whether the user can
    see and delete the data, and the quota it counts against. Record the choice.
- [x] **(owner)** Create the Google Cloud project (project, consent screen and the Desktop and Android clients exist; verification of the consent screen is still to be started), the OAuth consent screen (app name, the privacy
      policy and home page from `scripts/pages`), and the client ids. Start verification early: it can
      take weeks.
- [x] **(owner)** Decide F-Droid's treatment (decided 2026-10-03: hidden in F-Droid builds): the Drive backend shown with the `NonFreeNet`
      anti-feature in the recipe, or hidden in F-Droid builds.
- [x] **ADR 0013 "Sync"** (`docs/adr/0013-sync.md`, written 2026-10-03, status Proposed), recording at least:
  - Why there is no server and no database-file copy, and the remote layout (S2).
  - The logical clock, the per-field rule, the replay rule and the delete rule (S3), with an example
    of each.
  - What syncs and what never does (keys, tokens, providers, device settings).
  - Joining, leaving, restore, and what v1 does not support.
  - Encryption: key derivation, what is encrypted (contents, not file names), passphrase loss.
  - Backends: the `SyncStore` contract, and the Google scopes and client id handling.

**Exit:** ADR written (**owner: review it**); the spike's answers recorded in the ADR. *Status 2026-10-03: ADR 0013 is Accepted, the defaults are confirmed, F-Droid's treatment is decided and the
desktop half of the spike has run. Left: the Android half of the spike (S6's first task) and the owner's consent
screen verification. S1 and S2 can start.*

## S1 — Schema v6: recording changes (`:core:database`, `:core:data`)

**Goal:** every local change is recorded so it can be sent, without any repository having to remember
to do it. No behaviour changes for the user.

- [x] **Device id and clock state**: a `sync_state` single-row table (device id, a random UUID made on
      first open; the last logical clock value; an `applying` flag, see below). It is not copied by a
      backup **restore** (`PendingRestore` keeps the restoring device's id), so a backup restored on a
      second device does not pose as the first.
- [x] **`SyncClock`** (hybrid logical clock, `:core:data`): `now()` = max(wall clock, last + 1),
      `observe(remote)` moves it past a remote value. Repositories keep writing `updatedAt` from
      `Clock`; the clock value is attached when changes are packed (S3).
- [x] **Change outbox**: a `sync_changes` table (`seq` autoincrement, `tbl`, `rowId`, `fields`: the
      changed column names, `at`), filled by **SQLite triggers** `AFTER INSERT` / `AFTER UPDATE` on
      every synced table (decks, note types, notes, cards, review logs, media, `ai_answers`). An update
      trigger records only columns whose value changed (`NEW.x IS NOT OLD.x`). Triggers are skipped
      while `sync_state.applying = 1`, so applying remote changes does not echo them back. Triggers
      catch every path that writes, including Anki import, Smart Extract's accept and the browser's
      bulk actions.
  - Room does not manage triggers: create them in the migration **and** in the database's creation
    callback, in one shared list of SQL statements, and test that a new database and a migrated one
    have the same triggers.
  - The outbox only grows while sync is on: with sync off, the triggers are still there but a cheap
    `WHEN` on `sync_state.enabled` skips them.
- [x] **Full review snapshots**: `review_logs` gains `stateAfter`, `stepAfter`, `dueAfter` (nullable
      for old rows), written by `StudyScheduler.answer`'s log. Anki-imported reviews leave them null.
      Also `repsAfter` and `lapsesAfter`: the fuzz seed is `card.id.hashCode() * 31 + reps`, so a replay
      that starts from a snapshot needs the count of reviews (and a card's lapses) at that point, and
      neither can be derived from the logs of an imported card.
- [x] **Undo as a soft delete**: `undoAnswer` sets the log's `deletedAt` instead of `DELETE`. Check
      every query on `review_logs` filters `deletedAt IS NULL` (stats, today's counts, the optimizer,
      the forgetting curve, Anki export); `ReviewLogDao.delete` is removed.
- [x] Migration 5 → 6 in `Migrations.kt`, a `MigrationTest` case, a v6 fixture database for
      `FixtureDatabasesTest`, `core/database/schemas/6.json`.
- [x] Tests: every repository write lands in the outbox with the right fields; undo leaves a deleted
      log and the right outbox rows; `applying = 1` records nothing; stats ignore undone reviews.

**Exit:** v6 on both platforms; the outbox is filled correctly; nothing visible changes. *Done 2026-10-03.
Notes for S3: `fields` is `*` for an insert and the changed column names, comma-separated, otherwise
(`createdAt`/`updatedAt` are never listed; `at` is the row's `updatedAt`); a composite key
(`ai_answers`) is `noteId/kind`. `SyncTriggers`' table list is guarded by a test that compares it with the
real columns. `SyncSetup.onOpen` re-creates a missing `sync_state` row, and `PendingRestore` (now with a
SQLite driver parameter, defaulting to the platform's) keeps this device's id and sets `enabled = 0`;
a restore on a device with no database, or of a backup from before v6, gets a new id. The restored
outbox is not cleared (S4's restore flow resets the location instead).*

## S2 — `:core:sync`: format and stores (new JVM module)

**Goal:** a pure-Kotlin module, like `:core:ai`, that knows the remote layout and the stores, and
nothing about Room.

Remote layout (record the final version in the ADR):

```
<root>/
  sync.json                               # format version, collection id, created at, encryption salt + check
  devices/<deviceId>/device.json          # name, platform, app version, last seq, the seqs it has applied from others
  devices/<deviceId>/changes/<seq>.mnc    # a batch of changes, written once, never rewritten
  snapshots/<deviceId>-<clock>.mns        # the whole collection as of a clock value (S4)
  media/<sha256>                          # media files, written once
```

Only one device writes each file under `devices/`; snapshot and media names are unique or
content-addressed, so the same name always means the same contents. `sync.json` is written once by the
device that creates the location.

- [x] **`SyncStore`** interface: `list(prefix)`, `read(path)`, `write(path, bytes)` (new files only),
      `overwrite(path, bytes)` (only for the device's own `device.json`), `delete(path)`; failures are
      `IOException` subclasses that separate "offline", "auth", "quota" and "not found".
- [x] **Change file format**: a header (format version, device id, seq, clock range) and the changes:
      `table`, `id`, `clock`, `fields` (name → value as JSON), or a delete. kotlinx.serialization JSON,
      zstd-compressed, then encrypted. Files are capped (about 2 MB compressed) so a big Anki import
      becomes several files. (`format/Changes.kt`, `ChangeSplitter`; the envelope is `format/FileCodec.kt`.)
- [x] **Encryption**: PBKDF2-HMAC-SHA256 (high iteration count, salt in `sync.json`) → AES-256-GCM per
      file, with a check value in `sync.json` so a wrong passphrase fails at once, not with a
      corrupted file. File names are not encrypted (they hold ids and numbers, not content).
- [x] **`InMemorySyncStore`** (in the module's main source, see the exit note) and **`FolderSyncStore`** on
      `DocumentAccess` (works with a plain directory in tests through `FakeDocumentAccess`, a `File`
      folder on desktop, a SAF tree on Android). A half-written file (crash during a write) is written
      under a temporary name and renamed, or detected by its length and checksum and ignored.
- [x] Tests: round trip of every change type; a wrong passphrase; a newer format version is refused;
      a truncated file is ignored, not applied.

**Exit:** `:core:sync:test` passes; the module is in `settings.gradle.kts` and has no Android or Room
dependency. *Done 2026-10-03. Notes for S3–S6: the module depends on `:core:common` only (for
`DocumentAccess`), and `SyncRemote` is the API the merge engine uses (`create` / `open` / `openWithKey`,
`writeChanges(deviceId, firstSeq, changes)` which returns the seqs it used, `listChangeSeqs`, `readChanges`,
devices, snapshots, media), not `SyncStore`. A change file that throws `SyncCorruptException` is skipped, and if it
is `maybeIncomplete` the engine stops at it for that device and retries next time instead of moving on. `DocumentAccess`
has no subfolders, so the folder store flattens the layout (`/` → `__`) and `SyncPaths.isValid` forbids `__`; a
Drive store keeps flat names too. `InMemorySyncStore` is in the module's main source (see ADR 0013); the folder
store's tests use a real temporary directory through a test `DocumentAccess`, since `:core:testing` can't be a
dependency. An unreadable `device.json` is `SyncCorruptException`: compaction (S4) must treat it as "unknown", never
"applied everything". `SyncRemote.create` deletes a failed create's `sync.json` only when it doesn't parse.
The file format is recorded in ADR 0013 ("As built (S2)").*

## S3 — Merge engine (`:core:data/sync`)

**Goal:** turn the outbox into change files, apply other devices' files, and converge.

- [ ] **Packing**: read the outbox in `seq` order, coalesce changes to the same row, read the current
      values of the changed fields, stamp each with `SyncClock.now()`, write the file, then clear the
      packed outbox rows and record the per-field clocks locally (`sync_field_clocks`: table, row,
      field, clock). The file is written before the outbox is cleared, so a crash re-sends rather than
      loses.
- [ ] **Applying**: list every other device's files after the last seq applied from it, read them in
      clock order, and apply them in **one transaction** with `applying = 1`. Applying is idempotent:
      a file applied twice changes nothing. Room's flows refresh the UI by themselves.
- [ ] **Rules per table** (all deterministic; ties broken by device id):

  | Data | Rule |
  |---|---|
  | Review logs | Union by id. Only `deletedAt` (an undo) ever changes after creation. |
  | Card schedule (`state`, `due`, `stability`, `difficulty`, `step`, `lastReview`, `reps`, `lapses`) | One unit, not per field. If only one side reviewed the card since the last common review, take that side's schedule. If both did, **replay**: start from the schedule after the newest review both have (from its S1 snapshot fields, or the card's schedule from before any review), apply every later live review in `reviewedAt` order with the receiving device's synced scheduling settings. Undone reviews are skipped. |
  | Card flags (`flagged`, `starred`, `suspended`, `buriedUntil`), `deckId` | Per field, last writer by clock. |
  | Notes (each field, `tags`, `hint`, `deckId`), decks (`name`, `description`, `category`, `starred`, `examDate`, `parentId`), note types | Per field, last writer by clock. A note's `fields` list is compared per position. |
  | Deletes (`deletedAt`) | Delete wins over a concurrent edit. A note or card that arrives in a deleted deck: decided in S0 (proposed: the deck is restored as `<name> (recovered)`, so new work is never hidden). |
  | Two decks with the same parent and name (ignoring case, as `saveDeck`) | Merged: the lower id wins; the other's notes and cards move to it and it is soft-deleted, as normal changes, so every device does the same. |
  | Media rows and files | Union. The file is uploaded before the change that references it; a card whose file has not arrived yet shows the missing-media placeholder until it does. |
  | `ai_answers` | Last writer by clock per `(noteId, kind)`. |
  | Scheduling settings | One record, last writer by clock, stored in a change file as a pseudo-table. Fitted FSRS weights travel with it. |

- [ ] **Replay seam**: `:core:data` defines `ScheduleReplayer` and `:core:domain` binds it to
      `StudyScheduler` in `domainModule` (or the model ↔ FSRS mapping moves down so `:core:data` can
      call it; pick one in the ADR).
- [ ] **Convergence test** (`commonTest`, both targets): two and three in-memory databases with an
      `InMemorySyncStore`, random sequences of edits, reviews, undos, deletes and deck creations on each
      device, synced in random orders. After everyone has synced, every database must hold the same
      rows (compare a canonical dump), and no review created on any device is missing.
- [ ] Scenario tests, one each:
  1. The same card reviewed on both devices offline: both reviews kept, schedule equals the replay.
  2. Front edited on A, back on B: both edits kept.
  3. The same field edited on both: the later clock wins on both devices.
  4. A's clock a day ahead, then B edits after syncing: B's edit wins (the clock moved past A's).
  5. Deleted on A, edited on B: deleted on both.
  6. "Spanish" created on both: one deck afterwards, with all notes.
  7. Undo on A after A synced the review: the review is undone on B, and B's schedule is replayed.
  8. A crash between writing a file and clearing the outbox: nothing is lost or doubled.
  9. 12,000 cards imported on A (`scripts/qa/make-large-apkg.py`): B applies them in reasonable time
     (record the number; the target is decided after measuring).

**Exit:** the convergence and scenario tests pass on both targets; no UI yet.

## S4 — Lifecycle (`:core:data/sync`)

**Goal:** the steps around merging: starting, joining, leaving, staying small, and running by itself.

- [ ] **`SyncRepository`**: `status: Flow<SyncStatus>` (off, idle with last sync time, syncing,
      waiting for network, error with a reason), `syncNow()`, `create(backend)`, `join(backend)`,
      `leave()`.
- [ ] **Create**: on an empty location, write `sync.json` and a snapshot of this collection. Refuse a
      location that already has `sync.json` (that's Join).
- [ ] **Join**: an empty collection downloads the newest snapshot, then the change files after it. A
      collection with data asks first, makes a backup with `BackupManager`, then replaces itself.
- [ ] **Leave**: turns sync off on this device, keeps the collection. "Delete sync data" (remove the
      location's files) is a separate, confirmed action.
- [ ] **Snapshots and compaction**: a device writes a snapshot when its files since the last snapshot
      pass a threshold. Change files older than the newest snapshot are deleted only once every device
      whose `device.json` was updated in the last 90 days has applied them; a device away longer must
      join again (it is told so). Remote media not referenced by the newest snapshot and older than 30
      days is deleted at the same time.
- [ ] **Restore**: after `PendingRestore` applies a backup, sync is off, and Settings asks: upload this
      collection as the new sync data (the location is reset, after a confirmation that names the
      other devices), or join the sync data again (the restore is discarded).
- [ ] **Running by itself**: `SyncWorker` (Android, unique periodic work with a network constraint,
      plus one-off work after a session and after edits, debounced; add it to `DependencyGraphTest`'s
      list) and a desktop timer in `desktopDataModule` (at start, hourly, after edits). Never two syncs
      at once (a `Mutex` in `SyncRepository`).
- [ ] Tests: create and join; join with data takes a backup first; compaction keeps everything a
      recent device needs; a device away too long is told to rejoin; restore turns sync off.

**Exit:** a full lifecycle runs in tests on both targets with the in-memory and folder stores.

## S5 — Settings › Sync and the folder backend (`:feature:settings`)

**Goal:** the first release a user can turn on.

- [ ] **Settings › Sync** screen: off / on with the backend's name; "Sync now"; last sync and status;
      the devices in the location (from `device.json`: name, platform, last seen); passphrase setup and
      entry; Leave; Delete sync data. Strings in the module's `composeResources`, no phone-only
      wording ("this device" → "this computer/phone" through `PlatformCapabilities` where needed).
- [ ] **Folder backend**: "Use a folder" with `rememberFolderPicker` and `keepAccess`. On desktop, a hint
      that the folder can be inside Google Drive, Dropbox, Nextcloud or Syncthing.
- [ ] A small status indicator where it helps (proposed: an icon in Settings and an error banner on
      Decks when sync has failed for more than a day). No indicator while everything works.
- [ ] Shortcut and menu: "Sync now" in `Shortcuts`, the `?` sheet and `DesktopMenu.kt`.
- [ ] Roborazzi screenshots for the screen's states; ViewModel tests in `commonTest`.
- [ ] Privacy policy: what is written to the chosen folder, encryption, and that Mnemo never sees it.

**Exit:** two real devices (desktop + desktop, or desktop + phone through Syncthing) sync through a
folder; the exit checks pass.

## S6 — Google Drive (`:core:sync`, platform seams)

**Goal:** the same sync for phones, through Drive's REST API.

- [ ] **`OAuthAuthorizer`** seam (`:core:ui` or `:core:data`, `expect`/`actual`): Android opens a Custom
      Tab and receives the redirect in an activity declared in `:app`'s manifest (AppAuth or a small
      hand-written one); desktop opens the browser with `LocalUriHandler` and listens on a loopback
      port (see the desktop runtime note above). Both use PKCE and a `state` check.
- [ ] **Tokens**: the refresh token in `SecretStore`, the access token in memory only; a revoked token
      becomes the "auth" error and Settings asks to sign in again.
- [ ] **`GoogleDriveSyncStore`**: the S2 layout as files in the app data folder (or the folder decided
      in S0), names stored in each file's `name`, a path → file id cache, resumable upload for files
      over 5 MB, `list` with paging. No redirects followed, as with the AI client.
- [ ] **Build config**: `MNEMO_GOOGLE_CLIENT_ID` (and the desktop client's values) read like the
      signing settings; without them the Drive option is not shown. Not in the repo.
- [ ] Licences: AppAuth (if used) in `NOTICE`, `app/config`, `scripts/fdroid/check-foss-deps.py` passes.
- [ ] Tests on MockWebServer: sign-in code exchange, refresh, list/read/write/delete, paging, a 401
      after refresh, quota full, offline.
- [ ] Privacy policy and Play's data safety form **(owner)**: what Mnemo sends to Google Drive, that it
      is encrypted, and that the only Google data it reads is its own folder.

**Exit:** a phone and a desktop sync through Drive; signing out and back in works; the FOSS check and
the exit checks pass.

## S7 — WebDAV (optional)

**Goal:** Nextcloud and other self-hosted storage, for users who want neither a folder sync tool nor
Google.

- [ ] **`WebDavSyncStore`** over OkHttp: `PROPFIND` (depth 1) to list, `GET`, `PUT` with
      `If-None-Match: *` for new files, `DELETE`, `MKCOL`. Basic auth with an app password in
      `SecretStore`. HTTPS only, except a local address with the same rule as AI providers
      (`AiEndpoint.check`).
- [ ] Settings: server URL, user, app password, a "Test connection" like the AI providers'.
- [ ] Tests on MockWebServer with recorded Nextcloud responses.

**Exit:** sync through a Nextcloud test account works **(owner: check on a real server)**.

## S8 — Polish and QA

**Goal:** confidence on real devices before turning sync on in a release.

- [ ] **Runbook** `docs/sync/qa.md` with a results log: the S3 scenarios by hand on a phone and a
      desktop, offline on both then online, a phone with a wrong clock, airplane mode during a sync, a
      large collection, a wrong passphrase, a revoked Google token, a full Drive, leave and rejoin,
      restore on one device.
- [ ] Battery and data use of background sync on Android (Battery Historian or `dumpsys`), recorded in
      the log.
- [ ] Docs: `CLAUDE.md` "Sync" section, `docs/ARCHITECTURE.md`, ADR 0013 updated with what changed,
      `docs/desktop/install.md` (moving a collection now has a sync option), user help for passphrase
      loss ("the sync data can't be read; leave and create it again from a device that has the
      collection").
- [ ] Release notes, F-Droid changelog, the F-Droid recipe's anti-feature (S0's decision), the store
      listing's feature list **(owner)**.

**Exit:** the runbook passes on real hardware; sync is announced in a release.

---

## Not in v1

- Merging two unrelated collections (two devices that each have their own data).
- Sharing decks between different people through a sync location.
- A Mnemo sync server, or syncing with AnkiWeb.
- Syncing AI providers (they would need their keys, which never leave a device).
- Choosing which decks sync.
