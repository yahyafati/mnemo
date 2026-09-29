# Licensing and distribution: options

The last open question in PROJECT_OVERVIEW §11. This is the owner's decision; nothing in the
code depends on it. The facts that matter:

- Dependencies are all Apache-2.0 or MIT (AndroidX, Kotlin, OkHttp, jsoup, PdfBox-Android,
  zstd-kmp), plus KaTeX (MIT) and the bundled fonts (SIL OFL 1.1: Newsreader, Hanken Grotesk,
  JetBrains Mono). All are compatible with any of the licenses below and with closed source.
  Each requires its notices to ship with the app (an "Open-source licenses" screen or file).
- The FSRS port follows py-fsrs (MIT): keep its copyright notice.
- There are no Google Play Services or other proprietary SDKs, so an F-Droid build needs no
  flavor split.

| Option | Distribution | Notes |
|---|---|---|
| **GPL-3.0** | Play + F-Droid | Same as Anki/AnkiDroid; forks must stay open. Fits "the user owns the data". |
| **Apache-2.0 / MIT** | Play + F-Droid | Permissive; others can build closed products on it. |
| **Source-available / closed** | Play only | Allows a paid app or paid tier; F-Droid requires a FOSS license. |

Monetization that fits the non-goals (no accounts, no servers): a paid Play listing with a free
F-Droid build, donations, or none. There is no server cost to cover: AI runs on the user's own
provider key.

Once decided: add `LICENSE`, a licenses screen (e.g. `oss-licenses` or a generated asset), and for
F-Droid the `fastlane/metadata` texts from `store-listing.md` and reproducible-build settings.
