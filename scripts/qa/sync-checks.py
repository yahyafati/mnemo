#!/usr/bin/env python3
"""Helpers for the sync QA pass (docs/sync/qa.md). Each subcommand does the part of a check that a
machine can do; the rest (two devices, a stopwatch, a person looking at the screen) is in the runbook.

    sync-checks.py folder <folder>                    what a sync folder holds: files by kind, sizes, every file's
                                                      envelope checked (length, checksum), the devices' seq ranges
    sync-checks.py compare <data dir A> <data dir B>  do two collections hold the same rows? (decks, notes, cards,
                                                      review logs, media), and the first rows that differ
    sync-checks.py watch <folder> [--every 10]        print the folder's size and file count as it changes (data use)

A *folder* is the folder backend's one flat folder (`devices__<id>__changes__3.mnc`, `snapshots__…`, `media__<hash>`,
`sync.json`; ADR 0013). For Google Drive and WebDAV look at the same files in the provider's web page instead.
`compare` reads copies of the databases (the apps keep them in WAL mode), so the apps may be running. It reads the
synced columns of `core/database/.../sync/SyncTriggers.kt` that matter to a person: ids, names, text, schedules and
`deletedAt`; it ignores `updatedAt` (it may differ between devices, ADR 0013 "As built (S3)") and device-local tables.
Standard library only.
"""
import argparse
import hashlib
import json
import re
import shutil
import sqlite3
import struct
import sys
import tempfile
import time
from pathlib import Path

MAGIC = b"MNSY"
HEADER = 11
CHECKSUM = 32
KNOWN_VERSION = 1  # SyncFormat.VERSION: a file with a higher number is "from a newer Mnemo", not damaged

CHANGE = re.compile(r"^devices__(?P<device>[A-Za-z0-9][A-Za-z0-9._-]*)__changes__(?P<seq>\d+)\.mnc$")
DEVICE = re.compile(r"^devices__(?P<device>[A-Za-z0-9][A-Za-z0-9._-]*)__device\.json$")
SNAPSHOT = re.compile(r"^snapshots__(?P<device>[A-Za-z0-9][A-Za-z0-9._-]*)-(?P<clock>\d+)\.mns$")
MEDIA = re.compile(r"^media__(?P<hash>[0-9a-f]{64})$")


def human(n: int) -> str:
    size = float(n)
    for unit in ("B", "KB", "MB", "GB"):
        if size < 1024 or unit == "GB":
            return f"{size:.0f} {unit}" if unit == "B" else f"{size:.1f} {unit}"
        size /= 1024
    return f"{n} B"


def envelope(data: bytes) -> tuple[str, str]:
    """('ok' | 'incomplete' | 'damaged' | 'newer', detail) for one file; mirrors FileCodec.open without decrypting."""
    if len(data) < HEADER:
        return "incomplete", f"{len(data)} bytes, shorter than a header"
    if data[:4] != MAGIC:
        return "damaged", "not a Mnemo sync file"
    version, flags, length = struct.unpack(">HBI", data[4:HEADER])
    if version > KNOWN_VERSION:
        return "newer", f"format version {version}"
    expected = HEADER + length + CHECKSUM
    if len(data) < expected:
        return "incomplete", f"{len(data)} of {expected} bytes (still being written?)"
    if len(data) > expected:
        return "damaged", f"longer than it says ({len(data)} of {expected} bytes)"
    if hashlib.sha256(data[: HEADER + length]).digest() != data[HEADER + length :]:
        return "damaged", "fails its checksum"
    return "ok", ("encrypted" if flags & 1 else "plain") + (", compressed" if flags & 2 else "")


