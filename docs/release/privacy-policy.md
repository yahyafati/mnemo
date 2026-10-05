# Mnemo privacy policy

_Last updated: 3 October 2026_

Mnemo is a flashcard app that works on your device. This policy describes what it does with
your data. The same text, shortened, is in the app under Settings › About › Privacy policy.

## What Mnemo collects

Nothing. Mnemo has no account, no server, and no analytics, advertising or crash-reporting code.
The developer receives no data about you or how you use the app.

## What stays on your device

Your decks, cards, review history, images, sounds and settings are stored in the app's private
storage on your device. They leave it only when you do one of these yourself:

- **Backups and exports** (Settings › Data, or a deck's Export): written to a file or folder you
  choose, such as your device storage or a cloud folder you picked in the system file picker.
  Automatic backups go to the folder you chose.
- **Android's own device backup**, if you have it turned on: Android may include Mnemo's
  collection in your Google device backup, which is governed by Google's terms. API keys are
  never included (see below).

## Optional AI features

AI features are off until you add an AI provider yourself (Settings › AI providers). When you use
one, Mnemo sends the content that feature needs **directly from your device to the provider you
configured**, over HTTPS (plain HTTP is allowed only for a provider you marked as running on your
own local network):

| Feature | What is sent |
|---|---|
| Smart Extract | The source text you paste, pick (PDF) or open (link), and the questions of cards already in the review queue |
| Smart Extract, PDF pages as images (only if you choose "Read pages with AI", Auto for pages without text, or "Cards from page images") | Pictures of the PDF pages you picked, with everything on them, and the text of those pages where a page has some. You confirm the number of requests first, and Mnemo asks once per provider before the first picture is sent |
| Explain / Example / Rewrite | The card you are looking at |
| Co-Author | The deck's name and up to 150 of its cards as plain text, and your messages; for "Improve weak cards", one card and how often you forgot it |
| Test connection | A one-word test message |

Every AI answer has a **Report** action. It asks first, then opens a draft issue on GitHub in your
browser with that answer, the feature and the model name. Nothing is sent until you submit the
draft there, and GitHub issues are public. Your source text and the rest of your deck are not
included.

Before the first request to each provider, Mnemo shows what will be sent and where. What the
provider does with it is governed by that provider's privacy policy. Mnemo records only the token
counts the provider reports, on your device. "Find duplicates" in Co-Author runs on your device
and sends nothing.

Links you open in Smart Extract are fetched directly from the website. Dictation uses Android's
speech recognizer, which works on the device where your device supports it.

## Optional sync between your devices

Sync is off until you turn it on (Settings › Sync). Mnemo has no server, so it syncs only through
storage you own: a **folder you choose**, which can be inside a cloud-drive or sync app such as Google
Drive for Desktop, Dropbox, Nextcloud or Syncthing, **a WebDAV server** such as Nextcloud, or, in builds that offer it,
**your Google Drive** (both below). Once it is on, Mnemo writes these to that place, as files only Mnemo reads: your decks, notes, review history, images, saved AI answers and scheduling settings, the name,
platform and app version of each device that syncs, and the time it last synced. Other devices read
those files and add their own.

- **Not synced:** AI providers and their keys, appearance, the reminder, backup settings.
- **Encryption:** you can protect the files with a passphrase. They are then encrypted on your device
  (AES-256-GCM, key derived from the passphrase) before they are written, and the passphrase is needed
  on every device. Without a passphrase, anyone who can open the folder can read them. If you forget the
  passphrase the sync data can't be read; leave and start again from a device that has the collection.
- **Google Drive:** if you choose it, you sign in to Google in your browser and Mnemo asks for one
  permission only, access to its own hidden application folder in your Drive (`drive.appdata`). Mnemo
  cannot see or change any other file in your Drive, and you cannot see this folder in the Drive app. The
  files count against your Drive storage. Mnemo keeps the token that keeps you signed in on your device
  only, in encrypted storage that no backup or export contains, and never sees your Google password. The
  only Google data Mnemo reads is the files it wrote itself to that folder. You can sign out in Settings ›
  Sync (Stop syncing), and revoke Mnemo's access at any time in your Google Account's "Third-party apps
  with account access" page. Files are encrypted with your passphrase if you set one (recommended for
  Drive: without one Google can read them).
- **WebDAV:** if you choose it, you enter the address of a folder on your server, a user name and a password
  (use an app password, not your account's). Mnemo sends its files, and the requests that list, read and delete them,
  to that address only, over HTTPS (plain HTTP is accepted only for a server on your own network). The password is
  kept on your device only, in encrypted storage that no backup or export contains, and is sent only to that
  server. Mnemo makes the folder if it is not there and otherwise touches nothing outside it. Files are encrypted with
  your passphrase if you set one (recommended: without one, whoever runs the server can read them).
- **Who sees it:** Mnemo never receives the folder, the files or the passphrase. What the cloud-drive,
  sync app or Google does with the files is governed by its own privacy policy.
- **Stopping:** "Stop syncing on this device" keeps your collection and leaves the files as they are
  (and signs out of Google or forgets the WebDAV password); "Delete the sync data" removes Mnemo's files from the folder, the Drive folder or the WebDAV folder.

## API keys

Keys you enter are encrypted with a key held by the Android Keystore, stored outside every backed
up location, never exported, never shown in full after saving, and never written to logs.

## Permissions

| Permission | Why |
|---|---|
| Internet | Only for AI providers, links you open in Smart Extract, Google Drive sync and WebDAV sync if you turn them on. Everything else works offline. (A sync folder is reached through your files, not through Mnemo's own network access.) |
| Notifications | The daily study reminder (if you turn it on) and progress of long imports, exports and backups. |
| Microphone | Only while you dictate in Smart Extract. |
| Foreground service (data sync) | Keeps a long import, export or backup running if you leave the app. |

## Children

Mnemo collects no personal data from anyone, including children.

## Changes

Changes to this policy ship with app updates and are listed in the release notes.

## Contact

Questions: yfati037@gmail.com.
