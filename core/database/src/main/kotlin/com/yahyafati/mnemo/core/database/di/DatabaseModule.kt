package com.yahyafati.mnemo.core.database.di

import android.content.Context
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.RoomTransactionRunner
import com.yahyafati.mnemo.core.database.TransactionRunner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): MnemoDatabase = MnemoDatabase.build(context)
}

@Module
@InstallIn(SingletonComponent::class)
object DaoModule {
    @Provides
    fun providesDeckDao(database: MnemoDatabase) = database.deckDao()

    @Provides
    fun providesNoteDao(database: MnemoDatabase) = database.noteDao()

    @Provides
    fun providesCardDao(database: MnemoDatabase) = database.cardDao()

    @Provides
    fun providesReviewLogDao(database: MnemoDatabase) = database.reviewLogDao()

    @Provides
    fun providesMediaDao(database: MnemoDatabase) = database.mediaDao()
}

@Module
@InstallIn(SingletonComponent::class)
internal interface TransactionModule {
    @Binds
    fun bindsTransactionRunner(runner: RoomTransactionRunner): TransactionRunner
}
