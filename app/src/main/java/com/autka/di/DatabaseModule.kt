package com.autka.di

import android.content.Context
import androidx.room.Room
import com.autka.data.local.AutkaDatabase
import com.autka.data.local.CarOfferDao
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
    fun provideDatabase(@ApplicationContext context: Context): AutkaDatabase =
        Room.databaseBuilder(context, AutkaDatabase::class.java, "autka.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCarOfferDao(db: AutkaDatabase): CarOfferDao = db.carOfferDao()
}
