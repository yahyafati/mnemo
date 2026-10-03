# Sync: help

Mnemo can keep your phone, tablet and computers in step. There is no Mnemo server and no account: each device
writes its changes as files to a place **you** own, and reads the other devices' files from it. Everything below is in
**Settings › Sync**. Why it works this way, and exactly what is stored, is in [ADR 0013](../adr/0013-sync.md) and the
[privacy policy](../release/privacy-policy.md).

## Where to sync

| Place | Good for | Notes |
|---|---|---|
| **A folder** | Computers, and phones with Syncthing or the Nextcloud app | Pick a folder you use for nothing else. On a computer it can sit inside Google Drive for Desktop, Dropbox, Nextcloud or Syncthing: that app carries the files to your other devices. |
| **Google Drive** | Phones and computers with a Google account | Not in F-Droid builds, and only in builds made with a Google client. Mnemo keeps its files in a hidden folder of your Drive that only Mnemo can open, and cannot see anything else in your Drive. The files count against your Drive storage. |
| **A WebDAV server** | Nextcloud, ownCloud or a server at home | Enter the folder's address, your user name and an **app password** (in Nextcloud: Settings › Security). Use `https://`; plain `http://` is only accepted for a server on your own network. |

Not synced, on purpose: AI providers and their keys, appearance, the reminder, the backup settings. They stay on each device.

## Set it up

1. On the device that has your cards: **Settings › Sync**, choose a place. If the place is empty, Mnemo offers to start syncing there.
2. **Choose a passphrase** (recommended, and on by default for Google Drive and WebDAV). Without one, anyone who can open the place can read your cards. With one,
   the files are encrypted on your device before they leave it. Write the passphrase down somewhere safe: see "I forgot the passphrase" below.
3. On each other device: **Settings › Sync**, choose the same place, enter the passphrase, **Join**. An empty device simply downloads your collection.
   A device that already has cards of its own is **replaced** by the synced collection; Mnemo says so first and saves a copy of what was there
   (the screen tells you where), but it does not merge two separate collections.

After that it syncs by itself: when the app opens, a few seconds after you change something, and every few hours in the background. **Sync now**
(Settings › Sync, or ⌘⇧S / Ctrl+Shift+S on a computer, or File › Sync now) syncs at once. You can study offline on every device; the changes meet the next time they
can reach the place. If two devices studied the same card, both reviews are kept and the card's schedule is worked out from both.

## What the screen is telling you

| It says | What to do |
|---|---|
| *Up to date* | Nothing. |
| *Can't reach the sync location right now. Mnemo will try again.* | You are offline, or the folder's app is not running. Nothing is lost. |
| *The sync data is encrypted and this device doesn't have its passphrase* | **Enter passphrase.** This happens on a device whose saved key was lost. |
| *Mnemo is no longer signed in to Google* | **Sign in to Google.** You may have removed Mnemo's access in your Google account. |
| *The WebDAV server no longer accepts the user name or password* | **Enter password.** Check the app password in your server's settings. |
| *Your Google Drive is full* / *There is no room left in the sync location* | Free some space. Mnemo carries on by itself. |
| *Another device started the sync data again* | **Join again.** A copy of this device's collection is saved first. |
| *This device was away so long that the sync data it needs has been cleaned up* | **Join again**, the same. Devices unused for 90 days are not waited for. |
| *The sync data is from a newer version of Mnemo* | Update Mnemo on this device. |
| *The sync location has no sync data* | The folder was emptied or is a different one. **Stop syncing**, then set it up again. |

Problems only you can fix also show as a banner on **Decks**. A problem that may mend itself (offline) only shows there after a day.

## I forgot the passphrase

The passphrase is never sent anywhere, so nobody, including us, can recover it, and without it the files can't be read. **A device that is already syncing is not affected**:
it keeps its own copy of the key. Only a *new* device needs the passphrase to join. To get going again you start the sync data over from a device that has the collection:

1. **Sync that device first** (Sync now) so the collection there is as complete as it can be, and check it holds what you expect.
2. On it: **Settings › Sync › Delete the sync data**, and confirm. This removes Mnemo's files from the place, for every device. Your cards stay on this device.
3. Set up again, as above, with a new passphrase. Write it down.
4. On every other device: it will say either that *another device started the sync data again* (choose **Join again**) or that the place has no sync data (choose **Stop syncing**, then set it up
   again and **Join**). A copy of the cards that were there is saved first, but anything you changed there since its last sync is not in the new collection: sync those devices first when you can.

## I restored a backup

A restored collection is a different history from the one in the sync place, so Mnemo **pauses** sync and asks:

- **Upload this collection as the new sync data** replaces what is in the place with this device's collection. Your other devices have to join again (the screen names them).
- **Use the sync data again** replaces this device's collection with the synced one, which discards the restore. A copy is saved first.

## A new phone

Install Mnemo, open **Settings › Sync**, choose the same place and enter the passphrase. Don't restore a backup first: joining downloads everything. (Backups of a device that syncs hold no keys, passphrases or
sign-ins, so the new phone asks again.)

## Stop syncing, or delete the sync data

- **Stop syncing on this device** keeps your cards here and leaves the files where they are; the other devices carry on without this one. It also signs out of Google or forgets the WebDAV password.
  You can join again later (that replaces this device's collection with the synced one, after saving a copy).
- **Delete the sync data** removes Mnemo's files from the place for every device. Your cards stay on this device, and the others find the sync data gone.

To remove Mnemo's access to Google completely, also revoke it in your Google Account under "Third-party apps with account access".

## Good to know

- Two devices that each have their own collection cannot be merged: one joins the other and is replaced (after a copy is saved).
- If a card's answer was undone on one device after another had already built on it, every review is still kept and all devices agree, but the schedule keeps the later answer.
- A folder is only as fast as the app that carries it. Files that Mnemo doesn't own in the folder (`.DS_Store`, a sync app's conflict copies) are ignored.
- Never rename, move or edit the files in the sync place by hand.
