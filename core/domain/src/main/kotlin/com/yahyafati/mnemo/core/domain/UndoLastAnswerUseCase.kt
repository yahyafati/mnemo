package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.data.repository.ReviewRepository
import javax.inject.Inject

/** Reverts a saved answer: restores the card as it was and deletes the review log. */
class UndoLastAnswerUseCase @Inject constructor(
    private val reviewRepository: ReviewRepository,
) {
    suspend operator fun invoke(answer: CardAnswer) {
        reviewRepository.undoAnswer(answer.previous, answer.log.id)
    }
}
