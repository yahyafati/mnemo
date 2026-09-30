#!/usr/bin/env python3
"""Helpers for the desktop QA pass (docs/desktop/qa.md). Each subcommand does the part of a check that a
machine can do; the rest (looking at the window, listening to audio) is in the runbook.

    desktop-checks.py info                          system, display, Java, data folder: paste into the results log
    desktop-checks.py verify-sums <dir>             check the downloaded installers against SHA256SUMS.txt
    desktop-checks.py startup <app image> [--runs 3]   cold start on an empty collection, until the database exists
    desktop-checks.py import-time <app image> <package>   start the app on an empty collection with a package to import, and time it
    desktop-checks.py counts [--data DIR]           what a collection holds (decks, notes, cards, reviews, media)
    desktop-checks.py mock-ai                       serve the canned OpenAI-compatible replies on 127.0.0.1:11435

`<app image>` is what `:desktop:createDistributable` writes (desktop/build/compose/binaries/main/app), or an
installed copy's folder. Standard library only.
"""
import argparse
import hashlib
import os
import platform
import re
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def data_dir() -> Path:
    override = os.environ.get("MNEMO_DATA_DIR")
    if override:
        return Path(override)
    system = platform.system()
    if system == "Windows":
        return Path(os.environ.get("LOCALAPPDATA", str(Path.home() / "AppData" / "Local"))) / "Mnemo"
    if system == "Darwin":
        return Path.home() / "Library" / "Application Support" / "Mnemo"
    return Path(os.environ.get("XDG_DATA_HOME", str(Path.home() / ".local" / "share"))) / "mnemo"


def run(*command: str) -> str:
    try:
        return subprocess.run(command, capture_output=True, text=True, timeout=20).stdout.strip()
    except (OSError, subprocess.SubprocessError):
        return ""


def info(_args) -> None:
    system = platform.system()
    print(f"System:        {platform.platform()} ({platform.machine()})")
    if system == "Darwin":
        displays = run("system_profiler", "SPDisplaysDataType")
        print("Displays:      " + "; ".join(re.findall(r"Resolution: (.*)", displays)) or "unknown")
    elif system == "Windows":
        print("Displays:      " + run("powershell", "-NoProfile", "-Command",
              "Add-Type -AssemblyName System.Windows.Forms; [System.Windows.Forms.Screen]::AllScreens | ForEach-Object { \"$($_.Bounds.Width)x$($_.Bounds.Height)\" }").replace("\n", "; "))
        print("Scaling:       set in Settings › System › Display (record the percentage by hand)")
    else:
        print(f"Session:       {os.environ.get('XDG_SESSION_TYPE', '?')} on {os.environ.get('XDG_CURRENT_DESKTOP', '?')}")
        print(f"Scale vars:    GDK_SCALE={os.environ.get('GDK_SCALE', '')} QT_SCALE_FACTOR={os.environ.get('QT_SCALE_FACTOR', '')}")
        print("Displays:      " + (run("xrandr", "--current").split("\n")[0] or "xrandr not found"))
    java = shutil.which("java")
    print(f"Java on PATH:  {run(java, '-version') or 'none (the packaged app brings its own)'}" if java else "Java on PATH:  none (the packaged app brings its own)")
    folder = data_dir()
    print(f"Data folder:   {folder} ({'exists' if folder.is_dir() else 'not created yet'})")
    if (folder / "mnemo.db").is_file():
        print(f"Collection:    {(folder / 'mnemo.db').stat().st_size / 1e6:.1f} MB database, {sum(1 for _ in (folder / 'media').glob('*')) if (folder / 'media').is_dir() else 0} media files")
    keyfile = folder / "secrets.key"
    print(f"Key storage:   {'a key file (no keychain was used)' if keyfile.is_file() else 'keychain, or no key stored yet'}")


def verify_sums(args) -> None:
    folder = Path(args.folder)
    sums = folder / "SHA256SUMS.txt"
    if not sums.is_file():
        sys.exit(f"no SHA256SUMS.txt in {folder}")
    bad = 0
    for line in sums.read_text().splitlines():
        expected, _, name = line.strip().partition("  ")
        name = name.lstrip("*")
        path = folder / name
        if not path.is_file():
            print(f"missing   {name}")
            continue
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        ok = digest == expected
        bad += not ok
        print(f"{'ok       ' if ok else 'MISMATCH '}{name}")
    sys.exit(1 if bad else 0)


def launcher(image: Path) -> Path:
    candidates = {
        "Darwin": [image / "Mnemo.app" / "Contents" / "MacOS" / "Mnemo", image / "Contents" / "MacOS" / "Mnemo"],
        "Windows": [image / "Mnemo" / "Mnemo.exe", image / "Mnemo.exe"],
        "Linux": [image / "Mnemo" / "bin" / "Mnemo", image / "bin" / "Mnemo"],
    }[platform.system()]
    for path in candidates:
        if path.is_file():
            return path
    sys.exit(f"no launcher under {image}")


