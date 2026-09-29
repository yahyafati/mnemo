#!/usr/bin/env python3
"""Fails if the release dependency tree contains a proprietary Google/Firebase SDK.

    ./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath | python3 scripts/fdroid/check-foss-deps.py

F-Droid refuses apps that link Play Services, Firebase, Play Billing and the like, and ADR 0009
rules them out for every distribution (one build serves Play and F-Droid). LicensesFlowTest checks
the same thing from the AboutLibraries output; this one reads the Gradle graph directly, so CI
catches it before a release build. Reads stdin, or a file given as the first argument.
"""
import re
import sys

# Group prefixes (or group:name for a coordinate inside an otherwise fine group).
BANNED = [
    "com.google.android.gms",
    "com.google.android.play",
    "com.google.android.ump",
    "com.google.android.datatransport",
    "com.google.firebase",
    "com.google.mlkit",
    "com.android.billingclient",
    "com.crashlytics",
    "io.fabric",
    "com.google.android.libraries",
]
# `group:name:version` or `group:name -> version` at the end of a dependency-tree line.
COORDINATE = re.compile(r"[+\\]--- ([\w.\-]+):([\w.\-]+)")


def main() -> int:
    text = open(sys.argv[1]).read() if len(sys.argv) > 1 else sys.stdin.read()
    coordinates = sorted({f"{g}:{n}" for g, n in COORDINATE.findall(text)})
    if not coordinates:
        print("check-foss-deps: no dependencies found in the input (wrong configuration?)", file=sys.stderr)
        return 2
    hits = [c for c in coordinates if any(c.startswith(b) for b in BANNED)]
    for c in hits:
        print(f"check-foss-deps: proprietary dependency {c}", file=sys.stderr)
    if hits:
        return 1
    print(f"check-foss-deps: {len(coordinates)} libraries, none proprietary")
    return 0


if __name__ == "__main__":
    sys.exit(main())
