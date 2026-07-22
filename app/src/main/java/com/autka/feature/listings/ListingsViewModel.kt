package com.autka.feature.listings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autka.core.model.Currency
import com.autka.core.model.SearchFilter
import com.autka.data.repository.CarOfferRepository
import com.autka.data.repository.ExchangeRateRepository
import com.autka.data.repository.SourceInfo
import com.autka.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ListingsViewModel @Inject constructor(
    private val repository: CarOfferRepository,
    private val rateRepository: ExchangeRateRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(SearchFilter())
    private val transient = MutableStateFlow(TransientState())

    val uiState: StateFlow<ListingsUiState> =
        combine(
            repository.observeOffers(),
            filter,
            transient,
            rateRepository.rates(),
            settingsRepository.displayCurrency,
        ) { offers, f, t, rates, currency ->
            ListingsUiState(
                isRefreshing = t.isRefreshing,
                offers = offers.applyFilter(f, rates, currency)
                    .sortedWith(sortComparator(f.sort, rates, currency)),
                filter = f,
                availableMakes = offers.map { it.make }.distinct().sorted(),
                // Facets describe the actual marketplaces represented by cached offers.
                // The backend is only a transport and must never appear as a selectable
                // marketplace id, because no offer has sourceId="backend".
                availableSources = offers
                    .map { offer ->
                        SourceInfo(
                            id = offer.sourceId,
                            displayName = offer.sourceId.toSourceDisplayName(),
                            enabled = true,
                        )
                    }
                    .distinctBy { it.id }
                    .sortedBy { it.displayName },
                failedSources = t.failedSources,
                errorMessage = t.errorMessage,
                displayCurrency = currency,
                exchangeRates = rates,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ListingsUiState(),
        )

    init {
        refresh()
        viewModelScope.launch { rateRepository.refresh() }
    }

    fun onQueryChange(query: String) {
        filter.value = filter.value.copy(query = query)
    }

    fun onApplyFilter(newFilter: SearchFilter) {
        filter.value = newFilter
        refresh()
    }

    fun onResetFilter() {
        filter.value = SearchFilter(query = filter.value.query)
        refresh()
    }

    fun onDisplayCurrencyChange(currency: Currency) {
        viewModelScope.launch { settingsRepository.setDisplayCurrency(currency) }
    }

    fun refresh() {
        viewModelScope.launch {
            transient.value = transient.value.copy(isRefreshing = true, errorMessage = null)
            val failed = runCatching { repository.refresh(filter.value) }
                .getOrElse {
                    transient.value = transient.value.copy(
                        isRefreshing = false,
                        errorMessage = it.message ?: "Failed to refresh",
                    )
                    return@launch
                }
            transient.value = transient.value.copy(isRefreshing = false, failedSources = failed)
        }
    }
}

private fun String.toSourceDisplayName(): String = when (this) {
    "mock" -> "Sample data"
    "otomoto" -> "Otomoto"
    "olx" -> "OLX"
    "facebook" -> "Facebook Marketplace"
    "us_auction" -> "US auctions"
    else -> split('_', '-')
        .joinToString(" ") { part ->
            part.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            }
        }
}

private data class TransientState(
    val isRefreshing: Boolean = false,
    val failedSources: List<String> = emptyList(),
    val errorMessage: String? = null,
)