def startup(args) -> None:
    program = launcher(Path(args.image).resolve())
    times = []
    for run_number in range(args.runs):
        scratch = Path(tempfile.mkdtemp(prefix="mnemo-startup-"))
        began = time.monotonic()
        process = subprocess.Popen([str(program)], env={**os.environ, "MNEMO_DATA_DIR": str(scratch)}, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            while not (scratch / "mnemo.db").is_file():
                if process.poll() is not None:
                    sys.exit(f"run {run_number + 1}: the app exited with status {process.returncode}")
                if time.monotonic() - began > 120:
                    sys.exit(f"run {run_number + 1}: no database after 120 s")
                time.sleep(0.05)
            times.append(time.monotonic() - began)
            print(f"run {run_number + 1}: database created after {times[-1]:.1f} s")
        finally:
            process.terminate()
            try:
                process.wait(10)
            except subprocess.TimeoutExpired:
                process.kill()
            shutil.rmtree(scratch, ignore_errors=True)
    print(f"median {sorted(times)[len(times) // 2]:.1f} s (the window is up a little after this; the first run on a system is slowest)")


def import_time(args) -> None:
    program = launcher(Path(args.image).resolve())
    package = Path(args.package).resolve()
    if not package.is_file():
        sys.exit(f"no such file: {package}")
    scratch = Path(tempfile.mkdtemp(prefix="mnemo-import-"))
    began = time.monotonic()
    # The file goes in as the program's argument, which is what double-clicking a package does.
    process = subprocess.Popen([str(program), str(package)], env={**os.environ, "MNEMO_DATA_DIR": str(scratch)}, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    last, changed_at, first_card_at = -1, began, None
    try:
        while True:
            if process.poll() is not None:
                sys.exit(f"the app exited with status {process.returncode}")
            now = time.monotonic()
            if now - began > args.timeout:
                sys.exit(f"still importing after {args.timeout} s (cards so far: {last})")
            total = -1
            if (scratch / "mnemo.db").is_file():
                try:
                    connection = sqlite3.connect(f"file:{scratch / 'mnemo.db'}?mode=ro", uri=True, timeout=1)
                    total = connection.execute("SELECT COUNT(*) FROM cards WHERE deletedAt IS NULL").fetchone()[0]
                    connection.close()
                except sqlite3.Error:
                    pass
            if total != last:
                last, changed_at = total, now
                if total > 0 and first_card_at is None:
                    first_card_at = now
            # The import is one transaction per batch: call it finished once the count has held for 8 s.
            if last > 0 and now - changed_at > 8:
                break
            time.sleep(0.5)
        seconds = changed_at - began
        print(f"{last} cards after {seconds:.1f} s from launch ({last / seconds:.0f} cards/s; includes the app's own start, about 1 to 3 s)")
        print("Compare with the package's counts, then open Decks and Browse to see it.")
    finally:
        process.terminate()
        try:
            process.wait(10)
        except subprocess.TimeoutExpired:
            process.kill()
        if args.keep:
            print(f"collection kept in {scratch}")
        else:
            shutil.rmtree(scratch, ignore_errors=True)


def counts(args) -> None:
    folder = Path(args.data) if args.data else data_dir()
    database = folder / "mnemo.db"
    if not database.is_file():
        sys.exit(f"no collection at {database}")
    # Read a copy: the running app holds the database in WAL mode.
    scratch = Path(tempfile.mkdtemp(prefix="mnemo-counts-"))
    try:
        for suffix in ("", "-wal", "-shm"):
            if (folder / f"mnemo.db{suffix}").is_file():
                shutil.copy2(folder / f"mnemo.db{suffix}", scratch / f"mnemo.db{suffix}")
        connection = sqlite3.connect(scratch / "mnemo.db")
        one = lambda sql: connection.execute(sql).fetchone()[0]
        print(f"schema version  {one('PRAGMA user_version')}")
        print(f"integrity       {one('PRAGMA integrity_check')}")
        for label, table in (("decks", "decks"), ("notes", "notes"), ("cards", "cards"), ("review logs", "review_logs"), ("media rows", "media")):
            print(f"{label:<15} {one(f'SELECT COUNT(*) FROM {table} WHERE deletedAt IS NULL')}")
        print("new / learning / review / relearning   " + " / ".join(str(one(f"SELECT COUNT(*) FROM cards WHERE deletedAt IS NULL AND state = {state}")) for state in (0, 1, 2, 3)))
        media = folder / "media"
        print(f"media files     {sum(1 for _ in media.glob('*')) if media.is_dir() else 0} on disk")
        print(f"providers       {one('SELECT COUNT(*) FROM ai_providers')} (their keys are not in the database)")
        connection.close()
    finally:
        shutil.rmtree(scratch, ignore_errors=True)


def mock_ai(_args) -> None:
    print("Add an Ollama-preset provider with base URL http://127.0.0.1:11435/v1 (marked local), then Test connection. Ctrl-C to stop.")
    os.execvp(sys.executable, [sys.executable, str(ROOT / "docs" / "release" / "assets" / "demo" / "mock_ai_server.py")])


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("info").set_defaults(run=info)
    sums = commands.add_parser("verify-sums")
    sums.add_argument("folder")
    sums.set_defaults(run=verify_sums)
    start = commands.add_parser("startup")
    start.add_argument("image")
    start.add_argument("--runs", type=int, default=3)
    start.set_defaults(run=startup)
    timed = commands.add_parser("import-time")
    timed.add_argument("image")
    timed.add_argument("package")
    timed.add_argument("--timeout", type=int, default=900)
    timed.add_argument("--keep", action="store_true", help="keep the scratch collection (then run counts --data on it)")
    timed.set_defaults(run=import_time)
    count = commands.add_parser("counts")
    count.add_argument("--data", help="a data folder (default: this system's, or MNEMO_DATA_DIR)")
    count.set_defaults(run=counts)
    commands.add_parser("mock-ai").set_defaults(run=mock_ai)
    args = parser.parse_args()
    args.run(args)


if __name__ == "__main__":
    main()
