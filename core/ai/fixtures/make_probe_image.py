#!/usr/bin/env python3
"""Writes core/ai/src/main/resources/probe/number.png: the picture "Check images" sends (ADR 0014, P4).

96 x 64, black digits on white, drawn from a 5 x 7 pixel font so the file is the same on every machine and
needs no libraries. The number must match ConnectionProbe.IMAGE_PROBE_NUMBER.

    python3 core/ai/fixtures/make_probe_image.py
"""
import struct
import zlib
from pathlib import Path

NUMBER = "52"
WIDTH, HEIGHT, SCALE, GAP = 96, 64, 8, 8

GLYPHS = {
    "0": ["01110", "10001", "10011", "10101", "11001", "10001", "01110"],
    "1": ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
    "2": ["01110", "10001", "00001", "00010", "00100", "01000", "11111"],
    "3": ["11110", "00001", "00001", "01110", "00001", "00001", "11110"],
    "4": ["00010", "00110", "01010", "10010", "11111", "00010", "00010"],
    "5": ["11111", "10000", "11110", "00001", "00001", "10001", "01110"],
    "6": ["00110", "01000", "10000", "11110", "10001", "10001", "01110"],
    "7": ["11111", "00001", "00010", "00100", "01000", "01000", "01000"],
    "8": ["01110", "10001", "10001", "01110", "10001", "10001", "01110"],
    "9": ["01110", "10001", "10001", "01111", "00001", "00010", "01100"],
}


def main() -> None:
    glyph_w, glyph_h = 5 * SCALE, 7 * SCALE
    total_w = len(NUMBER) * glyph_w + (len(NUMBER) - 1) * GAP
    left, top = (WIDTH - total_w) // 2, (HEIGHT - glyph_h) // 2
    rows = [bytearray([255] * WIDTH) for _ in range(HEIGHT)]
    for index, digit in enumerate(NUMBER):
        x0 = left + index * (glyph_w + GAP)
        for gy, line in enumerate(GLYPHS[digit]):
            for gx, bit in enumerate(line):
                if bit == "1":
                    for y in range(top + gy * SCALE, top + (gy + 1) * SCALE):
                        for x in range(x0 + gx * SCALE, x0 + (gx + 1) * SCALE):
                            rows[y][x] = 0
    raw = b"".join(b"\x00" + bytes(row) for row in rows)  # filter type 0 per scanline, 8-bit gray

    def chunk(kind: bytes, data: bytes) -> bytes:
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 0, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    out = Path(__file__).resolve().parents[1] / "src/main/resources/probe/number.png"
    out.write_bytes(png)
    print(f"wrote {out} ({len(png)} bytes, number {NUMBER})")


if __name__ == "__main__":
    main()