def folder(args) -> None:
    root = Path(args.folder)
    if not root.is_dir():
        sys.exit(f"{root} is not a folder")
    manifest = root / "sync.json"
    if manifest.is_file():
        try:
            info = json.loads(manifest.read_text())
            encryption = info.get("encryption")
            print(f"sync.json       format {info.get('formatVersion')}, collection {info.get('collectionId')}, "
                  f"created {time.strftime('%Y-%m-%d %H:%M', time.localtime(info.get('createdAt', 0) / 1000))}")
            print(f"encryption      {'on (' + encryption.get('kdf', '?') + ', ' + str(encryption.get('iterations')) + ' iterations)' if encryption else 'OFF: anyone who opens this folder can read the cards'}")
        except (ValueError, AttributeError) as e:
            print(f"sync.json       UNREADABLE: {e}")
    else:
        print("sync.json       MISSING (not a sync folder, or the creating device hasn't finished)")

    kinds = {"changes": [0, 0], "snapshots": [0, 0], "media": [0, 0], "devices": [0, 0], "other": [0, 0]}
    seqs: dict[str, list[int]] = {}
    problems: list[str] = []
    envelopes = {"plain": 0, "encrypted": 0}
    for path in sorted(root.iterdir()):
        if not path.is_file():
            continue
        name = path.name
        size = path.stat().st_size
        if name == "sync.json":
            continue
        if m := CHANGE.match(name):
            kind = "changes"
            seqs.setdefault(m["device"], []).append(int(m["seq"]))
        elif SNAPSHOT.match(name):
            kind = "snapshots"
        elif MEDIA.match(name):
            kind = "media"  # media are the raw file bytes under the envelope too, checked below
        elif DEVICE.match(name):
            kind = "devices"
        else:
            kinds["other"][0] += 1
            kinds["other"][1] += size
            continue
        kinds[kind][0] += 1
        kinds[kind][1] += size
        state, detail = envelope(path.read_bytes())
        if state == "ok":
            envelopes["encrypted" if detail.startswith("encrypted") else "plain"] += 1
        else:
            problems.append(f"{state:<10} {name}: {detail}")

    for label, (count, size) in kinds.items():
        if count or label != "other":
            print(f"{label:<15} {count:>6} files  {human(size):>10}")
    total_files = sum(c for c, _ in kinds.values())
    total_size = sum(s for _, s in kinds.values())
    print(f"{'total':<15} {total_files:>6} files  {human(total_size):>10}")
    if kinds["other"][0]:
        print("                (other = files that are not Mnemo's, such as .DS_Store or a sync tool's conflict copies: Mnemo ignores them)")
    if envelopes["plain"] and envelopes["encrypted"]:
        print("MIXED           some files are encrypted and some are not")
    for device, numbers in sorted(seqs.items()):
        numbers.sort()
        gaps = sorted(set(range(numbers[0], numbers[-1] + 1)) - set(numbers))
        cleaned = "" if numbers[0] <= 1 else f" (numbers below {numbers[0]} were cleaned up)"
        print(f"device {device[:8]}…  change files {numbers[0]}–{numbers[-1]}, {len(numbers)} present{cleaned}"
              + (f", MISSING {gaps[:10]}{'…' if len(gaps) > 10 else ''}" if gaps else ""))
    for line in problems:
        print(line)
    if problems:
        print(f"\n{len(problems)} file(s) failed the envelope check. 'incomplete' on a folder a sync tool is still copying is normal: "
              "run again when it is idle. 'damaged' is not.")
        sys.exit(1)
    print("\nevery file's length and checksum is intact")


COLUMNS = {
    "decks": "id, name, parentId, description, category, starred, examDate, deletedAt",
    "notes": "id, deckId, noteTypeId, fields, tags, hint, deletedAt",
    "cards": "id, noteId, deckId, ordinal, state, due, stability, difficulty, step, lastReview, reps, lapses, flagged, starred, suspended, buriedUntil, deletedAt",
    "review_logs": "id, cardId, rating, reviewedAt, stateBefore, deletedAt",
    "media": "id, deletedAt",
    "note_types": "id, name, deletedAt",
}


