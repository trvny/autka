package com.autka.feature.listings

import com.autka.core.model.CarOffer
import com.autka.core.model.Currency
import com.autka.core.model.ExchangeRates
import com.autka.core.model.SearchFilter
import com.autka.data.repository.SourceInfo

data class ListingsUiState(
    val isRefreshing: Boolean = false,
    val offers: List<CarOffer> = emptyList(),
    val filter: SearchFilter = SearchFilter(),
    val availableMakes: List<String> = emptyList(),
    val availableSources: List<SourceInfo> = emptyList(),
    val failedSources: List<String> = emptyList(),
    val errorMessage: String? = null,
    val displayCurrency: Currency = Currency.PLN,
    val exchangeRates: ExchangeRates? = null,
) {
    val activeFilterCount: Int get() = filter.activeCount()
    val ratesAreStale: Boolean get() = exchangeRates?.isStale ?: true
}
