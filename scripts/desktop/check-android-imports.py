#!/usr/bin/env python3
"""Fails if Android APIs are imported outside the files that are meant to be Android-only.

    python3 scripts/desktop/check-android-imports.py           # check (CI)
    python3 scripts/desktop/check-android-imports.py --list    # print every file that imports one

The desktop app (docs/desktop/ROADMAP.md) shares the domain, data and UI code with Android. That
code may not import Android APIs: each Android API sits behind an interface (`AppDirectories`,
`DocumentAccess`, `PlatformCapabilities`, `MathRenderer`, ...) whose Android implementation lives
in a directory named `android`, which becomes the `androidMain` source set when a module turns
into a Kotlin Multiplatform module (D4-D6).

A file may import them when it is
  * under a directory named `android` (the convention for new Android implementations), or
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

# path (relative to the repository root) -> why it is Android-only for now.
PENDING = {
    # The Android application: entry points, widget, manifest-registered components.
    "app/src/main/java/com/yahyafati/mnemo/MainActivity.kt": "Android launcher (stays in :app)",
    "app/src/main/java/com/yahyafati/mnemo/MnemoApplication.kt": "Android launcher (stays in :app)",
    "app/src/main/java/com/yahyafati/mnemo/widget/TodayWidget.kt": "home-screen widget: Android only (stays in :app)",
    # Room and the framework SQLite classes: D4 moves them to Room's multiplatform driver API.
    "core/database/src/main/kotlin/com/yahyafati/mnemo/core/database/MnemoDatabase.kt": "D4: Room on the framework driver",
    # The licenses list reads AboutLibraries' JSON from an Android raw resource: D6 passes it in as text.
    "feature/settings/src/main/kotlin/com/yahyafati/mnemo/feature/settings/LicensesScreen.kt": "D6: raw resource id",
    # Keystore: D4 adds the desktop SecretCipher.
    "core/security/src/main/kotlin/com/yahyafati/mnemo/core/security/SecretCipher.kt": "D4: Android Keystore cipher",
    # WorkManager and notifications: the Android job runner. D4 adds the desktop one behind the same repositories.
    "core/data/src/main/kotlin/com/yahyafati/mnemo/core/data/di/DataModule.kt": "D4: Koin bindings of the Android implementations",
    "core/data/src/main/kotlin/com/yahyafati/mnemo/core/data/repository/WorkManagerDataTransferRepository.kt": "D4: WorkManager runner",
    "core/data/src/main/kotlin/com/yahyafati/mnemo/core/data/repository/WorkManagerFsrsOptimizationRepository.kt": "D4: WorkManager runner",
    "core/data/src/main/kotlin/com/yahyafati/mnemo/core/data/repository/WorkManagerReminderRepository.kt": "D4: WorkManager runner",
    "core/data/src/main/kotlin/com/yahyafati/mnemo/core/data/work/OptimizeFsrsWorker.kt": "D4: WorkManager runner",
    "core/data/src/main/kotlin/com/yahyafati/mnemo/core/data/work/ReminderWorker.kt": "D4: WorkManager runner and notification",
    "core/data/src/main/kotlin/com/yahyafati/mnemo/core/data/work/TransferNotifications.kt": "D4: WorkManager runner and notification",
    "core/data/src/main/kotlin/com/yahyafati/mnemo/core/data/work/TransferWorkers.kt": "D4: WorkManager runner",
    "core/data/src/main/kotlin/com/yahyafati/mnemo/core/data/work/WorkStates.kt": "D4: WorkManager runner",
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
        for path in sorted((ROOT / module).rglob("src/main/**/*")):
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
        if "android" in Path(rel).parts[:-1] or rel in PENDING:
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
            "(see the docstring of this script and docs/desktop/ROADMAP.md, D3).",
            file=sys.stderr,
        )
        return 1
    print(f"check-android-imports: ok ({len(offenders)} Android-only files, {len(PENDING)} pending a later step)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
