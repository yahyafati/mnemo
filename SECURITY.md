# Security policy

Mnemo keeps your collection on your device and has no server, so the main risks are in the app itself:
how it stores AI provider keys, how it reads files it imports (`.apkg`, `.colpkg`, PDF, web pages), and
how it handles backups.

## Reporting a vulnerability

**Please do not open a public issue for a security problem.**

- Preferred: [report it privately on GitHub](https://github.com/yahyafati/mnemo/security/advisories/new).
- Or write to **yfati037@gmail.com** (the address in the [privacy policy](docs/release/privacy-policy.md)).

Say which version you have (Settings › About), what system you use, and how to reproduce the problem.
Do not include your own cards or API keys; a small test file is better.

This is a one-person, unpaid project. I will read the report and answer as soon as I can, and fix confirmed problems
in a following release. If you want credit in the release notes, say so.

## Supported versions

Only the latest release gets fixes. Updates are not automatic: see
[Updates](https://yahyafati.github.io/mnemo/#updates) on the download page.

## Out of scope

- A key or collection read from a device that is already rooted, or from an unlocked machine you share.
- The unsigned-installer warnings on Windows and macOS (known; see the install notes).
- Output that an AI provider gives you. If it is harmful or wrong, use the Report button next to it.
