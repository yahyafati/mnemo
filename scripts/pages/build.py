#!/usr/bin/env python3
"""Builds the public site (home, download page, privacy policy) from docs/release into a directory.

    python3 scripts/pages/build.py <output-dir>

Needs `pip install markdown`. Fails while the privacy policy still has its contact placeholder,
because Play rejects a policy without a way to reach the developer (release step R4), and when
the download table names a file the release does not produce.

The home page (`home.html`) is for anyone: what Mnemo is and one download button. Everything technical
(every file, install warnings, updates, checksums) is on `download.html`, served at `download/`.
Both share `site.css` (the app's colors and its own fonts) and `download.js`. The table of files is made
here from the stable names that `scripts/release/prepare-release.py` gives the release files, so the
links `releases/latest/download/<name>` cannot drift from what a release holds.
"""
import html
import importlib.util
import pathlib
import re
import sys

import markdown

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = pathlib.Path(__file__).resolve().parent
POLICY = ROOT / "docs/release/privacy-policy.md"
ASSETS = ROOT / "docs/release/assets"
ICON = ASSETS / "play-icon-512.png"
FONTS = ROOT / "core/designsystem/src/commonMain/composeResources/font"
FONT_FILES = ["newsreader.ttf", "hanken_grotesk.ttf"]
SHOTS = {  # name on the site -> file in docs/release/assets/screenshots
    "home": "phone/01-home-light.png",
    "study": "phone/03-study-cloze-light.png",
    "create": "phone/06-smart-extract-light.png",
    "progress": "phone/08-analytics-light.png",
}
REPO = "https://github.com/yahyafati/mnemo"
RELEASES = REPO + "/releases"
LATEST = RELEASES + "/latest/download/"
API = "https://api.github.com/repos/yahyafati/mnemo/releases/latest"
EMAIL_PATTERN = r"[\w.+-]+@[\w-]+\.[\w.-]+"

# (key the script picks by, what the button says, stable name, what the row says,
#  a line under the big button, the install section it links to)
DOWNLOADS = [
    ("android", "Android", "Mnemo-android.apk", "Android 10 or newer, phones and tablets",
     "Android will ask you to allow the install.", "android"),
    ("windows", "Windows", "Mnemo-windows-x64.msi", "Windows 10 or 11, 64-bit; installs for your user only",
     "Windows may show a warning the first time.", "windows"),
    ("macos", "Mac", "Mnemo-macos-arm64.dmg", "macOS 12 or newer, Apple silicon (M1 and later)",
     "macOS may show a warning the first time.", "macos"),
    ("macos-intel", "Intel Mac", "Mnemo-macos-x64.dmg", "macOS 12 or newer, Macs with an Intel processor", "", "macos"),
    ("deb", "Linux (.deb)", "mnemo-amd64.deb", "Debian, Ubuntu, Mint and relatives, x86-64", "", "linux"),
    ("rpm", "Linux (.rpm)", "mnemo-x86_64.rpm", "Fedora, openSUSE and relatives, x86-64", "", "linux"),
    ("linux", "Linux", "Mnemo-linux-x64.tar.gz", "Any other Linux, x86-64: unpack it anywhere", "", "linux"),
]

PAGE = """<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title}</title><meta name="description" content="{description}">
<link rel="icon" href="{root}icon.png"><link rel="stylesheet" href="{root}site.css">
</head><body>
<header class="wrap{width} top"><a class="brand" href="{root}"><img src="{root}icon.png" alt="">Mnemo</a>
<nav><a href="{root}download/">Download</a><a href="{root}download/#help">Help</a></nav></header>
<main class="wrap{width}{doc}">
{body}
</main>
<footer><div class="wrap{width}">
<nav><a href="{root}download/">All downloads</a><a href="{root}download/#install">Installing</a>
<a href="{root}privacy/">Privacy</a><a href="{repo}">Source code</a><a href="{repo}/issues">Report a problem</a></nav>
<p>Mnemo is free and open source (GPL-3.0).</p>
</div></footer>
{script}</body></html>
"""


