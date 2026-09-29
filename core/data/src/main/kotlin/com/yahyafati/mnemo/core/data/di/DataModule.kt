package com.yahyafati.mnemo.core.data.di

import android.content.Context
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.work.WorkManager
import com.yahyafati.mnemo.core.data.repository.CardBrowserRepository
import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.data.repository.DataTransferRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.data.repository.DefaultUserSettingsRepository
import com.yahyafati.mnemo.core.data.repository.FileMediaRepository
import com.yahyafati.mnemo.core.data.repository.MediaRepository
import com.yahyafati.mnemo.core.data.repository.OfflineCardBrowserRepository
import com.yahyafati.mnemo.core.data.repository.OfflineCardRepository
import com.yahyafati.mnemo.core.data.repository.OfflineDeckRepository
import com.yahyafati.mnemo.core.data.repository.OfflineReviewRepository
import com.yahyafati.mnemo.core.data.repository.ReviewRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.data.repository.WorkManagerDataTransferRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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

    @Binds
    fun bindsMediaRepository(repository: FileMediaRepository): MediaRepository

    @Binds
    fun bindsCardBrowserRepository(repository: OfflineCardBrowserRepository): CardBrowserRepository

    @Binds
    fun bindsDataTransferRepository(repository: WorkManagerDataTransferRepository): DataTransferRepository
}

@Module
@InstallIn(SingletonComponent::class)
object WorkModule {
    /** Replaced in app tests, which initialize a test WorkManager. */
    @Provides
    fun providesWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}

@Module
@InstallIn(SingletonComponent::class)
internal object AnkiModule {
    /** Anki packages are read and written with Android's own SQLite. */
    @Provides
    fun providesSqliteDriver(): SQLiteDriver = AndroidSQLiteDriver()
}
