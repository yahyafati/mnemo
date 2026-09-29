#!/usr/bin/env python3
"""Checks (or refreshes) the F-Droid store metadata in fastlane/metadata/android/en-US.

    python3 scripts/fdroid/fastlane.py          # validate; exits 1 on any problem
    python3 scripts/fdroid/fastlane.py --sync   # copy the graphics from docs/release/assets first

F-Droid reads these files from the app repo at the tagged commit (release ROADMAP R7). The
graphics are copies of docs/release/assets (plain files, not symlinks, so F-Droid's checkout
needs nothing special); --sync is the only place that maps one to the other, and the check fails
when a copy has drifted. No dependencies: the PNG size is read from the header.
"""
import pathlib
import re
import struct
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
META = ROOT / "fastlane/metadata/android/en-US"
ASSETS = ROOT / "docs/release/assets"

# fastlane file -> source under docs/release/assets. F-Droid shows screenshots sorted by name;
# the first eight are Play's picks in Play's order (play-console.md §3), the rest follow.
GRAPHICS = {
    "images/icon.png": "play-icon-512.png",
    "images/featureGraphic.png": "feature-graphic.png",
}
for i, name in enumerate(
    [
        "03-study-cloze-light", "01-home-light", "05-multiple-choice-light", "04-ai-explain-light",
        "06-smart-extract-light", "08-analytics-light", "07-co-author-light", "10-analytics-dark",
        "02-decks-list-light", "09-analytics-activity-light", "11-home-dark",
    ],
    start=1,
):
    GRAPHICS[f"images/phoneScreenshots/{i:02d}-{name.split('-', 1)[1]}.png"] = f"screenshots/phone/{name}.png"
for kind, folder in (("sevenInch", "tablet-7"), ("tenInch", "tablet-10")):
    for src in ("01-decks-tablet", "02-study-tablet"):
        GRAPHICS[f"images/{kind}Screenshots/{src}.png"] = f"screenshots/{folder}/{src}.png"

# Exact sizes where F-Droid's own guidance has one; the rest are checked for orientation only.
EXACT = {"images/icon.png": (512, 512), "images/featureGraphic.png": (1024, 500)}

LIMITS = {"title.txt": 50, "short_description.txt": 80, "full_description.txt": 4000}
CHANGELOG_BYTES = 500
# Play-only wording that doesn't belong on F-Droid.
PLAY_ONLY = re.compile(r"google play|play store|data safety|play console", re.I)


def png_size(path: pathlib.Path) -> tuple[int, int]:
    head = path.read_bytes()[:24]
    if head[:8] != b"\x89PNG\r\n\x1a\n" or head[12:16] != b"IHDR":
        raise ValueError("not a PNG")
    return struct.unpack(">II", head[16:24])


def version_code() -> int:
    text = (ROOT / "gradle.properties").read_text()
    m = re.search(r"^mnemo\.versionCode=(\d+)\s*$", text, re.M)
    if not m:
        sys.exit("gradle.properties: mnemo.versionCode not found")
    return int(m.group(1))


def sync() -> None:
    for target, source in GRAPHICS.items():
        dest = META / target
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes((ASSETS / source).read_bytes())
    # Drop screenshots that are no longer listed, so a renamed one doesn't linger.
    for folder in ("phoneScreenshots", "sevenInchScreenshots", "tenInchScreenshots"):
        for f in (META / "images" / folder).glob("*.png"):
            if f"images/{folder}/{f.name}" not in GRAPHICS:
                f.unlink()


def check() -> list[str]:
    errors: list[str] = []

    for name, limit in LIMITS.items():
        f = META / name
        if not f.is_file():
            errors.append(f"{name}: missing")
            continue
        text = f.read_text()
        if len(text.strip()) > limit:
            errors.append(f"{name}: {len(text.strip())} characters, limit {limit}")
        if not text.strip():
            errors.append(f"{name}: empty")
        if PLAY_ONLY.search(text):
            errors.append(f"{name}: mentions a Play-only term")
    if (META / "title.txt").is_file() and "\n" in (META / "title.txt").read_text().strip():
        errors.append("title.txt: must be one line")

    code = version_code()
    changelog = META / "changelogs" / f"{code}.txt"
    if not changelog.is_file():
        errors.append(f"changelogs/{code}.txt: missing (versionCode is {code}; F-Droid shows it for that build)")
    else:
        size = len(changelog.read_bytes())
        if size > CHANGELOG_BYTES:
            errors.append(f"changelogs/{code}.txt: {size} bytes, limit {CHANGELOG_BYTES}")
    for f in (META / "changelogs").glob("*.txt"):
        if not f.stem.isdigit():
            errors.append(f"changelogs/{f.name}: the name must be a versionCode")

    for target, source in GRAPHICS.items():
        dest, src = META / target, ASSETS / source
        if not dest.is_file():
            errors.append(f"{target}: missing (run with --sync)")
            continue
        try:
            w, h = png_size(dest)
        except ValueError as e:
            errors.append(f"{target}: {e}")
            continue
        if target in EXACT and (w, h) != EXACT[target]:
            errors.append(f"{target}: {w}x{h}, expected {EXACT[target][0]}x{EXACT[target][1]}")
        if "Screenshots" in target and max(w, h) > 3840:
            errors.append(f"{target}: {w}x{h} is larger than F-Droid's 3840 px cap")
        if dest.read_bytes() != src.read_bytes():
            errors.append(f"{target}: differs from docs/release/assets/{source} (run with --sync)")

    listed = {str(p.relative_to(META)) for p in (META / "images").rglob("*") if p.is_file()}
    for extra in sorted(listed - set(GRAPHICS)):
        errors.append(f"{extra}: not in GRAPHICS (delete it, or add it to scripts/fdroid/fastlane.py)")
    return errors


if __name__ == "__main__":
    if "--sync" in sys.argv[1:]:
        sync()
    problems = check()
    for p in problems:
        print(f"fastlane: {p}", file=sys.stderr)
    if problems:
        sys.exit(1)
    print(f"fastlane metadata OK (versionCode {version_code()}, {len(GRAPHICS)} graphics)")
