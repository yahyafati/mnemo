#!/usr/bin/env python3
"""Builds the public site (download page + privacy policy) from docs/release into a directory.

    python3 scripts/pages/build.py <output-dir>

Needs `pip install markdown`. Fails while the privacy policy still has its contact placeholder,
because Play rejects a policy without a way to reach the developer (release ROADMAP R4), and when
the download table names a file the release does not produce (sideload roadmap S4).

The page is `index.html` + `download.js` in this folder; the table of files is made here from the
stable names that `scripts/release/prepare-release.py` gives the release files, so the links
`releases/latest/download/<name>` cannot drift from what a release holds.
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
SHOTS = {  # name on the site -> file in docs/release/assets/screenshots
    "home": "phone/01-home-light.png",
    "study": "phone/03-study-cloze-light.png",
    "analytics": "phone/08-analytics-light.png",
}
REPO = "https://github.com/yahyafati/mnemo"
RELEASES = REPO + "/releases"
LATEST = RELEASES + "/latest/download/"
API = "https://api.github.com/repos/yahyafati/mnemo/releases/latest"
EMAIL_PATTERN = r"[\w.+-]+@[\w-]+\.[\w.-]+"

# (key the script picks by, what the button says, stable name, what the row says, a line under the big button)
DOWNLOADS = [
    ("android", "Android", "Mnemo-android.apk", "Android 10 or newer, phones and tablets", ""),
    ("windows", "Windows", "Mnemo-windows-x64.msi", "Windows 10 or 11, 64-bit; installs for your user only",
     "Windows will warn you the first time you open it: choose More info, then Run anyway."),
    ("macos", "Mac (Apple Silicon)", "Mnemo-macos-arm64.dmg", "macOS 12 or newer, M1 and later",
     "This build is for Macs with Apple silicon (M1 or later). macOS will warn you the first time you open it."),
    ("macos-intel", "Mac (Intel)", "Mnemo-macos-x64.dmg", "macOS 12 or newer, Macs with an Intel processor", ""),
    ("deb", "Linux (.deb)", "mnemo-amd64.deb", "Debian, Ubuntu, Mint and relatives, x86-64", ""),
    ("rpm", "Linux (.rpm)", "mnemo-x86_64.rpm", "Fedora, openSUSE and relatives, x86-64", ""),
    ("linux", "Linux (portable)", "Mnemo-linux-x64.tar.gz", "Any other Linux, x86-64: unpack it anywhere", ""),
]

PAGE = """<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title}</title><meta name="description" content="{description}"><link rel="icon" href="{root}icon.png">
<style>
:root{{color-scheme:light dark;--bg:#faf8f3;--fg:#1c1a17;--muted:#6b665d;--line:#e3ded2;--accent:#4f46e5;--on-accent:#fff}}
@media (prefers-color-scheme:dark){{:root{{--bg:#1c1a17;--fg:#f4f1ea;--muted:#a8a294;--line:#3a362f;--accent:#a5a0ff;--on-accent:#1c1a17}}}}
body{{margin:0;background:var(--bg);color:var(--fg);font:17px/1.6 system-ui,sans-serif}}
main{{max-width:44rem;margin:0 auto;padding:2rem 1rem 4rem}}
a{{color:var(--accent)}} h1,h2,h3{{line-height:1.25}} h2{{margin-top:2.5rem}} h3{{margin-top:1.5rem}} em{{color:var(--muted)}}
table{{border-collapse:collapse;width:100%;font-size:.95rem}} th,td{{border:1px solid var(--line);padding:.4rem .6rem;text-align:left;vertical-align:top}}
code{{background:var(--line);padding:0 .25em;border-radius:3px;overflow-wrap:anywhere}} nav{{margin-bottom:1.5rem;color:var(--muted)}}
pre{{background:var(--line);padding:.75rem 1rem;border-radius:6px;overflow-x:auto;font-size:.9rem}} pre code{{padding:0;background:none;overflow-wrap:normal}}
img.icon{{width:96px;height:96px;border-radius:22px;flex:none}}
.hero{{display:flex;gap:1.25rem;align-items:center}} .hero h1{{margin:0}} .tagline{{margin:.25rem 0 0;font-size:1.1rem;color:var(--muted)}}
.button{{display:inline-block;background:var(--accent);color:var(--on-accent);font-weight:600;font-size:1.15rem;text-decoration:none;padding:.8rem 1.4rem;border-radius:10px}}
.button:hover,.button:focus-visible{{outline:3px solid var(--fg);outline-offset:2px}}
.small,.status{{font-size:.9rem;color:var(--muted)}} #pick{{margin:1rem 0}} #pick .small{{margin:.5rem 0 0}}
.notice{{border:1px solid var(--line);border-left:4px solid var(--accent);padding:.6rem .9rem;border-radius:4px}}
td.get{{white-space:nowrap}}
.shots{{display:flex;gap:.75rem;margin-top:2.5rem;overflow-x:auto}} .shots img{{height:26rem;width:auto;border:1px solid var(--line);border-radius:12px}}
@media (max-width:30rem){{.hero{{flex-direction:column;align-items:flex-start}} td.get{{white-space:normal}}}}
</style></head><body><main>{nav}{body}</main>{script}</body></html>
"""


def stable_names() -> set[str]:
    """The names `prepare-release.py` gives the release files (its file name has a dash: load it by path)."""
    spec = importlib.util.spec_from_file_location("prepare_release", ROOT / "scripts/release/prepare-release.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return {stable for _, _, stable in module.FILES}


def downloads_table() -> str:
    names = stable_names()
    listed = {name for _, _, name, _, _ in DOWNLOADS}
    if listed != names:
        sys.exit("The download table and prepare-release.py disagree: only on the page: %s; only in the release: %s"
                 % (sorted(listed - names), sorted(names - listed)))
    rows = []
    for key, label, name, text, note in DOWNLOADS:
        rows.append(
            '<tr data-os="%s" data-label="%s" data-note="%s"><td>%s</td><td class="get"><a href="%s%s">%s</a></td></tr>'
            % (key, html.escape(label), html.escape(note), html.escape(text), LATEST, name, name))
    return "<table><thead><tr><th>System</th><th>Download</th></tr></thead><tbody>\n%s\n</tbody></table>" % "\n".join(rows)


def main(out: pathlib.Path) -> None:
    text = POLICY.read_text()
    if re.search(r"\[contact address to be added", text, re.I):
        sys.exit("privacy-policy.md still has the contact placeholder: add the address (R4) first.")
    email = re.search(EMAIL_PATTERN, text)
    if not email:
        sys.exit("privacy-policy.md has no contact email address.")

    page = (HERE / "index.html").read_text()
    for key, value in {"repo": REPO, "releases": RELEASES, "api": API, "email": email.group(0),
                       "downloads": downloads_table()}.items():
        page = page.replace("{{" + key + "}}", value)
    if "{{" in page:
        sys.exit("index.html has a placeholder build.py does not fill.")

    md = markdown.Markdown(extensions=["tables"])
    (out / "privacy").mkdir(parents=True, exist_ok=True)
    (out / "privacy/index.html").write_text(PAGE.format(
        title="Mnemo privacy policy", description="What Mnemo does with your data: nothing leaves your device unless you send it.",
        root="../", script="",
        nav='<nav><a href="../">Mnemo</a> · <a href="../#download">Download</a> · <a href="%s">Source</a></nav>' % REPO,
        body=md.convert(text)))
    nav = ('<nav><a href="#download">Download</a> · <a href="privacy/">Privacy</a> · <a href="%s">Source</a>'
           ' · <a href="%s">Releases</a> · <a href="%s/issues">Report a problem</a></nav>' % (REPO, RELEASES, REPO))
    (out / "index.html").write_text(PAGE.format(
        title="Mnemo: flashcards that stay on your device",
        description="Free, open-source spaced-repetition flashcards for Android, Windows, macOS and Linux. No account, optional AI.",
        root="", nav=nav, body=page, script="<script>\n%s</script>" % (HERE / "download.js").read_text()))
    (out / "icon.png").write_bytes(ICON.read_bytes())
    (out / "shots").mkdir(exist_ok=True)
    for name, source in SHOTS.items():
        (out / "shots" / (name + ".png")).write_bytes((ASSETS / "screenshots" / source).read_bytes())
    (out / ".nojekyll").write_text("")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(pathlib.Path(sys.argv[1]))
