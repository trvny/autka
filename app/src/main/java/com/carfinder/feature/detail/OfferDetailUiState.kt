package com.carfinder.feature.detail

import com.carfinder.core.model.CarOffer
import com.carfinder.core.model.Currency
import com.carfinder.core.model.ExchangeRates
import com.carfinder.core.model.ImportCostEstimate

sealed interface OfferDetailUiState {
    data object Loading : OfferDetailUiState
    data class Success(
        val offer: CarOffer,
        val importEstimate: ImportCostEstimate?,
        val displayCurrency: Currency,
        val exchangeRates: ExchangeRates?,
        // Editable import-calculator inputs (relevant when importEstimate != null):
        val shippingUsd: Double = 0.0,
        val engineCapacityCc: Int? = null,
    ) : OfferDetailUiState
    data object NotFound : OfferDetailUiState
}
