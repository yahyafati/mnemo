# ADR 0001: FSRS is the only scheduler

- **Status:** Accepted
- **Date:** 2026-09-28
- **Resolves:** PROJECT_OVERVIEW §11, "Scheduling defaults"

## Context

The mockups disagree. The Decks screen says the daily queue is "calculated by SuperMemo-2", while the Study and Analytics screens show FSRS concepts (stability, retrievability, forgetting curve). We need one answer before Phase 1 builds `:core:scheduler`, the `Card` schema, and the review log.

Many users will come from Anki. Their decks carry SM-2 state per card (ease factor, interval, due date, lapses) and a review log (`revlog`).

## Decision

1. Mnemo schedules with **FSRS only**. There is no SM-2 mode and no per-deck algorithm choice.
2. A card's scheduling state is FSRS memory state: **stability**, **difficulty**, **due**, **state** (New, Learning, Review, Relearning), plus `lastReview`, `reps`, and `lapses`. SM-2 fields are not stored.
3. On `.apkg` import (Phase 2), SM-2 state is **converted** to FSRS memory state:
   - If the card has review history, replay its `revlog` through FSRS to get stability and difficulty. The review history is kept as Mnemo `ReviewLog` rows either way.
   - If there is no usable history, estimate from SM-2 fields: stability from the current interval, and difficulty from the ease factor. Keep the Anki due date so imported cards aren't all due at once.
4. Parameters start at the FSRS defaults. The on-device optimizer (Phase 5) fits them from the user's own `ReviewLog`.
5. The UI never says "SuperMemo-2". The Decks mockup text is replaced when that screen is built.

## Consequences

- One code path to implement, test against reference vectors, and optimize. The interval labels on the rating buttons, the retention analytics, and the forgetting curve all come from the same model.
- `ReviewLog` must record everything FSRS replay and the optimizer need from the start (Phase 1): rating, review time, elapsed days, and the state before the review.
- Anki users get slightly different intervals after import than Anki would give them. That is acceptable: FSRS needs fewer reviews for the same retention, and the due dates they already have are kept.
- `.apkg` export (Phase 2) must write SM-2-compatible fields so Anki can read the deck. Anki with FSRS enabled can also re-derive memory state from the exported review log.
- If SM-2 is ever needed (e.g. a user request for exact Anki parity), it would be a new ADR, not a setting.
