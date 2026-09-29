#!/usr/bin/env python3
"""Builds the public site (privacy policy + landing page) from docs/release into a directory.

    python3 scripts/pages/build.py <output-dir>

Needs `pip install markdown`. Fails while the privacy policy still has its contact placeholder,
because Play rejects a policy without a way to reach the developer (release ROADMAP R4).
"""
import html
import pathlib
import re
import sys

import markdown

ROOT = pathlib.Path(__file__).resolve().parents[2]
POLICY = ROOT / "docs/release/privacy-policy.md"
ICON = ROOT / "docs/release/assets/play-icon-512.png"
REPO = "https://github.com/yahyafati/mnemo"

PAGE = """<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title}</title><link rel="icon" href="{root}icon.png">
<style>
:root{{color-scheme:light dark;--bg:#faf8f3;--fg:#1c1a17;--muted:#6b665d;--line:#e3ded2;--accent:#4f46e5}}
@media (prefers-color-scheme:dark){{:root{{--bg:#1c1a17;--fg:#f4f1ea;--muted:#a8a294;--line:#3a362f;--accent:#a5a0ff}}}}
body{{margin:0;background:var(--bg);color:var(--fg);font:17px/1.6 system-ui,sans-serif}}
main{{max-width:44rem;margin:0 auto;padding:2rem 1rem 4rem}}
a{{color:var(--accent)}} h1,h2{{line-height:1.25}} h2{{margin-top:2rem}} em{{color:var(--muted)}}
table{{border-collapse:collapse;width:100%;font-size:.95rem}} th,td{{border:1px solid var(--line);padding:.4rem .6rem;text-align:left;vertical-align:top}}
code{{background:var(--line);padding:0 .25em;border-radius:3px}} nav{{margin-bottom:1.5rem;color:var(--muted)}}
img.icon{{width:96px;height:96px;border-radius:22px}}
</style></head><body><main>{nav}{body}</main></body></html>
"""


def main(out: pathlib.Path) -> None:
    text = POLICY.read_text()
    if re.search(r"\[contact address to be added", text, re.I):
        sys.exit("privacy-policy.md still has the contact placeholder: add the address (R4) first.")
    if not re.search(r"[\w.+-]+@[\w-]+\.[\w.-]+", text):
        sys.exit("privacy-policy.md has no contact email address.")

    md = markdown.Markdown(extensions=["tables"])
    (out / "privacy").mkdir(parents=True, exist_ok=True)
    (out / "privacy/index.html").write_text(PAGE.format(
        title="Mnemo privacy policy", root="../",
        nav='<nav><a href="../">Mnemo</a> · <a href="%s">Source</a></nav>' % REPO,
        body=md.convert(text)))
    (out / "index.html").write_text(PAGE.format(
        title="Mnemo", root="",
        nav="",
        body=(
            '<img class="icon" src="icon.png" alt="">\n<h1>Mnemo</h1>\n'
            "<p>Spaced-repetition flashcards for Android. Offline, private, with optional AI. "
            "Free software under GPL-3.0-or-later.</p>\n"
            '<ul><li><a href="privacy/">Privacy policy</a></li>'
            '<li><a href="%s">Source code</a></li>'
            '<li><a href="%s/issues">Report a problem</a></li></ul>' % (html.escape(REPO), html.escape(REPO))
        )))
    (out / "icon.png").write_bytes(ICON.read_bytes())
    (out / ".nojekyll").write_text("")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(pathlib.Path(sys.argv[1]))