def open_copy(data_dir: Path, scratch: Path, label: str) -> sqlite3.Connection:
    database = data_dir / "mnemo.db"
    if not database.is_file():
        sys.exit(f"no collection at {database}")
    for suffix in ("", "-wal", "-shm"):
        source = data_dir / f"mnemo.db{suffix}"
        if source.is_file():
            shutil.copy2(source, scratch / f"{label}.db{suffix}")
    return sqlite3.connect(scratch / f"{label}.db")


def table_columns(connection: sqlite3.Connection, table: str, wanted: str) -> list[str]:
    present = {row[1] for row in connection.execute(f"PRAGMA table_info({table})")}
    return [c.strip() for c in wanted.split(",") if c.strip() in present]


def compare(args) -> None:
    scratch = Path(tempfile.mkdtemp(prefix="mnemo-compare-"))
    try:
        a = open_copy(Path(args.a), scratch, "a")
        b = open_copy(Path(args.b), scratch, "b")
        different = 0
        for table, wanted in COLUMNS.items():
            columns = [c for c in table_columns(a, table, wanted) if c in table_columns(b, table, wanted)]
            order = columns[0]
            rows = [{r[0]: r for r in conn.execute(f"SELECT {', '.join(columns)} FROM {table} ORDER BY {order}")} for conn in (a, b)]
            live = [sum(1 for r in side.values() if r[columns.index('deletedAt')] is None) for side in rows]
            only_a = sorted(set(rows[0]) - set(rows[1]))
            only_b = sorted(set(rows[1]) - set(rows[0]))
            changed = [i for i in sorted(set(rows[0]) & set(rows[1])) if rows[0][i] != rows[1][i]]
            status = "same" if not (only_a or only_b or changed) else "DIFFERENT"
            different += status != "same"
            print(f"{table:<12} A {len(rows[0]):>6} rows ({live[0]} live)   B {len(rows[1]):>6} rows ({live[1]} live)   {status}")
            for label, ids in (("only in A", only_a), ("only in B", only_b)):
                for i in ids[:3]:
                    print(f"    {label}: {i}")
            for i in changed[:3]:
                differing = [c for c, x, y in zip(columns, rows[0][i], rows[1][i]) if x != y]
                print(f"    differs: {i} in {', '.join(differing)}")
            extra = len(only_a) + len(only_b) + len(changed) - 9
            if extra > 0:
                print(f"    … and more")
        a.close()
        b.close()
    finally:
        shutil.rmtree(scratch, ignore_errors=True)
    if different:
        print("\nThe collections differ. Sync both devices again and wait for the folder tool to finish before reading this as a failure.")
        sys.exit(1)
    print("\nthe two collections hold the same rows")


def watch(args) -> None:
    root = Path(args.folder)
    if not root.is_dir():
        sys.exit(f"{root} is not a folder")
    start = time.time()
    last = None
    print("seconds   files   size        change")
    try:
        while True:
            files = [p for p in root.iterdir() if p.is_file()]
            size = sum(p.stat().st_size for p in files)
            now = (len(files), size)
            if now != last:
                delta = "" if last is None else f"{'+' if size >= last[1] else '-'}{human(abs(size - last[1]))}"
                print(f"{time.time() - start:7.0f}   {len(files):5}   {human(size):>10}  {delta}")
                last = now
            time.sleep(args.every)
    except KeyboardInterrupt:
        pass


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    f = commands.add_parser("folder")
    f.add_argument("folder")
    f.set_defaults(run=folder)
    c = commands.add_parser("compare")
    c.add_argument("a", help="a data folder (the one holding mnemo.db)")
    c.add_argument("b")
    c.set_defaults(run=compare)
    w = commands.add_parser("watch")
    w.add_argument("folder")
    w.add_argument("--every", type=float, default=10)
    w.set_defaults(run=watch)
    args = parser.parse_args()
    args.run(args)


if __name__ == "__main__":
    main()
