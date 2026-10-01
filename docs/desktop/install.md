# Mnemo for desktop: install, update, uninstall

Mnemo runs on Windows, macOS and Linux. It is the same app as on the phone, with the same collection
format, and it keeps everything on your computer. This page covers the installers from
[GitHub Releases](https://github.com/yahyafati/mnemo/releases) (built by
[`release.yml`](../../.github/workflows/release.yml), see
[ROADMAP.md](ROADMAP.md) D8).

## Which file to download

| System | File | Notes |
|---|---|---|
| Windows 10 or 11, 64-bit | `Mnemo-<version>-windows-x64.msi` | Installs for your user only; no administrator prompt |
| macOS 12 or newer, Apple Silicon (M1 and later) | `Mnemo-<version>-macos-arm64.dmg` | There is no build for Intel Macs yet |
| Debian, Ubuntu, Mint and relatives, x86-64 | `mnemo_<version>-1_amd64.deb` | |
| Fedora, openSUSE and relatives, x86-64 | `mnemo-<version>-1.x86_64.rpm` | |
| Any other Linux, x86-64 | `Mnemo-<version>-linux-x64.tar.gz` | Portable: unpack it anywhere |

Each file is on the release twice: with the version in its name, and without it (`Mnemo-windows-x64.msi`,
`Mnemo-macos-arm64.dmg`, `Mnemo-linux-x64.tar.gz`, `mnemo-amd64.deb`, `mnemo-x86_64.rpm`), which is the link
that always gives the newest release.

Every release has a `SHA256SUMS.txt`. To check a download, compare its hash with the line for that
file: `sha256sum <file>` (Linux), `shasum -a 256 <file>` (macOS), `Get-FileHash <file>` (PowerShell).

## First run: the installers are not signed

Signing needs paid developer accounts, so for now the installers are unsigned. Your system will warn
you the first time; Mnemo is not doing anything wrong, and the warning goes away after you allow it once.
The source of every build is in this repository, and the checksums above tell you the file is the one
the release built.

- **Windows (SmartScreen):** "Windows protected your PC" appears when you run the `.msi`. Choose
  **More info**, then **Run anyway**.
- **macOS (Gatekeeper):** open the `.dmg`, drag Mnemo to Applications and open it. macOS says it cannot
  check the app for malware. On macOS 14 and older, right-click Mnemo and choose **Open**, then **Open**
  again. On macOS 15 and newer, open **System Settings › Privacy & Security**, scroll down to the
  message about Mnemo and choose **Open Anyway**. From a terminal, the same thing is
  `xattr -dr com.apple.quarantine /Applications/Mnemo.app`.
- **Linux:** no warning. Your package manager may say the package is unsigned; `.deb` and `.rpm` files
  from a release are not in a repository.

## Install on Linux

```bash
sudo apt install ./mnemo_<version>-1_amd64.deb        # Debian, Ubuntu
sudo dnf install ./mnemo-<version>-1.x86_64.rpm       # Fedora
tar xzf Mnemo-<version>-linux-x64.tar.gz && Mnemo/bin/Mnemo   # portable
```

Mnemo needs a desktop with X11 or Wayland (through XWayland) and these libraries, which almost every
desktop already has: OpenGL (`libgl1`), `libxrender1`, `libxtst6`, `libxi6`, `libfontconfig1` and
`libfreetype6`. The packages install into `/opt/mnemo` and add Mnemo to the applications menu.

## Where your data lives

Nothing is stored in the install folder, so installing, updating and uninstalling never touch your
collection.

| System | Collection, settings and media | Notes |
|---|---|---|
| Windows | `%LOCALAPPDATA%\Mnemo` | |
| macOS | `~/Library/Application Support/Mnemo` | |
| Linux | `$XDG_DATA_HOME/mnemo`, usually `~/.local/share/mnemo` | |

Set the environment variable `MNEMO_DATA_DIR` to use another folder (for example on a USB stick with the
portable build). Only one Mnemo can have a collection open: starting a second one says so, or, when it
was started by opening a file, hands the file to the one that is running.

API keys for AI providers are encrypted in a `secrets` folder beside the collection. The key that
decrypts them is in your system's keychain (Windows Credential Manager, macOS Keychain, the Secret
Service on Linux), or, where there is no keychain, in a file only your account can read; Settings › AI
providers says which. Backups and exports never contain API keys: enter them again after a restore.

## Opening Anki packages

Double-click an `.apkg` or `.colpkg` file (or drop one on the window, or use File › Import) and Mnemo
imports it.

## Sound on cards

Mnemo plays `wav`, `mp3` and `ogg` sound files from cards. Other formats (for example `flac` or `m4a`)
are not played: the app says so instead of staying silent. Text to speech and dictation are not on
the desktop yet.

## Update

Install the new version over the old one. Your data stays where it is, and Mnemo opens it, migrating the
collection if the release needs it.

- **Windows:** run the new `.msi`; it replaces the old version.
- **macOS:** drag the new Mnemo to Applications and replace the old one. Quit Mnemo first.
- **Linux:** `sudo apt install ./<new .deb>` or `sudo dnf upgrade ./<new .rpm>`; the portable build is
  replaced by unpacking the new archive.

Mnemo does not check for updates or connect anywhere on its own (only your AI provider, when you use it).

## Uninstall

- **Windows:** Settings › Apps › Installed apps › Mnemo › Uninstall.
- **macOS:** quit Mnemo and drag it from Applications to the Trash.
- **Linux:** `sudo apt remove mnemo` or `sudo dnf remove mnemo`; delete the unpacked folder for the
  portable build.

Your collection stays. To remove it too, delete the data folder above, and the entry named Mnemo in your
keychain if you want its key gone. Make a backup first (Settings › Data) if you might want it back.

## Moving a collection between phone and desktop

There is no sync yet. Two manual paths use formats both apps share:

| Want to move | Use | Effect on the other device |
|---|---|---|
| **Everything** (decks, notes, review history, settings, media) | Settings › Data › Back up (or File › Back up…), copy the file, then Restore on the other device | **Replaces** its whole collection |
| **One deck** | Export as `.apkg`, then Import | **Adds** to its collection |

Study on one device at a time, and move the backup before you switch: restoring overwrites reviews
made on the other device since its last transfer. The daily reminder and the home-screen widget are
Android-only, and their settings are ignored on the desktop.

## The license

Mnemo is free software under the GPL-3.0-or-later. `LICENSE` and `NOTICE` are in the app's `resources` folder, and the libraries it
bundles are listed in Settings › About › Open-source licenses. The source of every release is this repository, at its tag.

## Building it yourself

```bash
./gradlew :desktop:run                  # run from source
./gradlew :desktop:createDistributable  # the app image, without an installer
./gradlew :desktop:packageDmg           # or packageMsi, packageDeb, packageRpm: on that system only
```

Installers are built per system (jpackage cannot cross-compile), with a JDK 21 that has `jmods`; the
Windows `.msi` needs the WiX Toolset 3, and the Linux `.rpm` needs `rpm`. `README.md` has the rest.
