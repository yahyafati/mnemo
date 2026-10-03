#!/usr/bin/env python3
"""Fails if Android APIs are imported outside the files that are meant to be Android-only.

    python3 scripts/desktop/check-android-imports.py           # check (CI)
    python3 scripts/desktop/check-android-imports.py --list    # print every file that imports one

The desktop app (docs/desktop/ROADMAP.md) shares the domain, data and UI code with Android. That
code may not import Android APIs: each Android API sits behind an interface (`AppDirectories`,
`DocumentAccess`, `PlatformCapabilities`, `MathRenderer`, ...) whose Android implementation lives
in a directory named `android`. A module that has turned into a Kotlin Multiplatform module (D4
onwards) keeps its Android code in the `androidMain` source set; `commonMain` and `desktopMain`
are scanned like `src/main` of an Android module.

A file may import them when it is
  * under a directory named `android` or `androidMain` (the Android implementations), or
  * listed in PENDING below: Android code that has not moved yet, with the step that moves it.
    The list only shrinks. A listed file that no longer imports an Android API fails the check, so
    finish the move by deleting its entry.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# Import prefixes of Android-only APIs (a prefix ends at a package boundary).
FORBIDDEN = [
    "android",
    "androidx.work",
    "androidx.activity",
    "androidx.webkit",
    "androidx.documentfile",
    "androidx.core",
    "androidx.compose.ui.platform.LocalContext",
    "androidx.compose.ui.viewinterop.AndroidView",
]

# Modules whose main sources are scanned. `:app` and the F-Droid/Play packaging are Android by
# nature, but only its entry points are exempt (see PENDING).
SCAN = ["core", "feature", "app", "desktop"]

# Source sets that may never import Android: everything but the Android-only ones.
SOURCE_SETS = ["main", "commonMain", "desktopMain", "androidMain"]

# Directory names whose files are Android-only by definition.
ANDROID_DIRECTORIES = {"android", "androidMain"}

# path (relative to the repository root) -> why it is Android-only for now.
PENDING = {
    # The Android application: entry points, widget, manifest-registered components.
    "app/src/main/java/com/yahyafati/mnemo/MainActivity.kt": "Android launcher (stays in :app)",
    "app/src/main/java/com/yahyafati/mnemo/MnemoApplication.kt": "Android launcher (stays in :app)",
    "app/src/main/java/com/yahyafati/mnemo/OAuthRedirectActivity.kt": "receives Google's sign-in redirect for Drive sync (stays in :app)",
    "app/src/main/java/com/yahyafati/mnemo/widget/TodayWidget.kt": "home-screen widget: Android only (stays in :app)",
}


def imports(path: Path):
    """Yields (line number, imported name) for every import in a Kotlin or Java file."""
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        match = re.match(r"\s*import\s+(?:static\s+)?([\w.]+)", line)
        if match:
            yield number, match.group(1)


def is_forbidden(name: str) -> bool:
    return any(name == p or name.startswith(p + ".") for p in FORBIDDEN)


def sources():
    for module in SCAN:
        for source_set in SOURCE_SETS:
            for path in sorted((ROOT / module).rglob(f"src/{source_set}/**/*")):
                if path.suffix in (".kt", ".java") and path.is_file():
                    yield path


def main() -> int:
    listing = "--list" in sys.argv[1:]
    offenders = {}
    for path in sources():
        hits = [(n, name) for n, name in imports(path) if is_forbidden(name)]
        if hits:
            offenders[path.relative_to(ROOT).as_posix()] = hits

    if listing:
        for rel, hits in offenders.items():
            print(f"{rel}: {hits[0][1]}" + (f" (+{len(hits) - 1})" if len(hits) > 1 else ""))
        return 0

    failures = []
    for rel, hits in offenders.items():
        if ANDROID_DIRECTORIES & set(Path(rel).parts[:-1]) or rel in PENDING:
            continue
        for number, name in hits:
            failures.append(f"{rel}:{number}: imports {name}")
    stale = [rel for rel in PENDING if rel not in offenders]
    for rel in stale:
        failures.append(f"{rel}: no longer imports an Android API; remove it from PENDING")
    missing = [rel for rel in PENDING if not (ROOT / rel).exists()]
    for rel in missing:
        if rel not in stale:
            failures.append(f"{rel}: listed in PENDING but does not exist")

    if failures:
        print("check-android-imports: Android APIs outside the Android-only files:", file=sys.stderr)
        for failure in failures:
            print(f"  {failure}", file=sys.stderr)
        print(
            "Put the API behind an interface and its implementation in a directory named `android`\n"
            "or in `androidMain` (see the docstring of this script and docs/desktop/ROADMAP.md, D3).",
            file=sys.stderr,
        )
        return 1
    print(f"check-android-imports: ok ({len(offenders)} Android-only files, {len(PENDING)} pending a later step)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
