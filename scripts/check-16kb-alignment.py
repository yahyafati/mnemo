#!/usr/bin/env python3
"""Checks that an .aab or .apk is ready for 16 KB memory pages (Play requires it for apps that
target Android 15+ and ship native libraries).

For every 64-bit native library (arm64-v8a, x86_64) it checks that
  * each ELF LOAD segment is aligned to at least 16384 bytes, and
  * in an APK, an uncompressed library starts at a 16384-byte offset in the zip.
32-bit ABIs are not affected by the requirement and are skipped.

    python3 scripts/check-16kb-alignment.py app/build/outputs/bundle/release/app-release.aab

Exit status 1 if any library fails. `zipalign -c -P 16 -v 4 <apk>` (Android SDK build-tools 35+)
checks the zip side independently.
"""
import struct
import sys
import zipfile

PAGE = 16384
ABIS_64 = ("arm64-v8a", "x86_64")
PT_LOAD = 1


def load_alignments(data: bytes) -> list[int]:
    if data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    if data[4] != 2 or data[5] != 1:
        raise ValueError("expected a 64-bit little-endian ELF file")
    phoff = struct.unpack_from("<Q", data, 0x20)[0]
    phentsize, phnum = struct.unpack_from("<HH", data, 0x36)
    alignments = []
    for i in range(phnum):
        p_type, _flags, _off, _va, _pa, _fsz, _msz, p_align = struct.unpack_from("<IIQQQQQQ", data, phoff + i * phentsize)
        if p_type == PT_LOAD:
            alignments.append(p_align)
    return alignments


def data_offset(archive: zipfile.ZipFile, info: zipfile.ZipInfo) -> int:
    """Where a stored entry's bytes start: past its local header, name and extra field."""
    with open(archive.filename, "rb") as raw:
        raw.seek(info.header_offset)
        header = raw.read(30)
    name_len, extra_len = struct.unpack_from("<HH", header, 26)
    return info.header_offset + 30 + name_len + extra_len


def main(path: str) -> int:
    is_apk = path.endswith(".apk")
    failures = 0
    checked = 0
    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            name = info.filename
            if not name.endswith(".so") or not any(f"/{abi}/" in name or name.startswith(f"{abi}/") for abi in ABIS_64):
                continue
            checked += 1
            problems = []
            try:
                small = [a for a in load_alignments(archive.read(name)) if a < PAGE]
            except ValueError as error:
                problems.append(str(error))
            else:
                if small:
                    problems.append(f"LOAD segment aligned to {min(small)} bytes")
            if is_apk and info.compress_type == zipfile.ZIP_STORED and data_offset(archive, info) % PAGE:
                problems.append("stored at an offset that is not a multiple of 16384")
            print(f"{'FAIL' if problems else 'ok  '}  {name}" + (f"  ({'; '.join(problems)})" if problems else ""))
            failures += bool(problems)
    if checked == 0:
        print("No 64-bit native libraries found.")
    print(f"{checked} libraries checked, {failures} failed.")
    return 1 if failures else 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    sys.exit(main(sys.argv[1]))
