# Mnemo privacy policy

_Last updated: 29 September 2026_

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

## API keys

Keys you enter are encrypted with a key held by the Android Keystore, stored outside every backed
up location, never exported, never shown in full after saving, and never written to logs.

## Permissions

| Permission | Why |
|---|---|
| Internet | Only for AI providers and links you open in Smart Extract. Everything else works offline. |
| Notifications | The daily study reminder (if you turn it on) and progress of long imports, exports and backups. |
| Microphone | Only while you dictate in Smart Extract. |
| Foreground service (data sync) | Keeps a long import, export or backup running if you leave the app. |

## Children

Mnemo collects no personal data from anyone, including children.

## Changes

Changes to this policy ship with app updates and are listed in the release notes.

## Contact

Questions: yfati037@gmail.com.