def stable_names() -> set[str]:
    """The names `prepare-release.py` gives the release files (its file name has a dash: load it by path)."""
    spec = importlib.util.spec_from_file_location("prepare_release", ROOT / "scripts/release/prepare-release.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return {stable for _, _, stable in module.FILES}


def check_names() -> None:
    names = stable_names()
    listed = {row[2] for row in DOWNLOADS}
    if listed != names:
        sys.exit("The download table and prepare-release.py disagree: only on the page: %s; only in the release: %s"
                 % (sorted(listed - names), sorted(names - listed)))


def downloads_table() -> str:
    rows = ['<tr><td>%s</td><td class="file"><a href="%s%s">%s</a></td></tr>' % (html.escape(text), LATEST, name, name)
            for _, _, name, text, _, _ in DOWNLOADS]
    return "<table><thead><tr><th>System</th><th>File</th></tr></thead><tbody>\n%s\n</tbody></table>" % "\n".join(rows)


def file_list() -> str:
    """What download.js picks the visitor's file from (every page that has a download button carries it)."""
    links = ['<a data-os="%s" data-label="%s" data-note="%s" data-section="%s" href="%s%s"></a>'
             % (key, html.escape(label), html.escape(note), section, LATEST, name)
             for key, label, name, _, note, section in DOWNLOADS]
    return '<div id="files" hidden data-api="%s" data-fallback="%s/latest">%s</div>' % (API, RELEASES, "".join(links))


def fill(page: str, values: dict[str, str]) -> str:
    for key, value in values.items():
        page = page.replace("{{" + key + "}}", value)
    if "{{" in page:
        sys.exit("A page has a placeholder build.py does not fill.")
    return page


def write(out: pathlib.Path, path: str, title: str, description: str, body: str, script: bool, narrow: bool) -> None:
    root = "../" * path.count("/")
    target = out / path / "index.html" if path else out / "index.html"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(PAGE.format(
        title=title, description=description, root=root, repo=REPO, body=body,
        width=" narrow" if narrow else "", doc=" doc" if narrow else "",
        script=file_list() + '\n<script src="%sdownload.js"></script>\n' % root if script else ""))


def main(out: pathlib.Path) -> None:
    text = POLICY.read_text()
    if re.search(r"\[contact address to be added", text, re.I):
        sys.exit("privacy-policy.md still has the contact placeholder: add the address (R4) first.")
    if not re.search(EMAIL_PATTERN, text):
        sys.exit("privacy-policy.md has no contact email address.")
    check_names()

    values = {"repo": REPO, "releases": RELEASES, "downloads": downloads_table()}
    write(out, "", "Mnemo: free flashcards that help you remember",
          "Free flashcards for Android, Windows, Mac and Linux. Mnemo shows each card just before you would forget it. "
          "No account, no ads.",
          fill((HERE / "home.html").read_text(), values), script=True, narrow=False)
    write(out, "download/", "Download Mnemo",
          "Every Mnemo download, how to install it, updates and how to check a file.",
          fill((HERE / "download.html").read_text(), values), script=True, narrow=True)
    write(out, "privacy/", "Mnemo privacy policy",
          "What Mnemo does with your data: nothing leaves your device unless you send it.",
          markdown.Markdown(extensions=["tables"]).convert(text), script=False, narrow=True)

    (out / "site.css").write_text((HERE / "site.css").read_text())
    (out / "download.js").write_text((HERE / "download.js").read_text())
    (out / "icon.png").write_bytes(ICON.read_bytes())
    (out / "fonts").mkdir(exist_ok=True)
    for name in FONT_FILES:
        (out / "fonts" / name).write_bytes((FONTS / name).read_bytes())
    (out / "shots").mkdir(exist_ok=True)
    for name, source in SHOTS.items():
        (out / "shots" / (name + ".png")).write_bytes((ASSETS / "screenshots" / source).read_bytes())
    (out / ".nojekyll").write_text("")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(pathlib.Path(sys.argv[1]))
