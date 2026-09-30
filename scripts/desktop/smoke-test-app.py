#!/usr/bin/env python3
"""Starts a packaged desktop app and checks it comes up (desktop ROADMAP D8).

    python3 scripts/desktop/smoke-test-app.py desktop/build/compose/binaries/main/app [--open-file deck.apkg]

The argument is the directory `:desktop:createDistributable` writes (`Mnemo.app` on macOS, `Mnemo/` on
Windows and Linux). The app runs against a scratch data directory (MNEMO_DATA_DIR) and must

  1. create its database, which means the trimmed Java runtime loaded the UI, SQLite and the data layer,
  2. still be running a few seconds later, with no class or library error on its output,
  3. with --open-file: hand the file to the running app when it is launched a second time (what a
     double-clicked .apkg does on Windows and Linux), which then takes the request off the folder.

It needs a display: on Linux CI run it under `xvfb-run`. Exit status 0 means the app is fine.
"""
import argparse
import os
import platform
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

STARTUP_SECONDS = 90
SETTLE_SECONDS = 5
ERROR_MARKERS = ("NoClassDefFoundError", "ClassNotFoundException", "UnsatisfiedLinkError", "Could not find or load main class", "Exception in thread")


def launcher(image: Path) -> Path:
    system = platform.system()
    candidates = {
        "Darwin": [image / "Mnemo.app" / "Contents" / "MacOS" / "Mnemo"],
        "Windows": [image / "Mnemo" / "Mnemo.exe"],
        "Linux": [image / "Mnemo" / "bin" / "Mnemo"],
    }.get(system, [])
    for path in candidates:
        if path.is_file():
            return path
    sys.exit(f"no launcher found under {image} (looked for {[str(c) for c in candidates]})")


def wait_for(condition, seconds: float, process: subprocess.Popen, what: str) -> None:
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if condition():
            return
        if process.poll() is not None:
            sys.exit(f"the app exited with status {process.returncode} before {what}")
        time.sleep(0.25)
    sys.exit(f"timed out after {seconds:.0f} s waiting for {what}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("image", type=Path)
    parser.add_argument("--open-file", type=Path, help="a file to hand to the running app through a second launch")
    args = parser.parse_args()

    program = launcher(args.image.resolve())
    data = Path(tempfile.mkdtemp(prefix="mnemo-smoke-"))
    environment = {**os.environ, "MNEMO_DATA_DIR": str(data)}
    output = data.parent / f"{data.name}.log"
    process = None
    try:
        with open(output, "wb") as log:
            process = subprocess.Popen([str(program)], env=environment, stdout=log, stderr=subprocess.STDOUT)
            wait_for(lambda: (data / "mnemo.db").is_file(), STARTUP_SECONDS, process, "the database to be created")
            time.sleep(SETTLE_SECONDS)
            if process.poll() is not None:
                sys.exit(f"the app exited with status {process.returncode} after starting")
            if args.open_file:
                second = subprocess.run([str(program), str(args.open_file.resolve())], env=environment, stdout=log, stderr=subprocess.STDOUT, timeout=STARTUP_SECONDS)
                if second.returncode != 0:
                    sys.exit(f"the second launch exited with status {second.returncode}, not 0 (the file was not handed over)")
                requests = data / "open-requests"
                wait_for(lambda: not list(requests.glob("*.request")), 15, process, "the running app to take the request")
        text = output.read_text(errors="replace")
        for marker in ERROR_MARKERS:
            if marker in text:
                sys.exit(f"the app's output contains {marker}:\n{text[-3000:]}")
        print(f"ok: {program.name} started, created its database and kept running" + (", and took a file from a second launch" if args.open_file else ""))
    finally:
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(10)
            except subprocess.TimeoutExpired:
                process.kill()
        shutil.rmtree(data, ignore_errors=True)
        output.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
