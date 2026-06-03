package com.autka.data.remote.rates

import com.autka.core.model.Currency
import com.autka.core.model.ExchangeRates
import javax.inject.Inject

/**
 * Live PLN-based rates from NBP. NBP "mid" is PLN per 1 unit of the foreign currency,
 * which is exactly the "base per unit" convention [ExchangeRates] expects.
 */
class NbpRateProvider @Inject constructor(
    private val api: NbpApi,
) : RateProvider {

    override suspend fun latest(): ExchangeRates {
        val rates = api.tableA().firstOrNull()?.rates.orEmpty()
        val byCode = rates.associate { it.code.uppercase() to it.mid }
        val perUnit = buildMap {
            put(Currency.PLN, 1.0)
            byCode["EUR"]?.let { put(Currency.EUR, it) }
            byCode["USD"]?.let { put(Currency.USD, it) }
        }
        return ExchangeRates(
            base = Currency.PLN,
            perUnit = perUnit,
            asOfEpochMs = System.currentTimeMillis(),
            isStale = false,
        )
    }
}
