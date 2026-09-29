package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.common.time.StudyDay
import com.yahyafati.mnemo.core.data.repository.StatsRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.model.RetentionOverview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** The Decks screen's retention tiles and per-deck retention health (ROADMAP Phase 5). */
class GetRetentionOverviewUseCase @Inject constructor(
    private val statsRepository: StatsRepository,
    private val settingsRepository: UserSettingsRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<RetentionOverview> {
        val zone = clock.zone()
        val windowStart = StudyDay.start(StudyDay.date(clock.now(), zone).minusDays(RETENTION_WINDOW_DAYS - 1), zone)
        return combine(
            settingsRepository.settings,
            statsRepository.observePassCounts(windowStart),
            statsRepository.observeDeckMaturity(),
            statsRepository.observeRetrievability(),
        ) { settings, passCounts, maturity, buckets ->
            RetentionOverview(
                retention = passCounts.rate,
                matureCards = maturity.sumOf { it.mature },
                deckRecall = RetentionMath.deckRecall(buckets, RetentionMath.fsrs(settings)),
            )
        }
    }

    internal companion object {
        /** True retention is measured over the last 30 study days, today included. */
        const val RETENTION_WINDOW_DAYS = 30L
    }
}
