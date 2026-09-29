# ADR 0007: Analytics and the FSRS optimizer

- **Status:** Accepted
- **Date:** 2026-09-29
- **Context for:** ROADMAP Phase 5; PROJECT_OVERVIEW §4.4, §4.5; ARCHITECTURE §6; ADR 0001

## Decision

### Stats are SQL aggregates

`StatsDao` (`:core:database`) returns one row per day, deck or bucket, never one per card or
review. `StatsRepository` (`:core:data`) maps them to `:core:model` types and turns study days
(`StudyDay`, 4 a.m. rollover) into day numbers with the same offset trick as the streak query.
`ComputeRetentionStatsUseCase` (Analytics) and `GetRetentionOverviewUseCase` (Decks tiles and
retention health) combine the aggregates with the user's FSRS model in `:core:domain`.

No schema change was needed: the review-log queries use the existing `reviewedAt` and
`(cardId, reviewedAt)` indices. `StatsDaoTest` checks the plans and runs every query on 20,000
cards and 120,000 reviews.

### Definitions

| Number | Definition |
|---|---|
| True retention | Share of answers to cards in review state (`stateBefore = 2`) that weren't Again, over the last 30 study days. The badge compares it with the 30 days before. |
| Volume | Every answer in the last 30 study days, learning steps included. |
| Stability | Mean stability (days) of cards in review state. |
| Mature | Review state with stability ≥ 21 days (Anki's 21-day interval; FSRS stability is the interval at 90% retention). Young: review state below that. Learning: learning or relearning. |
| Time saved | (answers a "review every card once a day" routine would have taken in the window − answers actually given) × mean answer time. The routine is counted per live card from its first review. It is labeled "vs. daily review", not a claim about cramming in general. |
| Recall now / retention health | Mean retrievability of a deck's studied, unsuspended cards at this moment, subdecks included. |
| Hardest cards | The 5 cards with the most lapses (at least 2); 8 or more is a leech, Anki's default threshold. |
| Forecast | Unsuspended studied cards due on each of the next 14 study days; overdue cards count toward today. |
| Forgetting curve | One card answered Good on day 0 and Good at each due date (the user's weights and desired retention, no learning steps, no fuzz), against the same card never reviewed again, over 60 days. |

### Retrievability without reading cards

Retrievability depends only on `elapsed whole days / stability` (and the decay weight). The
database groups cards per deck into narrow buckets of that ratio (0.1 wide below 10, 1 wide up
to 60, one bucket beyond) and returns each bucket's count and mean ratio; `:core:domain`
evaluates `Fsrs.retrievability(meanRatio, 1)` once per bucket. SQLite on Android has no `pow`,
and a few hundred rows per deck at most replace one row per card. The error of using the
bucket mean is far below what the UI shows (one decimal of a percent).

### The optimizer is a port of py-fsrs's

`FsrsOptimizer` (`:core:scheduler/optimizer`) ports py-fsrs 6.3.2's `Optimizer.compute_optimal_parameters`,
the same reference the scheduler already follows (ADR 0001): each card's first 64 reviews are
replayed from the default first-review state, the predicted retrievability at every review made
a day or more after the previous one is scored with binary cross entropy against "not Again",
and the weights follow Adam (PyTorch defaults) with cosine annealing from 0.04, 5 epochs of
512-review mini-batches with truncated backpropagation at batch boundaries, weights clipped to
their bounds after each step. The epoch with the lowest loss wins. Fewer than 512 training
reviews: no fit.

py-fsrs uses PyTorch's autograd. Mnemo uses forward-mode derivatives instead: each replayed
card carries the partial derivatives of its stability and difficulty with respect to the 21
weights, updated with hand-derived formulas (clamps pass the gradient on their closed range,
as in PyTorch). No autodiff or ML library is added; 100,000 reviews fit in well under a second
on a laptop JVM.

Memory state doesn't depend on learning steps, desired retention or fuzz, so the optimizer
needs only `(cardId, reviewedAt, rating)`. `FsrsOptimization` (`:core:data/scheduling`) reads them
500 cards at a time into compact arrays.

**Reference test.** `core/scheduler/fixtures/make_optimizer_fixtures.py` simulates a learner with
known non-default weights, runs py-fsrs on the log, and records the loss (default, true and
fitted weights), the full-history gradient at the defaults, each epoch's card order and the
fitted weights. `FsrsOptimizerTest` replays the same orders (py-fsrs shuffles with Python's
`random.Random(42)`; the app shuffles with a seeded Kotlin `Random`) and matches the losses and
gradient to 1e-12 and the fitted weights to 1e-9. Finite differences check every derivative
independently of the reference.

### Applying the fit

`OptimizeFsrsWorker` runs `FsrsOptimization` when the user taps **Optimize** (Settings ›
Scheduling). The fitted weights are applied only if their loss on the history is lower than the
loss of the weights in use (py-fsrs itself can return weights worse than the defaults); the
result (applied, no improvement, not enough reviews) is reported with both losses. Weights live
in DataStore (`UserSettings.fsrsWeights`) with when they were fitted, how many reviews and the
losses, so backups carry them. `StudyScheduler.parameters` uses them everywhere the app
schedules and falls back to the defaults if stored weights are out of bounds. **Reset to
defaults** removes them. Anki import still replays imported history with the default weights.

## Consequences

- Analytics cost is a handful of aggregate queries, independent of history size except for
  index range scans.
- The optimizer can be compared line by line with py-fsrs when the reference changes: rerun the
  fixture script and the test.
- Metrics that need per-card math (retrievability) go through buckets; new ones should too.
- The optimizer runs only on request. Automatic re-fitting (e.g. every few thousand reviews) can
  reuse the worker later.

## Alternatives considered

- **fsrs-rs (Anki's optimizer) through JNI.** Better pretraining and regularization, but a Rust
  toolchain and native libraries for every ABI, for a feature that runs a few times a year.
- **A general autodiff library (e.g. KotlinDL, DJL).** Large dependencies for 21 parameters and
  a handful of formulas.
- **Loading cards and computing retrievability in Kotlin.** Simple, but breaks "SQL aggregates
  only" and scales with collection size on every screen visit.
- **Applying whatever the optimizer returns.** Matches py-fsrs, but can make scheduling worse on
  small or unusual histories.
