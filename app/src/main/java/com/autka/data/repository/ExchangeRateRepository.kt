package com.autka.data.repository

import com.autka.core.model.ExchangeRates
import com.autka.data.remote.rates.NbpRateProvider
import com.autka.data.remote.rates.StaticRateProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface ExchangeRateRepository {
    /** Latest rates; always has a value (seeded with offline fallback). */
    fun rates(): StateFlow<ExchangeRates>

    /** Try to refresh from the live source; silently keeps current rates on failure. */
    suspend fun refresh()
}

@Singleton
class DefaultExchangeRateRepository @Inject constructor(
    private val live: NbpRateProvider,
    staticRates: StaticRateProvider,
) : ExchangeRateRepository {

    private val state = MutableStateFlow(staticRates.snapshot())

    override fun rates(): StateFlow<ExchangeRates> = state.asStateFlow()

    override suspend fun refresh() {
        runCatching { live.latest() }.onSuccess { state.value = it }
        // on failure: keep the existing (possibly stale) rates
    }
}
