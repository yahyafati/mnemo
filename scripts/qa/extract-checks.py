#!/usr/bin/env python3
"""Helpers for judging Smart Extract's cards (ADR 0015, C0 and C7; results in docs/release/qa.md §2.1). A machine can
send the requests and count the cards; whether a card is about an idea or about an illustration is for a person.

    extract-checks.py requests --out <dir> [options]        write the requests the app would send for the QA texts
    extract-checks.py run --url <base> --model <id> [options]
                                                            write them, send them, and write the cards to cards.csv
    extract-checks.py summary <cards.csv>...                count cards per text and the labels a person filled in

`requests` and `run` take --density (Concise, Balanced, Comprehensive), --archetypes (Definition,Cloze,...),
--no-skip-illustrations, and --sources <dir> (default scripts/qa/extract). The requests come from the app's own code
(`ExtractRequestDump`, a desktopTest of :core:data, run through Gradle), so they are exactly what Smart Extract sends,
except that the cards of earlier parts are not listed to the model: this script drops repeated fronts itself.

`run` sends to an OpenAI-compatible server: --url is its base (https://api.openai.com/v1, http://localhost:11434/v1),
the key is read from MNEMO_QA_API_KEY (never put it on the command line), --no-schema leaves out response_format for
a model without structured output. The texts go to that provider: use one you would use with the app.

In cards.csv, fill the `label` column with `idea` (worth a card on its own), `illustration` (about an example,
anecdote or story) or `bad` (wrong, unclear or not a card), then run `summary`. Standard library only.
"""
import argparse
import csv
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SOURCES = ROOT / "scripts" / "qa" / "extract"
THINK = re.compile(r"<think>.*?</think>", re.S)
LABELS = ("idea", "illustration", "bad")


def write_requests(args, out: Path) -> list:
    env = dict(os.environ)
    env["MNEMO_EXTRACT_DUMP"] = str(out.resolve())
    env["MNEMO_EXTRACT_SOURCES"] = str(Path(args.sources).resolve())
    env["MNEMO_EXTRACT_DENSITY"] = args.density
    env["MNEMO_EXTRACT_ARCHETYPES"] = args.archetypes
    env["MNEMO_EXTRACT_SKIP_ILLUSTRATIONS"] = "0" if args.no_skip_illustrations else "1"
    gradle = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    command = [str(gradle), ":core:data:desktopTest", "--tests", "*ExtractRequestDump*", "--rerun", "-q"]
    print("Writing the requests with Gradle…", file=sys.stderr)
    subprocess.run(command, cwd=ROOT, env=env, check=True)
    manifest = out / "manifest.json"
    if not manifest.exists():
        sys.exit(f"Gradle ran but wrote no {manifest}: was the test skipped?")
    return json.loads(manifest.read_text())


def send(url: str, key: str | None, body: dict, timeout: int) -> dict:
    request = urllib.request.Request(
        url.rstrip("/") + "/chat/completions",
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json", **({"Authorization": f"Bearer {key}"} if key else {})},
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read())


def cards_of(reply: str) -> list:
    """The cards in a reply, leniently: the whole reply as JSON, else the outermost {…} or […] in it."""
    text = THINK.sub("", reply).strip()
    candidates = [text]
    for open_, close in (("{", "}"), ("[", "]")):
        start, end = text.find(open_), text.rfind(close)
        if 0 <= start < end:
            candidates.append(text[start : end + 1])
    for candidate in candidates:
        try:
            value = json.loads(candidate)
        except json.JSONDecodeError:
            continue
        if isinstance(value, dict):
            value = value.get("cards", [])
        if isinstance(value, list):
            return [card for card in value if isinstance(card, dict)]
    return []


def key(front: str) -> str:
    return re.sub(r"\W+", " ", front).strip().lower()


