# Fixture databases

`mnemo-v<N>.db` are real Mnemo databases of schema version N (1 to 4), created by the Android build
(Room on Robolectric, with the framework's SQLite, so they carry Android's `android_metadata`
table) and filled with a little data: a deck "Biology" with a note and a card, plus whatever the
version added (media at v2, hint and Anki fields at v4 …).

`FixtureDatabasesTest` copies each one, opens it the way the app does on both targets (Android
host tests and desktop) and checks the data survives every migration up to the current version.

Add `mnemo-v<N+1>.db` whenever the schema version goes up: make it with the previous app build,
or with `MigrationTestBase.createDatabase(N+1)` in an Android host test, and copy the file here.
Keep these files small and free of personal data.
