package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.data.repository.ReviewRepository
import javax.inject.Inject

/**
 * Saves an answer: the card's new state and its review log, in one transaction.
 *
 * The answer itself comes from [StudyScheduler.answer], which is pure, so the study screen moves
 * to the next card first and saves afterwards (optimistic UI, ARCHITECTURE §5.1).
 */
class AnswerCardUseCase @Inject constructor(
    private val reviewRepository: ReviewRepository,
) {
    suspend operator fun invoke(answer: CardAnswer) {
        reviewRepository.recordAnswer(answer.card, answer.log)
    }
}
