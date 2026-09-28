package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.common.time.StudyDay
import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.data.repository.ReviewRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.model.UserSettings
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** A ready-to-run session plus the scheduler configured with the user's settings. */
data class StudyQueue(
    val session: StudySession,
    val scheduler: StudyScheduler,
    val settings: UserSettings,
    /** The studied deck's name, or null for the Daily Mix across all decks. */
    val deckName: String?,
)

/**
 * Builds today's queue for one deck and its subdecks, or for every deck when `deckId` is null
 * (the Daily Mix). New and review cards are capped by what is left of today's limits; learning
 * cards are never capped.
 */
class BuildStudyQueueUseCase @Inject constructor(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val reviewRepository: ReviewRepository,
    private val settingsRepository: UserSettingsRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(deckId: String? = null): StudyQueue {
        val settings = settingsRepository.settings.first()
        val decks = deckRepository.getDecks()
        val deckIds = if (deckId == null) decks.map { it.id } else DeckNode.subtreeIds(decks, deckId)
        val now = clock.now()
        val dayEnd = StudyDay.end(now, clock.zone())
        val today = reviewRepository.getTodayCounts()

        val candidates = cardRepository.getQueueCandidates(
            deckIds = deckIds,
            now = now,
            dayEnd = dayEnd,
            reviewLimit = (settings.reviewsPerDay - today.reviewsDone).coerceAtLeast(0),
            newLimit = (settings.newCardsPerDay - today.newStudied).coerceAtLeast(0),
        )
        return StudyQueue(
            session = StudySession.create(candidates.learning, candidates.review, candidates.new, dayEnd),
            scheduler = StudyScheduler(settings),
            settings = settings,
            deckName = deckId?.let { id -> decks.firstOrNull { it.id == id }?.name },
        )
    }
}
