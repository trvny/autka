package com.autka.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.autka.feature.listings.ListingsViewModel

/**
 * Map view of the current offers. Reuses ListingsViewModel — it observes the same
 * offer repository flow and refreshes on init, so this instance is populated too.
 */
@Composable
fun MapRoute(
    onBack: () -> Unit,
    onOfferClick: (String) -> Unit,
    viewModel: ListingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    MapScreen(offers = uiState.offers, onBack = onBack, onOfferClick = onOfferClick)
}
