package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.model.CurvePoint
import com.yahyafati.mnemo.core.model.Deck
import com.yahyafati.mnemo.core.model.ForgettingCurve
import com.yahyafati.mnemo.core.model.RecallTotal
import com.yahyafati.mnemo.core.model.RetrievabilityBucket
import com.yahyafati.mnemo.core.model.UserSettings
import com.yahyafati.mnemo.core.scheduler.Fsrs
import com.yahyafati.mnemo.core.scheduler.FsrsCard
import com.yahyafati.mnemo.core.scheduler.FsrsRating
import java.time.Duration
import java.time.Instant

/** The model behind the retention numbers: the user's own FSRS weights and retention (ADR 0007). */
internal object RetentionMath {
    fun fsrs(settings: UserSettings) = Fsrs(StudyScheduler.parameters(settings))

    /** Current recall per deck, from the database's buckets: one retrievability per bucket, at its mean ratio. */
    fun deckRecall(buckets: List<RetrievabilityBucket>, fsrs: Fsrs): Map<String, RecallTotal> =
        buckets.groupBy { it.deckId }.mapValues { (_, deckBuckets) ->
            deckBuckets.fold(RecallTotal.None) { total, bucket ->
                total + RecallTotal(bucket.cards, bucket.cards * fsrs.retrievability(bucket.meanElapsedRatio, 1.0))
            }
        }

    /** Each deck's top-level ancestor (itself for a top-level deck). */
    fun roots(decks: List<Deck>): Map<String, Deck> {
        val byId = decks.associateBy { it.id }
        return decks.associate { deck ->
            deck.id to generateSequence(deck) { d -> d.parentId?.let(byId::get) }.last()
        }
    }

    /**
     * One card answered Good on day 0, then Good each time it comes due (no learning steps, no
     * fuzz), next to the same card never reviewed again. [samplesPerDay] points per day; each
     * review adds the curve's value just before it, then 1.
     */
    fun forgettingCurve(settings: UserSettings, horizonDays: Int = CURVE_DAYS, samplesPerDay: Int = 4): ForgettingCurve {
        val fsrs = Fsrs(
            StudyScheduler.parameters(settings).copy(learningSteps = emptyList(), enableFuzzing = false),
        )
        val start = Instant.EPOCH
        var card = fsrs.review(FsrsCard(due = start), FsrsRating.Good, start)
        val firstStability = checkNotNull(card.stability)

        // (review day, stability after it); day 0 is the first review.
        val segments = mutableListOf(0 to firstStability)
        while (true) {
            val day = Duration.between(start, card.due).toDays().toInt()
            if (day > horizonDays) break
            card = fsrs.review(card, FsrsRating.Good, card.due)
            segments += day to checkNotNull(card.stability)
        }

        val scheduled = mutableListOf<CurvePoint>()
        segments.forEachIndexed { i, (from, stability) ->
            val until = segments.getOrNull(i + 1)?.first ?: horizonDays
            val steps = (until - from) * samplesPerDay
            for (step in 0..steps) {
                val elapsed = step.toDouble() / samplesPerDay
                scheduled += CurvePoint(from + elapsed, fsrs.retrievability(elapsed, stability))
            }
        }
        val passive = (0..horizonDays * samplesPerDay).map { step ->
            val day = step.toDouble() / samplesPerDay
            CurvePoint(day, fsrs.retrievability(day, firstStability))
        }
        return ForgettingCurve(horizonDays, scheduled, segments.drop(1).map { it.first }, passive)
    }

    private const val CURVE_DAYS = 60
}
