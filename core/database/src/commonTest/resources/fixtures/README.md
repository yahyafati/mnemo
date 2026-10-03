# Fixture databases

`mnemo-v<N>.db` are real Mnemo databases of schema version N (1 to 6), created by the Android build
(Room on Robolectric, with the framework's SQLite, so they carry Android's `android_metadata`
table) and filled with a little data: a deck "Biology" with a note and a card, plus whatever the
version added (media at v2, hint and Anki fields at v4, a saved AI answer at v5 …).

`FixtureDatabasesTest` copies each one, opens it the way the app does on both targets (Android
host tests and desktop) and checks the data survives every migration up to the current version.

Add `mnemo-v<N+1>.db` whenever the schema version goes up: make it with the previous app build,
or with `MigrationTestBase.createDatabase(N+1)` in an Android host test, and copy the file here.
Keep these files small and free of personal data.

`mnemo-v5.db` is `mnemo-v4.db` with `Migration4To5`'s SQL applied by hand (`sqlite3`), the identity
hash of `schemas/…/5.json` in `room_master_table`, `user_version` 5 and one row in `ai_answers`.

`mnemo-v6.db` is `mnemo-v5.db` opened once by the current build (Migration5To6 ran: the sync tables, the
five snapshot columns on `review_logs`, the 14 sync triggers and a `sync_state` row with a random device
id), checkpointed and copied. The device id in it is just a UUID; every copy of this file shares it.
