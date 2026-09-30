# Fixture backups

`android-backup.zip` was made by the Android build (Room on Robolectric with the framework's
SQLite), `desktop-backup.zip` by the desktop build (bundled SQLite). Both hold `SampleCollection`:
a nested deck, a Basic note with an image, one review and a preference, at schema version 4.

`CrossDeviceBackupTest` restores both on both targets, which is the "a backup made on the phone
restores on the computer, and back" check of docs/desktop/ROADMAP.md (D4).

Regenerate only when the backup format changes on purpose, one platform at a time:

```
MNEMO_WRITE_FIXTURES=/tmp/fixtures ./gradlew :core:data:testAndroidHostTest :core:data:desktopTest \
    --tests '*CrossDeviceBackupTest*' --rerun-tasks
```
