package com.carfinder.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carfinder.core.model.ImportCostCalculator
import com.carfinder.core.model.Region
import com.carfinder.data.repository.CarOfferRepository
import com.carfinder.data.repository.ExchangeRateRepository
import com.carfinder.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class OfferDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: CarOfferRepository,
    rateRepository: ExchangeRateRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val offerId: String = checkNotNull(savedStateHandle["offerId"])

    // Default assumption for shipping a US car to Poland; surface as editable in UI later.
    private val defaultUsShippingUsd = 2_400.0

    val uiState: StateFlow<OfferDetailUiState> =
        combine(
            repository.observeOffer(offerId),
            rateRepository.rates(),
            settingsRepository.displayCurrency,
        ) { offer, rates, currency ->
            when {
                offer == null -> OfferDetailUiState.NotFound
                offer.region == Region.USA -> OfferDetailUiState.Success(
                    offer = offer,
                    importEstimate = ImportCostCalculator.estimate(
                        vehiclePriceUsd = offer.price.amount,
                        shippingUsd = defaultUsShippingUsd,
                        engineCapacityCc = null,
                    ),
                    displayCurrency = currency,
                    exchangeRates = rates,
                )
                else -> OfferDetailUiState.Success(
                    offer = offer,
                    importEstimate = null,
                    displayCurrency = currency,
                    exchangeRates = rates,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OfferDetailUiState.Loading,
        )
}
