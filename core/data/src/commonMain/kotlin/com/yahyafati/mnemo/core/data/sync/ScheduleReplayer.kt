package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.model.UserSettings
import java.time.Instant

/**
 * How a card changes when it is answered, for the sync merge to replay reviews that two devices made on the same
 * card (ADR 0013). `:core:data` can't see the scheduler's model mapping, which lives in `:core:domain`
 * (`StudyScheduler`), so `domainModule` binds this interface to it.
 */
interface ScheduleReplayer {
    /** Answers for [settings]: the user's retention, steps and fitted weights. */
    fun answerer(settings: UserSettings): CardAnswerer
}

/** One answer of a [ScheduleReplayer]. Must be deterministic: the same arguments give the same card on every device. */
fun interface CardAnswerer {
    /** [card] after it was answered with [rating] at [reviewedAt]. */
    fun answer(card: Card, rating: Rating, reviewedAt: Instant): Card
}
