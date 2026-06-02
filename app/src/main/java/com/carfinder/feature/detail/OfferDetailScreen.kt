package com.carfinder.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carfinder.core.model.Currency
import com.carfinder.core.model.ExchangeRates
import com.carfinder.core.model.ImportCostEstimate
import com.carfinder.ui.components.EmptyState
import com.carfinder.ui.components.LoadingIndicator
import com.carfinder.ui.components.formatted
import com.carfinder.ui.components.kmOrDash

@Composable
fun OfferDetailRoute(
    onBack: () -> Unit,
    viewModel: OfferDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    OfferDetailScreen(uiState = uiState, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfferDetailScreen(uiState: OfferDetailUiState, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (uiState) {
            is OfferDetailUiState.Loading -> LoadingIndicator(Modifier.padding(padding))
            is OfferDetailUiState.NotFound -> EmptyState("Offer no longer available.", Modifier.padding(padding))
            is OfferDetailUiState.Success -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val o = uiState.offer
                Text(o.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(o.price.formatted(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                val rates = uiState.exchangeRates
                if (rates != null && o.price.currency != uiState.displayCurrency) {
                    Text(
                        "~ ${rates.convert(o.price, uiState.displayCurrency).formatted()}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SpecRow("Year", o.year?.toString() ?: "--")
                SpecRow("Mileage", o.mileageKm.kmOrDash())
                SpecRow("Fuel", o.fuelType.name)
                SpecRow("Transmission", o.transmission.name)
                SpecRow("Power", o.powerHp?.let { "$it hp" } ?: "--")
                SpecRow("Location", o.location ?: "--")
                uiState.importEstimate?.let {
                    ImportBreakdown(
                        est = it,
                        displayCurrency = uiState.displayCurrency,
                        rates = uiState.exchangeRates,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ImportBreakdown(
    est: ImportCostEstimate,
    displayCurrency: Currency,
    rates: ExchangeRates?,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Estimated import to Poland", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SpecRow("Vehicle", est.vehiclePrice.formatted())
            SpecRow("Shipping", est.shipping.formatted())
            SpecRow("Customs duty", est.customsDuty.formatted())
            SpecRow("Excise (akcyza)", est.exciseDuty.formatted())
            SpecRow("VAT (23%)", est.vat.formatted())
            Divider()
            SpecRow("Total landed", est.total.formatted())
            if (rates != null && est.total.currency != displayCurrency) {
                SpecRow("Total landed (${displayCurrency.name})", rates.convert(est.total, displayCurrency).formatted())
            }
            Text(
                "Estimate only -- actual customs valuation, rates and fees vary.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
