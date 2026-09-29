"""
Writes the reference data for `FsrsOptimizerTest` (core/scheduler/src/test/resources/optimizer/).

The reference is py-fsrs's own optimizer (`fsrs.Optimizer`), the implementation `FsrsOptimizer`
ports. The script simulates a learner whose memory follows FSRS with known, non-default
parameters, then records what py-fsrs computes from that review log:

- the mean log loss at the default and at the learner's true parameters (the forward pass),
- the gradient of the mean loss at the defaults, with every card's full history in the graph
  (the backward pass),
- the card order of each epoch (py-fsrs shuffles with Python's `random.Random(42)`, which the
  Kotlin test replays instead of reimplementing),
- the optimized parameters and their loss.

Run with the version pinned below (`pip install "fsrs[optimizer]==6.3.2"`):

    python core/scheduler/fixtures/make_optimizer_fixtures.py
"""

import json
import random
from importlib.metadata import version
from datetime import datetime, timedelta, timezone
from pathlib import Path

import torch
from fsrs import Card, Optimizer, Rating, ReviewLog, Scheduler
from fsrs.optimizer import max_seq_len
from fsrs.scheduler import DEFAULT_PARAMETERS
from torch.nn import BCELoss

FSRS_VERSION = "6.3.2"

OUT = Path(__file__).resolve().parent.parent / "src" / "test" / "resources" / "optimizer"

# A learner who forgets faster than the defaults assume at first, but whose memories
# consolidate more on success, with a steeper forgetting curve (w20).
TRUE_PARAMETERS = [
    0.6, 1.9, 4.2, 11.0,  # initial stability per first rating
    5.8, 1.1, 2.4, 0.02,  # difficulty
    1.55, 0.2, 1.1,  # recall stability
    1.9, 0.09, 0.35, 1.9,  # forget stability
    0.5, 2.2,  # hard penalty, easy bonus
    0.6, 0.15, 0.1,  # short-term stability
    0.3,  # decay
]

CARDS = 260
START = datetime(2025, 1, 1, tzinfo=timezone.utc)
HORIZON_DAYS = 300
MAX_REVIEWS_PER_CARD = 24


def simulate(rng: random.Random) -> list[ReviewLog]:
    scheduler = Scheduler(parameters=TRUE_PARAMETERS, desired_retention=0.85, enable_fuzzing=True)
    end = START + timedelta(days=HORIZON_DAYS)
    logs: list[ReviewLog] = []
    for card_id in range(1, CARDS + 1):
        card = Card(card_id=card_id)
        now = START + timedelta(days=rng.uniform(0, HORIZON_DAYS / 2))
        for _ in range(MAX_REVIEWS_PER_CARD):
            if now >= end:
                break
            if card.last_review is None:
                rating = rng.choices(list(Rating), weights=[0.15, 0.1, 0.6, 0.15])[0]
            elif (now - card.last_review).days < 1:
                rating = rng.choices(list(Rating), weights=[0.1, 0.1, 0.8, 0.0])[0]
            else:
                recalled = rng.random() < scheduler.get_card_retrievability(card, now)
                rating = rng.choices([Rating.Hard, Rating.Good, Rating.Easy], weights=[0.15, 0.7, 0.15])[0] if recalled else Rating.Again
            card, log = scheduler.review_card(card, rating, now)
            logs.append(log)
            # The learner shows up at the due time, or up to a third of the interval late.
            interval = card.due - now
            now = card.due + interval * rng.uniform(0, 0.33) + timedelta(minutes=rng.uniform(0, 90))
    return logs


def replay_loss(review_logs: list[ReviewLog], parameters: torch.Tensor) -> torch.Tensor:
    """Mean loss over every card's history (as `Optimizer._compute_batch_loss`), kept in the graph."""
    histories: dict[int, list[ReviewLog]] = {}
    for log in review_logs:
        histories.setdefault(log.card_id, []).append(log)
    scheduler = Scheduler(parameters=parameters)
    loss_fn = BCELoss()
    losses = []
    for card_id in sorted(histories):
        history = sorted(histories[card_id], key=lambda log: log.review_datetime)[:max_seq_len]
        card = Card(card_id=card_id, due=history[0].review_datetime)
        for log in history:
            if card.last_review and (log.review_datetime - card.last_review).days > 0:
                predicted = scheduler.get_card_retrievability(card, log.review_datetime)
                recalled = torch.tensor(0.0 if log.rating == Rating.Again else 1.0, dtype=torch.float64)
                losses.append(loss_fn(predicted, recalled))
            card, _ = scheduler.review_card(card, log.rating, log.review_datetime)
    return torch.stack(losses).mean()


def epoch_orders(card_ids: list[int], epochs: int) -> list[list[int]]:
    """The card order of each epoch, exactly as `Optimizer.compute_optimal_parameters` shuffles."""
    rng = random.Random(42)
    order = sorted(card_ids)
    orders = []
    for _ in range(epochs):
        rng.shuffle(order)
        orders.append(list(order))
    return orders


def main() -> None:
    assert version("fsrs") == FSRS_VERSION, f"expected py-fsrs {FSRS_VERSION}, found {version('fsrs')}"
    # py-fsrs fuzzes intervals with the global generator.
    random.seed(7)
    logs = simulate(random.Random(2025))

    OUT.mkdir(parents=True, exist_ok=True)
    with open(OUT / "revlog.csv", "w") as out:
        out.write("card_id,reviewed_at_ms,rating\n")
        for log in sorted(logs, key=lambda log: (log.card_id, log.review_datetime)):
            millis = int(log.review_datetime.timestamp() * 1000)
            out.write(f"{log.card_id},{millis},{int(log.rating)}\n")
    # Reload through the CSV so Python and Kotlin see the same millisecond timestamps.
    logs = []
    with open(OUT / "revlog.csv") as source:
        next(source)
        for line in source:
            card_id, millis, rating = (int(x) for x in line.split(","))
            when = datetime.fromtimestamp(millis / 1000, tz=timezone.utc)
            logs.append(ReviewLog(card_id=card_id, rating=Rating(rating), review_datetime=when, review_duration=None))

    optimizer = Optimizer(logs)
    defaults = torch.tensor(DEFAULT_PARAMETERS, dtype=torch.float64, requires_grad=True)
    default_loss = replay_loss(logs, defaults)
    default_loss.backward()

    optimized = optimizer.compute_optimal_parameters()
    reference = {
        "fsrs_version": FSRS_VERSION,
        "torch_version": torch.__version__,
        "reviews": len(logs),
        "cards": len({log.card_id for log in logs}),
        "true_parameters": TRUE_PARAMETERS,
        "loss_default": optimizer._compute_batch_loss(parameters=list(DEFAULT_PARAMETERS)),
        "loss_true": optimizer._compute_batch_loss(parameters=TRUE_PARAMETERS),
        "gradient_default": defaults.grad.tolist(),
        "epoch_orders": epoch_orders(list(optimizer._revlogs_train.keys()), epochs=5),
        "optimized": optimized,
        "loss_optimized": optimizer._compute_batch_loss(parameters=optimized),
    }
    assert abs(reference["loss_default"] - default_loss.item()) < 1e-12
    with open(OUT / "reference.json", "w") as out:
        json.dump(reference, out, indent=2)
        out.write("\n")
    print(f"{len(logs)} reviews of {reference['cards']} cards")
    print(f"loss: default {reference['loss_default']:.6f}, true {reference['loss_true']:.6f}, optimized {reference['loss_optimized']:.6f}")


if __name__ == "__main__":
    main()
