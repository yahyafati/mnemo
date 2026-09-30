package com.yahyafati.mnemo.core.domain.di

import com.yahyafati.mnemo.core.domain.AcceptGeneratedCardsUseCase
import com.yahyafati.mnemo.core.domain.AnswerCardUseCase
import com.yahyafati.mnemo.core.domain.BuildStudyQueueUseCase
import com.yahyafati.mnemo.core.domain.ComputeRetentionStatsUseCase
import com.yahyafati.mnemo.core.domain.FindDuplicateNotesUseCase
import com.yahyafati.mnemo.core.domain.GenerateCardsUseCase
import com.yahyafati.mnemo.core.domain.GetRetentionOverviewUseCase
import com.yahyafati.mnemo.core.domain.GetTodaySummaryUseCase
import com.yahyafati.mnemo.core.domain.RegenerateCardUseCase
import com.yahyafati.mnemo.core.domain.UndoLastAnswerUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/** The use cases. They hold no state, so each request gets its own. */
val domainModule = module {
    factoryOf(::AcceptGeneratedCardsUseCase)
    factoryOf(::AnswerCardUseCase)
    factoryOf(::BuildStudyQueueUseCase)
    factoryOf(::ComputeRetentionStatsUseCase)
    factoryOf(::FindDuplicateNotesUseCase)
    factoryOf(::GenerateCardsUseCase)
    factoryOf(::GetRetentionOverviewUseCase)
    factoryOf(::GetTodaySummaryUseCase)
    factoryOf(::RegenerateCardUseCase)
    factoryOf(::UndoLastAnswerUseCase)
}
