# ADR 0002: In-house Markdown subset for card content

- **Status:** Accepted
- **Date:** 2026-09-28
- **Context for:** ROADMAP Phase 1, "Card rendering (`:core:ui`)"; ARCHITECTURE §8 ("A Compose Markdown renderer")

## Context

Card faces need Markdown (emphasis, code, lists), code blocks, and Anki cloze deletions
(`{{c1::answer::hint}}`). Cloze markup sits *inside* Markdown (`{{c1::**ATP**}}`) and must render
three ways: hidden on the front, highlighted when revealed, and plain on sibling cards.

Compose Markdown libraries render full CommonMark to their own composables. None of them knows
cloze syntax, so we would have to pre-process cloze into something the library passes through,
then style it after the fact. They also bring a parser dependency and layout we don't control on
the one screen that must hold 60/120 fps.

## Decision

1. `:core:ui/card/markdown/Markdown.kt` parses (moved to `:core:model/markdown/` in Phase 2, see
   ADR 0004, which also added images, math and sound tags) a card-sized subset in pure Kotlin: headings,
   paragraphs, fenced code, block quotes, bullet and numbered lists, rules; inline bold, italic,
   strikethrough, code, links, and cloze deletions as first-class inline nodes.
2. Unlike CommonMark, a single newline in a paragraph is a line break. Cards are short, and people
   press Enter meaning it.
3. `MarkdownText` renders the tree with plain `Text`/`AnnotatedString`s from the theme, parsing once
   per card (`remember(markdown)`).
4. Anything outside the subset shows as literal text; nothing is dropped.

## Consequences

- Cloze rendering is a styling choice in one place, and the parser is unit-tested on the JVM
  (`MarkdownTest`).
- No tables, images, footnotes or HTML. Images and LaTeX arrive in Phase 2 with media (ROADMAP),
  which extends this renderer; LaTeX still uses a WebView only for cards that contain math.
- Anki decks with rich HTML templates will need a mapping on import (Phase 2, "how much Anki
  templating to support").