def run(args):
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    manifest = write_requests(args, out)
    api_key = os.environ.get("MNEMO_QA_API_KEY")
    rows, seen = [], defaultdict(set)
    totals = Counter()
    for entry in manifest:
        body = json.loads((out / entry["request"]).read_text())
        body["model"] = args.model
        if args.no_schema:
            body.pop("response_format", None)
        started = time.monotonic()
        try:
            response = send(args.url, api_key, body, args.timeout)
        except urllib.error.HTTPError as error:
            detail = error.read().decode(errors="replace")[:300]
            sys.exit(f"{entry['source']} part {entry['part']}: HTTP {error.code}: {detail}")
        reply = response["choices"][0]["message"].get("content") or ""
        (out / entry["request"].replace(".json", ".reply.txt")).write_text(reply)
        cards = cards_of(reply)
        kept = 0
        for card in cards:
            front = str(card.get("front", "")).strip()
            if not front or key(front) in seen[entry["source"]]:
                continue
            seen[entry["source"]].add(key(front))
            kept += 1
            rows.append(
                {
                    "source": entry["source"],
                    "part": entry["part"],
                    "type": card.get("type", ""),
                    "front": front,
                    "back": str(card.get("back", "")).strip(),
                    "label": "",
                }
            )
        totals[entry["source"]] += kept
        print(f"{entry['source']} part {entry['part']}/{entry['parts']} ({entry['words']} words): {kept} cards, {time.monotonic() - started:.1f} s")
    csv_path = out / "cards.csv"
    with csv_path.open("w", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=["source", "part", "type", "front", "back", "label"])
        writer.writeheader()
        writer.writerows(rows)
    print()
    for source, count in sorted(totals.items()):
        print(f"{source}: {count} cards")
    print(f"\nCards in {csv_path}: label each row {', '.join(LABELS)}, then run `summary`.")


def summary(args):
    counts = defaultdict(Counter)
    for path in args.csv:
        with open(path, newline="") as file:
            for row in csv.DictReader(file):
                label = row.get("label", "").strip().lower()
                counts[row["source"]]["cards"] += 1
                counts[row["source"]][label if label in LABELS else "unlabelled"] += 1
    print(f"{'source':32} {'cards':>5} {'idea':>5} {'illus.':>6} {'bad':>4} {'unlab.':>6} {'illus. %':>8}")
    for source, c in sorted(counts.items()):
        labelled = c["idea"] + c["illustration"] + c["bad"]
        share = f"{100 * c['illustration'] / labelled:.0f}%" if labelled else "–"
        print(f"{source:32} {c['cards']:>5} {c['idea']:>5} {c['illustration']:>6} {c['bad']:>4} {c['unlabelled']:>6} {share:>8}")


def add_request_options(parser):
    parser.add_argument("--sources", default=str(DEFAULT_SOURCES), help="folder of .md/.txt texts")
    parser.add_argument("--density", default="Balanced", choices=["Concise", "Balanced", "Comprehensive"])
    parser.add_argument("--archetypes", default="Definition,Cloze", help="comma-separated: Definition, Cloze, MultipleChoice, CaseStudy")
    parser.add_argument("--no-skip-illustrations", action="store_true", help="turn Skip examples and anecdotes off")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)

    requests = commands.add_parser("requests", help="write the requests only")
    requests.add_argument("--out", required=True)
    add_request_options(requests)

    send_ = commands.add_parser("run", help="write the requests, send them and collect the cards")
    send_.add_argument("--url", required=True, help="the server's OpenAI-compatible base URL")
    send_.add_argument("--model", required=True)
    send_.add_argument("--out", default=f"build/extract-checks/{time.strftime('%Y%m%d-%H%M%S')}")
    send_.add_argument("--no-schema", action="store_true", help="leave out response_format")
    send_.add_argument("--timeout", type=int, default=180)
    add_request_options(send_)

    summarize = commands.add_parser("summary", help="count cards and labels")
    summarize.add_argument("csv", nargs="+")

    args = parser.parse_args()
    if args.command == "requests":
        manifest = write_requests(args, Path(args.out))
        print(f"{len(manifest)} requests in {args.out}")
    elif args.command == "run":
        run(args)
    else:
        summary(args)


if __name__ == "__main__":
    main()
