"""
Builds the Anki package fixtures in ../src/test/resources/ with the real Anki backend, so the
reader is tested against files Anki itself writes.

    python3 -m venv /tmp/ankienv && /tmp/ankienv/bin/pip install anki
    /tmp/ankienv/bin/python core/anki/fixtures/make_fixtures.py

The collection is small but covers what the importer has to handle: nested decks, the stock
note types plus a custom three-field type with two templates, HTML formatting, an image, a sound,
MathJax, FSRS memory state in card data, SM-2 cards with and without review history, and
suspended and flagged cards. Review history is written relative to the time the script runs, so
tests check relationships between dates rather than absolute dates.
"""

import os
import shutil
import tempfile
import time

from anki.collection import Collection, ExportAnkiPackageOptions, DeckIdLimit

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "test", "resources")

def png(width=8, height=8, rgb=(220, 40, 40)):
    """A valid solid-color PNG, built here so the bytes are known to decode."""
    import struct
    import zlib

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    rows = b"".join(b"\x00" + bytes(rgb) * width for _ in range(height))
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b"")


# The sound is a few stand-in bytes: tests never decode it.
PNG = png()
MP3 = b"ID3\x03\x00\x00\x00\x00\x00\x00mnemo-test-sound"

DAY = 86_400
NOW = int(time.time())


def add_vocab_type(col):
    mm = col.models
    m = mm.new("Vocab")
    for name in ("Word", "Reading", "Meaning"):
        mm.add_field(m, mm.new_field(name))
    t = mm.new_template("Recognition")
    t["qfmt"] = "{{Word}}"
    t["afmt"] = "{{FrontSide}}<hr id=answer>{{Reading}}<br>{{Meaning}}"
    mm.add_template(m, t)
    t = mm.new_template("Production")
    t["qfmt"] = "{{Meaning}}"
    t["afmt"] = "{{FrontSide}}<hr id=answer>{{Word}}"
    mm.add_template(m, t)
    mm.add(m)
    return mm.by_name("Vocab")


def note(col, model_name, deck_id, fields, tags=()):
    n = col.new_note(col.models.by_name(model_name))
    for i, value in enumerate(fields):
        n.fields[i] = value
    n.tags = list(tags)
    col.add_note(n, deck_id)
    return n


def build(path):
    col = Collection(path)
    col.set_config("fsrs", True)
    bio = col.decks.id("Biology")
    jp = col.decks.id("Languages::Japanese")
    add_vocab_type(col)

    col.media.write_data("cell.png", PNG)
    col.media.write_data("konnichiwa.mp3", MP3)

    basic = note(
        col, "Basic", bio,
        ["What do <b>mitochondria</b> make?", "<i>ATP</i>, via oxidative phosphorylation<br>2 &lt; 3 &amp;&nbsp;done"],
        ["bio::cells", "marked"],
    )
    note(col, "Basic", bio, ["<div>A cell:</div><div><img src=\"cell.png\"></div>", "Eukaryotic"], ["bio::cells"])
    note(col, "Basic", bio, ["Mass-energy equivalence", "\\(E = mc^2\\)"])
    note(
        col, "Basic", bio,
        ["Steps of mitosis", "<ol><li>Prophase</li><li>Metaphase</li></ol><ul><li><code>x</code> stays</li></ul>"],
    )
    reversed_note = note(col, "Basic (and reversed card)", jp, ["猫", "cat"], ["jp"])
    cloze = note(
        col, "Cloze", bio,
        ["{{c1::Mitochondria}} make {{c2::ATP::energy molecule}}.", "Extra <b>context</b>"],
    )
    note(col, "Vocab", jp, ["こんにちは", "konnichiwa [sound:konnichiwa.mp3]", "hello"], ["jp"])

    # Card states.
    basic_card = basic.cards()[0]
    fwd, back = reversed_note.cards()
    c1, c2 = sorted(cloze.cards(), key=lambda c: c.ord)

    # An SM-2 review card with history and no FSRS memory state: the importer replays the log.
    history = [(NOW - 20 * DAY, 3, 0, 1), (NOW - 19 * DAY, 3, 3, 1), (NOW - 15 * DAY, 3, 8, 1)]
    for i, (at, ease, ivl, rtype) in enumerate(history):
        col.db.execute(
            "insert into revlog (id, cid, usn, ease, ivl, lastIvl, factor, time, type) values (?,?,?,?,?,?,?,?,?)",
            at * 1000, basic_card.id, -1, ease, ivl, history[i - 1][2] if i else 0, 2500, 6000, 0 if i == 0 else 1,
        )
    today = (NOW - col.crt) // DAY
    col.db.execute(
        "update cards set type=2, queue=2, ivl=8, factor=2500, reps=3, lapses=0, due=?, data='' where id=?",
        today - 7 + 8, basic_card.id,
    )

    # An SM-2 review card with no history at all (a shared deck with scheduling but no log).
    col.db.execute(
        "update cards set type=2, queue=2, ivl=30, factor=2300, reps=5, lapses=1, due=?, data='' where id=?",
        today + 4, fwd.id,
    )

    # An FSRS card: memory state lives in card data.
    col.db.execute(
        "insert into revlog (id, cid, usn, ease, ivl, lastIvl, factor, time, type) values (?,?,?,?,?,?,?,?,?)",
        (NOW - 3 * DAY) * 1000, c1.id, -1, 4, 5, 0, 0, 4000, 0,
    )
    col.db.execute(
        "update cards set type=2, queue=2, ivl=5, factor=0, reps=1, lapses=0, due=?, "
        "data='{\"s\":5.1,\"d\":3.2,\"dr\":0.9,\"lrt\":" + str(NOW - 3 * DAY) + "}' where id=?",
        today + 2, c1.id,
    )

    # A learning card: due is a timestamp.
    col.db.execute(
        "update cards set type=1, queue=1, ivl=0, reps=1, left=1001, due=? where id=?",
        NOW + 600, c2.id,
    )

    col.sched.suspend_cards([back.id])
    col.set_user_flag_for_cards(1, [fwd.id])
    return col


def export_apkg(col, name, legacy, with_scheduling=True, deck=None):
    options = ExportAnkiPackageOptions(
        with_scheduling=with_scheduling, with_deck_configs=with_scheduling, with_media=True, legacy=legacy,
    )
    limit = DeckIdLimit(deck) if deck else None
    col.export_anki_package(out_path=os.path.join(OUT, name), options=options, limit=limit)


def main():
    os.makedirs(OUT, exist_ok=True)
    work = tempfile.mkdtemp()
    try:
        col = build(os.path.join(work, "collection.anki2"))
        export_apkg(col, "modern.apkg", legacy=False)
        export_apkg(col, "legacy.apkg", legacy=True)
        export_apkg(col, "no-scheduling.apkg", legacy=False, with_scheduling=False)
        col.export_collection_package(os.path.join(OUT, "collection.colpkg"), include_media=True, legacy=False)
        col.close()
    finally:
        shutil.rmtree(work)


if __name__ == "__main__":
    main()
