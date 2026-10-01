## Mnemo {{version}}

{{changelog}}

Mnemo is free, keeps everything on your device and has no account. **Android** (`.apk`), **Windows**
(`.msi`), **macOS** (`.dmg`, one for Apple Silicon and one for Intel) and **Linux** (`.deb`, `.rpm`, portable `.tar.gz`) are
below. Check a download against `SHA256SUMS.txt`.

**Source:** the source of this release is the repository at its tag, which GitHub also attaches as
`Source code (zip)` and `Source code (tar.gz)` below. Mnemo is GPL-3.0-or-later.

### Android

1. Download the `.apk` on your phone (Android 10 or newer).
2. Open it. Android asks you to allow installs from your browser or file manager: allow it for this
   one app. You can turn it off again afterwards.
3. Play Protect may say it has not seen the app before. Choose **More details**, then **Install anyway**.

The APK is signed with the same key for every release, so a new one installs over the old one and
keeps your decks. Updates are not automatic: download the new file again, or follow this page with
[Obtainium](https://obtainium.imranr.dev/).

### Desktop

Install, update, uninstall and where your data lives:
[docs/desktop/install.md](https://github.com/yahyafati/mnemo/blob/main/docs/desktop/install.md).

**The installers are not signed.** Your system will warn you the first time you open Mnemo. This is expected.

- **Windows:** SmartScreen says "Windows protected your PC". Choose **More info**, then **Run anyway**.
- **macOS:** Gatekeeper says it cannot check the app. On macOS 14 and older, right-click Mnemo and choose
  **Open**. On macOS 15 and newer, open **System Settings › Privacy & Security** and choose **Open Anyway**
  next to the message about Mnemo.

### Good to know

- Your collection is in your user's data folder, not the install folder: updating and uninstalling keep it.
- No sync yet. Move a collection with Back up and Restore, or one deck with `.apkg` export and import.
- On a Mac, Apple menu › About This Mac says which `.dmg` to take: `arm64` for "Chip: Apple M…", `x64` for "Processor: Intel".
- Each file is there twice: with the version in its name (what `SHA256SUMS.txt` and bug reports use) and
  without (`Mnemo-android.apk`, ...), the one the download page links to.
