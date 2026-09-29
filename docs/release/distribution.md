# Licensing and distribution

**Decided 2026-09-29 ([ADR 0009](../adr/0009-license-and-distribution.md)):** Mnemo is
**GPL-3.0-or-later**, distributed **free on Google Play and F-Droid**. The steps to get there are
in [ROADMAP.md](ROADMAP.md). This page keeps the facts behind the decision and what it requires.

## Facts

- Dependencies are all Apache-2.0 or MIT (AndroidX, Kotlin, OkHttp, jsoup, PdfBox-Android,
  zstd-kmp), plus KaTeX (MIT) and the bundled fonts (SIL OFL 1.1: Newsreader, Hanken Grotesk,
  JetBrains Mono). All are compatible with the GPL-3.0. Each requires its notices to ship with
  the app: `NOTICE` in the repo, and the "Open-source licenses" screen in Settings › About.
- The FSRS port follows py-fsrs (MIT): its copyright notice is in `NOTICE`.
- There are no Google Play Services or other proprietary SDKs, so an F-Droid build needs no
  flavor split.

## Options that were considered

| Option | Distribution | Notes |
|---|---|---|
| **GPL-3.0** (chosen) | Play + F-Droid | Same as Anki/AnkiDroid; forks must stay open. Fits "the user owns the data". |
| Apache-2.0 / MIT | Play + F-Droid | Permissive; others can build closed products on it. |
| Source-available / closed | Play only | Allows a paid app or paid tier; F-Droid requires a FOSS license. |

There is no monetization: no paid tier, ads, accounts or servers. AI runs on the user's own
provider key, so there is no server cost to cover.

## What the GPL requires of us

- Offer the source to anyone who gets a binary: a public repository with a tag per release.
- Ship the license and third-party notices with the app (`LICENSE` and `NOTICE` in the source,
  the licenses screen and About in the app).
- Keep `local.properties`, keystores and secrets out of the repository and its history.

## Still to do

The public repository, the upload keystore and the F-Droid metadata are R0, R2 and R7 in
[ROADMAP.md](ROADMAP.md). Signing and releasing are in [signing.md](signing.md), and the F-Droid
texts come from [store-listing.md](store-listing.md).
