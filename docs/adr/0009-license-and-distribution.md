# ADR 0009: License and distribution

- **Status:** Accepted
- **Date:** 2026-09-29
- **Context for:** release ROADMAP R0–R7; PROJECT_OVERVIEW §11; ADR 0008 "Open"; `docs/release/distribution.md`

## Decision

### License: GPL-3.0-or-later

Mnemo is released under the GNU General Public License, version 3 or (at the user's option) any
later version. `LICENSE` holds the GPL-3.0 text and `NOTICE` holds the copyright line, the
`SPDX-License-Identifier: GPL-3.0-or-later` and the third-party notices.

- It is the license of Anki and AnkiDroid, which Mnemo imports from and exports to. It fits the
  product: users own their data, and a fork can't turn Mnemo into a closed product.
- Every dependency and bundled file is compatible with it: Apache-2.0 (AndroidX, Compose, Kotlin,
  Dagger/Hilt, OkHttp, Okio, zstd-kmp, PdfBox-Android), MIT (jsoup, KaTeX, py-fsrs) and SIL OFL 1.1
  (the three bundled fonts). GPL-3.0 can include Apache-2.0 code, but not the other way round,
  which is why the license had to be chosen before the first outside build.
- The FSRS scheduler and optimizer are ports of py-fsrs (MIT). Its copyright and permission
  notice ship in `NOTICE`.
- Source files carry no per-file headers. `LICENSE` and `NOTICE` at the root cover the tree.
  A file copied in from elsewhere keeps its own header.

### Distribution: Google Play and F-Droid, both free

- No paid tier, ads, accounts, servers or tracking. There is no server cost to cover: AI runs on
  the user's own provider key.
- **Source offer:** the source is public, with a tag for every release. That satisfies the GPL
  for the Play binary and is what F-Droid builds from.
- **No proprietary SDKs.** Play Services, Firebase and anything that needs a flavor split are out.
  One source tree and one build produce both distributions.
- **Two signing keys.** Play re-signs the AAB with the app-signing key that Google holds, and the
  upload key stays with the owner. F-Droid signs its own build with its own key, so the two
  builds can't update each other: a user who switches reinstalls, and the data moves through a
  Mnemo backup. Reproducible builds could later let F-Droid publish the developer-signed APK.
- **F-Droid anti-feature:** `NonFreeNet`, because AI providers can be proprietary services. The
  AI features are optional, and local servers (Ollama, LM Studio) work.
- The open-source licenses screen (R2) uses AboutLibraries, generated at build time. Google's
  `oss-licenses-plugin` is not used: it needs Play Services and would break the F-Droid build.

### Alternatives

- **Apache-2.0 / MIT:** compatible with both stores, but lets others ship a closed fork of an
  app whose point is data ownership.
- **Source-available or closed:** allows a paid app, but rules out F-Droid and conflicts with
  the project's ethos. No monetization is needed under the non-goals.

## Consequences

- The repository must be public before any build is distributed. Before the first push:
  `local.properties`, `*.jks` and `*.keystore` are ignored, and the history has no keys or
  passwords (checked 2026-09-29: 13 commits, none).
- The signing config (R2) reads the upload keystore from `local.properties` or environment
  variables and falls back to unsigned, so F-Droid and CI can still build.
- Contributions are accepted under the same license (inbound = outbound).
- Anything added later that isn't GPL-compatible, or that needs a proprietary SDK, needs a new
  ADR first.
