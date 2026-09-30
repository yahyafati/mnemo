## Mnemo for desktop

Installers for **Windows** (`.msi`), **macOS on Apple Silicon** (`.dmg`) and **Linux** (`.deb`, `.rpm`,
portable `.tar.gz`). Check a download against `SHA256SUMS.txt`. Install, update, uninstall and where your
data lives: [docs/desktop/install.md](https://github.com/yahyafati/mnemo/blob/main/docs/desktop/install.md).

### The installers are not signed

Your system will warn you the first time you open Mnemo. This is expected.

- **Windows:** SmartScreen says "Windows protected your PC". Choose **More info**, then **Run anyway**.
- **macOS:** Gatekeeper says it cannot check the app. On macOS 14 and older, right-click Mnemo and choose
  **Open**. On macOS 15 and newer, open **System Settings › Privacy & Security** and choose **Open Anyway**
  next to the message about Mnemo.

### Good to know

- Your collection is in your user's data folder, not the install folder: updating and uninstalling keep it.
- No sync yet. Move a collection with Back up and Restore, or one deck with `.apkg` export and import.
- Intel Macs are not built yet.
