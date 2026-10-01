#!/usr/bin/env python3
"""Gets the files of one release ready to attach (sideload roadmap S2).

Run by the `release` job of `.github/workflows/release.yml` on the downloaded build artifacts:

    python3 scripts/release/prepare-release.py dist --version 1.0.0 --version-code 1 --notes-out notes.md

It expects exactly one of each installer in <dist> and fails naming the missing one, so a release
never goes out without a system. Then it

  * adds a copy of each file under a name without the version (`Mnemo-android.apk`, ...): the download
    page links `releases/latest/download/<stable name>`, which must not change from release to release,
  * writes one `SHA256SUMS.txt` over every file, versioned and stable (`sha256sum -c` reads it), and
  * renders the release notes from docs/release/release-notes.md and the version's F-Droid changelog.

`--check` only tells whether <dist> holds every file, for running it locally on fake files.
"""
import argparse
import hashlib
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
NOTES_TEMPLATE = ROOT / "docs/release/release-notes.md"
CHANGELOGS = ROOT / "fastlane/metadata/android/en-US/changelogs"

# (what it is, a way to recognise the versioned file, the name that never changes)
FILES = [
    ("Android APK", lambda n: n.endswith("-android.apk"), "Mnemo-android.apk"),
    ("Windows installer", lambda n: n.endswith("-windows-x64.msi"), "Mnemo-windows-x64.msi"),
    ("macOS disk image (Apple Silicon)", lambda n: n.endswith("-macos-arm64.dmg"), "Mnemo-macos-arm64.dmg"),
    ("macOS disk image (Intel)", lambda n: n.endswith("-macos-x64.dmg"), "Mnemo-macos-x64.dmg"),
    ("Linux portable archive", lambda n: n.endswith("-linux-x64.tar.gz"), "Mnemo-linux-x64.tar.gz"),
    ("Debian package", lambda n: n.endswith("_amd64.deb"), "mnemo-amd64.deb"),
    ("RPM package", lambda n: n.endswith(".x86_64.rpm"), "mnemo-x86_64.rpm"),
]


def find_files(dist: Path) -> list[tuple[Path, str]]:
    """The versioned file of each kind with its stable name; exits when one is missing or doubled."""
    stable_names = {f[2] for f in FILES} | {"SHA256SUMS.txt"}  # a re-run finds the copies it made
    versioned = sorted(p.name for p in dist.iterdir() if p.is_file() and p.name not in stable_names)
    found, problems = [], []
    for label, matches, stable in FILES:
        hits = [n for n in versioned if matches(n)]
        if len(hits) != 1:
            problems.append(f"{label}: expected one file, found {len(hits)} ({', '.join(hits) or 'none'})")
        else:
            found.append((dist / hits[0], stable))
    unknown = set(versioned) - {p.name for p, _ in found}
    if unknown and not problems:
        problems.append(f"files that match no known installer: {', '.join(sorted(unknown))}")
    if problems:
        sys.exit("The release is incomplete:\n  " + "\n  ".join(problems))
    return found


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def write_checksums(dist: Path) -> None:
    lines = [f"{sha256(p)}  {p.name}" for p in sorted(dist.iterdir()) if p.is_file() and p.name != "SHA256SUMS.txt"]
    (dist / "SHA256SUMS.txt").write_text("\n".join(lines) + "\n")


def render_notes(version: str, version_code: int) -> str:
    changelog = CHANGELOGS / f"{version_code}.txt"
    if not changelog.is_file():
        sys.exit(f"No changelog for versionCode {version_code}: add {changelog.relative_to(ROOT)}.")
    text = NOTES_TEMPLATE.read_text()
    for key, value in {"version": version, "changelog": changelog.read_text().strip()}.items():
        text = text.replace("{{" + key + "}}", value)
    if "{{" in text:
        sys.exit("release-notes.md has a placeholder this script does not fill.")
    return text


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("dist", type=Path, help="the folder with every installer")
    parser.add_argument("--version", required=True, help="mnemo.versionName, e.g. 1.0.0")
    parser.add_argument("--version-code", required=True, type=int, help="mnemo.versionCode")
    parser.add_argument("--notes-out", type=Path, help="where to write the release notes")
    parser.add_argument("--check", action="store_true", help="only check that every file is there")
    args = parser.parse_args()

    found = find_files(args.dist)
    if args.check:
        print(f"ok: {len(found)} installers")
        return
    for source, stable in found:
        shutil.copyfile(source, args.dist / stable)
    write_checksums(args.dist)
    if args.notes_out:
        args.notes_out.write_text(render_notes(args.version, args.version_code))
    print((args.dist / "SHA256SUMS.txt").read_text(), end="")


if __name__ == "__main__":
    main()
