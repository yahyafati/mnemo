"""Generates the desktop app's icons from logo.svg: the window icon (a PNG on the classpath) and the
installers' icons (PNG for Linux, ICO for Windows, ICNS for macOS), and the icons of Anki package files.

Needs `rsvg-convert` (librsvg) on the PATH (`brew install librsvg`), nothing else. Run from anywhere; it
writes into `desktop/`. The Android icons are `generate_icon_drawables.py`.
"""
import os
import shutil
import struct
import subprocess

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
LOGO = f"{REPO}/docs/release/assets/logo.svg"
WINDOW_ICON = "desktop/src/main/resources/icons/mnemo.png"
INSTALLER_ICONS = "desktop/icons"

# Windows and Linux show the logo tile as it is. macOS icons sit in a transparent canvas with a margin
# (Apple's template puts the artwork at 824 of 1024), or they look too big in the Dock.
MAC_MARGIN = 0.1


def render(size, margin_fraction=0.0):
    """logo.svg as PNG bytes, `size` px square, with a transparent margin of `margin_fraction` on each side."""
    if shutil.which("rsvg-convert") is None:
        raise SystemExit("rsvg-convert (librsvg) is needed for the desktop icons: brew install librsvg")
    art = round(size * (1 - 2 * margin_fraction))
    offset = (size - art) // 2
    return subprocess.run(
        ["rsvg-convert", "-w", str(art), "-h", str(art), "--page-width", str(size), "--page-height", str(size),
         "--left", str(offset), "--top", str(offset), LOGO],
        check=True, capture_output=True).stdout


def ico(images):
    """An ICO holding PNG images (Windows Vista and later read them); a width of 256 px is stored as 0."""
    header = struct.pack("<HHH", 0, 1, len(images))
    entries, data, offset = b"", b"", 6 + 16 * len(images)
    for size, png in images:
        entries += struct.pack("<BBBBHHII", size % 256, size % 256, 0, 0, 1, 32, len(png), offset)
        data += png
        offset += len(png)
    return header + entries + data


def icns(chunks):
    """An ICNS of (type, PNG) chunks."""
    body = b"".join(code.encode() + struct.pack(">I", 8 + len(png)) + png for code, png in chunks)
    return b"icns" + struct.pack(">I", 8 + len(body)) + body


def write(path, data):
    os.makedirs(os.path.dirname(f"{REPO}/{path}"), exist_ok=True)
    with open(f"{REPO}/{path}", "wb") as f:
        f.write(data)


window = render(512)
write(WINDOW_ICON, window)
write(f"{INSTALLER_ICONS}/mnemo.png", window)
write(f"{INSTALLER_ICONS}/mnemo.ico", ico([(size, render(size)) for size in (16, 24, 32, 48, 64, 128, 256)]))
# Type codes: icp4, icp5 and icp6 are 16, 32 and 64 px; ic07, ic08 and ic09 are 128, 256 and 512 px; ic10 is 1024 px.
write(f"{INSTALLER_ICONS}/mnemo.icns", icns([
    (code, render(size, MAC_MARGIN))
    for code, size in (("icp4", 16), ("icp5", 32), ("icp6", 64), ("ic07", 128), ("ic08", 256), ("ic09", 512), ("ic10", 1024))
]))
# The icons of `.apkg` and `.colpkg` files. The installers copy every icon into the app by its file name, so
# these can't share the app's; for now the same picture, until there is a document icon of its own.
for extension in ("png", "ico", "icns"):
    shutil.copyfile(f"{REPO}/{INSTALLER_ICONS}/mnemo.{extension}", f"{REPO}/{INSTALLER_ICONS}/mnemo-package.{extension}")
print("wrote", WINDOW_ICON, "and", INSTALLER_ICONS)
