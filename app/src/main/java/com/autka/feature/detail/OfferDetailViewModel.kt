package com.autka.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autka.core.model.ImportCostCalculator
import com.autka.core.model.Region
import com.autka.data.imports.ImportServicesRepository
import com.autka.data.repository.CarOfferRepository
import com.autka.data.repository.ExchangeRateRepository
import com.autka.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OfferDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: CarOfferRepository,
    rateRepository: ExchangeRateRepository,
    settingsRepository: SettingsRepository,
    importServicesRepository: ImportServicesRepository,
) : ViewModel() {

    private val offerId: String = checkNotNull(savedStateHandle["offerId"])

    // Editable import-calculator inputs. Defaults: a typical US->PL shipping figure and
    // an unknown engine capacity (the calculator then assumes the lower excise band).
    private val shippingUsd = MutableStateFlow(2_400.0)
    private val engineCapacityCc = MutableStateFlow<Int?>(null)

    init {
        // Override the compiled-in seed with the backend directory. Offline-safe: a
        // failed fetch keeps the seed, so this can never break the screen.
        viewModelScope.launch { importServicesRepository.refresh() }
    }

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
                        fuelType = offer.fuelType,
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
        }
            .combine(importServicesRepository.services()) { state, services ->
                // Attach region-matched importers (USA offer -> US importers, etc.).
                // POLAND or non-Success -> unchanged (empty list hides the section).
                if (state is OfferDetailUiState.Success && state.offer.region != Region.POLAND) {
                    state.copy(importServices = services.filter { it.origin == state.offer.region })
                } else {
                    state
                }
            }
            .stateIn(
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
