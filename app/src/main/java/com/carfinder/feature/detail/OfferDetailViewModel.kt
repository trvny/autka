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
import kotlinx.coroutines.flow.MutableStateFlow
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

    // Editable import-calculator inputs. Defaults: a typical US->PL shipping figure and
    // an unknown engine capacity (the calculator then assumes the lower excise band).
    private val shippingUsd = MutableStateFlow(2_400.0)
    private val engineCapacityCc = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<OfferDetailUiState> =
        combine(
            repository.observeOffer(offerId),
            rateRepository.rates(),
            settingsRepository.displayCurrency,
            shippingUsd,
            engineCapacityCc,
        ) { offer, rates, currency, shipping, engineCc ->
            when {
                offer == null -> OfferDetailUiState.NotFound
                offer.region == Region.USA -> OfferDetailUiState.Success(
                    offer = offer,
                    importEstimate = ImportCostCalculator.estimate(
                        vehiclePriceUsd = offer.price.amount,
                        shippingUsd = shipping,
                        engineCapacityCc = engineCc,
                    ),
                    displayCurrency = currency,
                    exchangeRates = rates,
                    shippingUsd = shipping,
                    engineCapacityCc = engineCc,
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

    /** Update the shipping assumption (USD). Ignores blank/negative input. */
    fun onShippingChange(usd: Double?) {
        if (usd != null && usd >= 0) shippingUsd.value = usd
    }

    /** Update the engine capacity (cc) used for the PL excise band; null = unknown. */
    fun onEngineCapacityChange(cc: Int?) {
        engineCapacityCc.value = cc?.takeIf { it > 0 }
    }
}
