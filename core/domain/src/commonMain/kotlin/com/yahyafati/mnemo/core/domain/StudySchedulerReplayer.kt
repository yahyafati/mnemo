package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.data.sync.CardAnswerer
import com.yahyafati.mnemo.core.data.sync.ScheduleReplayer
import com.yahyafati.mnemo.core.model.UserSettings

/**
 * The sync merge's way of answering a card (ADR 0013): the same [StudyScheduler] the study screen uses, so a card
 * reviewed on two devices ends up with the schedule one device would have produced by answering both reviews in order.
 */
internal class StudySchedulerReplayer : ScheduleReplayer {
    override fun answerer(settings: UserSettings): CardAnswerer {
        val scheduler = StudyScheduler(settings)
        return CardAnswerer { card, rating, reviewedAt -> scheduler.answer(card, rating, reviewedAt).card }
    }
}
