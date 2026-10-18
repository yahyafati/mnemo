# Smart Extract QA texts

Texts for judging which cards Smart Extract writes (ADR 0015, C0 and C7), sent by `scripts/qa/extract-checks.py`.
They were written for Mnemo and are under the project's license (GPL-3.0-or-later). The people and stories in them
are invented; the facts they explain are meant to be right, but the texts are test material, not a reference.

| Text | What it tests | What good cards look like |
|---|---|---|
| `narrative-chapter.md` | a popular-science chapter: anecdotes around a few ideas (loss aversion, prospect theory, the endowment effect, framing) | cards on the ideas; none on the fisherman, the mugs' price or the grandmother's shop |
| `story-prologue.md` | a novel's prologue: a story and nothing to learn | no cards, or almost none |
| `dense-textbook.md` | the control: a dense textbook section on cellular respiration | many cards, about as many as today |
| `essay-with-headings.md` | an essay with nested headings (`#`–`###`) and examples in its sections; two parts at today's 1,200 words | cards on the ideas; the famous examples that are the content (the prisoner's dilemma, tit for tat) may get cards, the uncle's games and Dagen H's hour should not |

Only `essay-with-headings.md` is long enough (three parts) for the default context summary; run
`extract-checks.py` with `--summary always` (and `--overlap Always`) to see what the summary and the overlap do for
the others. This README is not itself a text: the request dump skips it.

A new text goes here with a row in this table. Keep each under a few thousand words so a run stays cheap.
