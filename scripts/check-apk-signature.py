#!/usr/bin/env python3
"""Checks that a release APK is really signed, and signed by the key you expect.

`assembleRelease` falls back to an unsigned APK when the MNEMO_KEYSTORE_* settings are missing
(ReleaseConfig.kt), and an unsigned APK can't be installed. This runs `apksigner verify` and fails if

  * the APK has no signature, or the signature doesn't verify,
  * it is signed with the Android debug key,
  * it lacks the v2+ (APK Signature Scheme) signature that Android 11+ requires, or
  * with --cert-sha256, the signer's certificate isn't that one, or, with several APKs,
    they aren't all signed by the same certificate (Android refuses to update an app whose
    signer changed, so this is the offline version of the "install over the old build" check).

    python3 scripts/check-apk-signature.py app/build/outputs/apk/release/app-release.apk
    python3 scripts/check-apk-signature.py old.apk new.apk
    python3 scripts/check-apk-signature.py --cert-sha256 <64 hex digits> Mnemo-1.0.0-android.apk

Prints the signer's SHA-256 fingerprint: write it down once, and CI can pin it. `apksigner` comes
from the Android SDK build-tools (ANDROID_HOME / ANDROID_SDK_ROOT / `sdk.dir` in local.properties,
or on PATH). Never prints a password and never needs one.
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def sdk_dirs() -> list[Path]:
    dirs = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    local = ROOT / "local.properties"
    if local.is_file():
        for line in local.read_text().splitlines():
            if line.startswith("sdk.dir="):
                dirs.append(line.split("=", 1)[1].strip().replace("\\:", ":").replace("\\\\", "\\"))
    return [Path(d) for d in dirs if d]


def version_key(path: Path) -> list[int]:
    return [int(p) for p in re.findall(r"\d+", path.name)]


def find_apksigner() -> str:
    for sdk in sdk_dirs():
        tools = sorted((sdk / "build-tools").glob("*/apksigner"), key=lambda p: version_key(p.parent))
        if tools:
            return str(tools[-1])
    found = shutil.which("apksigner")
    if found:
        return found
    sys.exit("apksigner not found: install the Android SDK build-tools or set ANDROID_HOME.")


def check(apksigner: str, apk: Path, expected: str | None) -> tuple[str | None, list[str]]:
    """Returns the signer's SHA-256 (None when unsigned) and the problems found."""
    result = subprocess.run(
        [apksigner, "verify", "--verbose", "--print-certs", "--min-sdk-version", "29", str(apk)],
        capture_output=True,
        text=True,
    )
    out = result.stdout + result.stderr
    if result.returncode != 0:
        reason = "; ".join(line.strip() for line in out.strip().splitlines()[:2]) or "apksigner failed"
        return None, [f"does not verify ({reason}): is it unsigned? Check MNEMO_KEYSTORE_*"]

    problems = []
    fingerprints = re.findall(r"certificate SHA-256 digest: ([0-9a-f]{64})", out)
    if not fingerprints:
        return None, ["no signer certificate in the apksigner output"]
    if len(set(fingerprints)) > 1:
        problems.append("signed by more than one certificate")
    fingerprint = fingerprints[0]

    # v1 alone doesn't cover Android 11+ (it needs v2 or newer).
    if not re.search(r"Verified using v(2|3|3\.1|4) scheme[^:]*: true", out):
        problems.append("no v2 or newer signature")
    if re.search(r"certificate DN: .*(Android Debug|CN=Android)", out, re.IGNORECASE):
        problems.append("signed with the Android debug key")
    if expected and fingerprint != expected.lower().replace(":", ""):
        problems.append(f"signer is {fingerprint}, expected {expected.lower()}")
    return fingerprint, problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("apk", nargs="+", type=Path)
    parser.add_argument("--cert-sha256", help="the signer certificate's SHA-256 fingerprint (colons allowed)")
    args = parser.parse_args()

    apksigner = find_apksigner()
    failed = False
    signers = {}
    for apk in args.apk:
        if not apk.is_file():
            print(f"FAIL {apk}: no such file")
            failed = True
            continue
        fingerprint, problems = check(apksigner, apk, args.cert_sha256)
        if fingerprint:
            signers[apk] = fingerprint
        if problems:
            failed = True
            for problem in problems:
                print(f"FAIL {apk}: {problem}")
        else:
            print(f"ok   {apk}: signer SHA-256 {fingerprint}")

    if len(set(signers.values())) > 1:
        print("FAIL the APKs are signed by different certificates: Android won't update one with the other.")
        failed = True
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
