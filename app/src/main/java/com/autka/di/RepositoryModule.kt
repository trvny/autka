package com.autka.di

import com.autka.data.repository.CarOfferRepository
import com.autka.data.repository.DefaultExchangeRateRepository
import com.autka.data.repository.ExchangeRateRepository
import com.autka.data.repository.OfflineFirstCarOfferRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCarOfferRepository(
        impl: OfflineFirstCarOfferRepository,
    ): CarOfferRepository

    @Binds
    @Singleton
    abstract fun bindExchangeRateRepository(
        impl: DefaultExchangeRateRepository,
    ): ExchangeRateRepository
}
