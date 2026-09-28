package com.yahyafati.mnemo.core.data.di

import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.data.repository.DefaultUserSettingsRepository
import com.yahyafati.mnemo.core.data.repository.OfflineCardRepository
import com.yahyafati.mnemo.core.data.repository.OfflineDeckRepository
import com.yahyafati.mnemo.core.data.repository.OfflineReviewRepository
import com.yahyafati.mnemo.core.data.repository.ReviewRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds
    fun bindsDeckRepository(repository: OfflineDeckRepository): DeckRepository

    @Binds
    fun bindsCardRepository(repository: OfflineCardRepository): CardRepository

    @Binds
    fun bindsReviewRepository(repository: OfflineReviewRepository): ReviewRepository

    @Binds
    fun bindsUserSettingsRepository(repository: DefaultUserSettingsRepository): UserSettingsRepository
}
