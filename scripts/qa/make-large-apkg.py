#!/usr/bin/env python3
"""Builds a large Anki package for the desktop import check (docs/desktop/qa.md §2.2).

    python3 -m venv /tmp/ankienv && /tmp/ankienv/bin/pip install anki
    /tmp/ankienv/bin/python scripts/qa/make-large-apkg.py large.apkg [--cards 12000]

A real collection of your own is the better test. This is the fallback when you have none that
big: the file is written by Anki itself, with nested decks, Basic, Basic (and reversed card), Cloze
and Type-in notes, math, images, sounds, tags, and review history on every third card, so the
importer has the same things to do as with a real one. Card text is generated; nothing in it is real
study material. Prints the counts the import must reproduce (compare with `desktop-checks.py counts`).
"""
import argparse
import os
import random
import shutil
import struct
import sys
import tempfile
import time
import zlib

from anki.collection import Collection, ExportAnkiPackageOptions

DAY = 86_400
TOPICS = ["Anatomy", "Chemistry::Organic", "Chemistry::Inorganic", "History::Ancient", "History::Modern", "Languages::Spanish", "Languages::Japanese", "Physics::Mechanics", "Physics::Optics", "Maths::Calculus"]
MEDIA_FILES = 40  # distinct images; notes share them, like a real collection that reuses a figure


def png(width, rgb):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    rows = b"".join(b"\x00" + bytes(rgb) * width for _ in range(width))
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, width, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b"")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("out")
    parser.add_argument("--cards", type=int, default=12_000, help="about how many cards (default 12,000)")
    args = parser.parse_args()
    random.seed(11)
    now = int(time.time())
    work = tempfile.mkdtemp()
    try:
        col = Collection(os.path.join(work, "collection.anki2"))
        col.set_config("fsrs", True)
        for i in range(MEDIA_FILES):
            col.media.write_data(f"fig{i}.png", png(24 + i, (random.randrange(256), random.randrange(256), random.randrange(256))))
        col.media.write_data("clip.mp3", b"ID3\x03\x00\x00\x00\x00\x00\x00mnemo-qa-sound")
        decks = {name: col.decks.id(name) for name in TOPICS}
        models = {name: col.models.by_name(name) for name in ("Basic", "Basic (and reversed card)", "Cloze")}
        typein = col.models.by_name("Basic (type in the answer)")
        cards = 0
        notes = 0
        used = set()
        n = 0
        while cards < args.cards:
            n += 1
            topic = TOPICS[n % len(TOPICS)]
            kind = n % 10
            if kind < 4:
                note = col.new_note(models["Basic"])
                note.fields[0] = f"{topic} question {n}: what does \\(x^{{{n % 9 + 2}}}\\) differentiate to?"
                note.fields[1] = f"\\({n % 9 + 2}x^{{{n % 9 + 1}}}\\)<br><img src=\"fig{n % MEDIA_FILES}.png\">"
                used.add(n % MEDIA_FILES)
                cards += 1
            elif kind < 7:
                note = col.new_note(models["Basic (and reversed card)"])
                note.fields[0] = f"{topic} term {n}"
                note.fields[1] = f"definition of term {n} <b>in bold</b> [sound:clip.mp3]"
                cards += 2
            elif kind < 9:
                note = col.new_note(models["Cloze"])
                note.fields[0] = f"In {topic.lower()}, {{{{c1::fact {n}}}}} relates to {{{{c2::fact {n + 1}}}}}."
                cards += 2
            else:
                note = col.new_note(typein)
                note.fields[0] = f"{topic} spelling {n}"
                note.fields[1] = f"answer{n}"
                cards += 1
            note.tags = [topic.split("::")[0].lower(), f"batch{n // 500}"]
            col.add_note(note, decks[topic])
            notes += 1
        reviews = 0
        for index, card_id in enumerate(col.find_cards("")):
            if index % 3:
                continue
            card = col.get_card(card_id)
            count = random.randint(1, 4)
            for k in range(count):
                day = now - (60 - k * 12 - random.randint(0, 6)) * DAY
                col.db.execute(
                    "insert into revlog values (?, ?, -1, ?, ?, ?, ?, ?, 1)",
                    day * 1000 + reviews, card_id, random.choice((2, 3, 3, 3, 4)), random.randint(1, 30), 1, 0, random.randint(2000, 9000),
                )
                reviews += 1
            col.db.execute("update cards set type = 2, queue = 2, due = ?, ivl = ?, reps = ? where id = ?", (col.sched.today + random.randint(-5, 40)), random.randint(1, 120), count, card_id)
        options = ExportAnkiPackageOptions(with_scheduling=True, with_deck_configs=True, with_media=True, legacy=False)
        col.export_anki_package(out_path=os.path.abspath(args.out), options=options, limit=None)
        print(f"wrote {args.out} ({os.path.getsize(args.out) / 1e6:.1f} MB)")
        print(f"decks {len(TOPICS) + 5} (the {len(TOPICS)} topics and their 5 parents), notes {notes}, cards {cards}, review rows {reviews}, media files {len(used) + 1} (Anki exports only the files a note uses)")
        col.close()
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
