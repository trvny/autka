package com.autka.data.remote.rates

import com.autka.core.model.Currency
import com.autka.core.model.ExchangeRates
import javax.inject.Inject

/** Supplies a current [ExchangeRates] snapshot from some source. */
interface RateProvider {
    suspend fun latest(): ExchangeRates
}

/**
 * Always-available offline fallback. Rates are indicative constants in PLN base --
 * good enough to keep sorting/filtering sane when no live rates are available, and
 * marked [ExchangeRates.isStale] = true so the UI can flag them.
 */
class StaticRateProvider @Inject constructor() : RateProvider {

    fun snapshot(): ExchangeRates = ExchangeRates(
        base = Currency.PLN,
        perUnit = mapOf(
            Currency.PLN to 1.0,
            Currency.EUR to 4.30, // PLN per 1 EUR (indicative)
            Currency.USD to 4.00, // PLN per 1 USD (indicative)
        ),
        asOfEpochMs = 0L,
        isStale = true,
    )

    override suspend fun latest(): ExchangeRates = snapshot()
}
