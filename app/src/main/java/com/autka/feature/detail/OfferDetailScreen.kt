package com.autka.feature.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import com.autka.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autka.core.model.Currency
import com.autka.core.model.ExchangeRates
import com.autka.core.model.ImportCostEstimate
import com.autka.ui.components.EmptyState
import com.autka.ui.components.LoadingIndicator
import com.autka.ui.components.OfferImage
import com.autka.ui.components.formatted
import com.autka.ui.components.kmOrDash

@Composable
fun OfferDetailRoute(
    onBack: () -> Unit,
    viewModel: OfferDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    OfferDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onShippingChange = viewModel::onShippingChange,
        onEngineCapacityChange = viewModel::onEngineCapacityChange,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfferDetailScreen(
    uiState: OfferDetailUiState,
    onBack: () -> Unit,
    onShippingChange: (Double?) -> Unit = {},
    onEngineCapacityChange: (Int?) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.offer)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        when (uiState) {
            is OfferDetailUiState.Loading -> LoadingIndicator(Modifier.padding(padding))
            is OfferDetailUiState.NotFound -> EmptyState(stringResource(R.string.offer_unavailable), Modifier.padding(padding))
            is OfferDetailUiState.Success -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val o = uiState.offer
                if (o.imageUrls.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(o.imageUrls) { url ->
                            OfferImage(
                                url = url,
                                modifier = Modifier
                                    .size(width = 280.dp, height = 180.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                        }
                    }
                } else {
                    OfferImage(
                        url = o.thumbnailUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
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
                ListingActions(title = o.title, listingUrl = o.listingUrl)
                SpecRow(stringResource(R.string.spec_year), o.year?.toString() ?: "--")
                SpecRow(stringResource(R.string.spec_mileage), o.mileageKm.kmOrDash())
                SpecRow(stringResource(R.string.spec_fuel), o.fuelType.name)
                SpecRow(stringResource(R.string.spec_transmission), o.transmission.name)
                SpecRow(stringResource(R.string.spec_power), o.powerHp?.let { "$it hp" } ?: "--")
                SpecRow(stringResource(R.string.spec_location), o.location ?: "--")
                uiState.importEstimate?.let { est ->
                    ImportBreakdown(
                        est = est,
                        displayCurrency = uiState.displayCurrency,
                        rates = uiState.exchangeRates,
                        shippingUsd = uiState.shippingUsd,
                        engineCapacityCc = uiState.engineCapacityCc,
                        onShippingChange = onShippingChange,
                        onEngineCapacityChange = onEngineCapacityChange,
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
    shippingUsd: Double,
    engineCapacityCc: Int?,
    onShippingChange: (Double?) -> Unit,
    onEngineCapacityChange: (Int?) -> Unit,
) {
    // Local text state so partial typing isn't clobbered by the reactive recompute.
    var shippingText by remember { mutableStateOf(shippingUsd.toLong().toString()) }
    var engineText by remember { mutableStateOf(engineCapacityCc?.toString() ?: "") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.import_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = shippingText,
                    onValueChange = { shippingText = it; onShippingChange(it.toDoubleOrNull()) },
                    label = { Text(stringResource(R.string.import_shipping_usd)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = engineText,
                    onValueChange = { engineText = it; onEngineCapacityChange(it.toIntOrNull()) },
                    label = { Text(stringResource(R.string.import_engine_cc)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            SpecRow(stringResource(R.string.import_vehicle), est.vehiclePrice.formatted())
            SpecRow(stringResource(R.string.import_shipping), est.shipping.formatted())
            SpecRow(stringResource(R.string.import_customs), est.customsDuty.formatted())
            SpecRow(stringResource(R.string.import_excise), est.exciseDuty.formatted())
            SpecRow(stringResource(R.string.import_vat), est.vat.formatted())
            Divider()
            SpecRow(stringResource(R.string.import_total), est.total.formatted())
            if (rates != null && est.total.currency != displayCurrency) {
                SpecRow(stringResource(R.string.import_total_in, displayCurrency.name), rates.convert(est.total, displayCurrency).formatted())
            }
            Text(
                stringResource(R.string.import_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ListingActions(title: String, listingUrl: String) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(listingUrl)))
                }
            },
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(R.string.view_listing)) }
        OutlinedButton(
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "$title\n$listingUrl")
                }
                runCatching { context.startActivity(Intent.createChooser(send, null)) }
            },
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(R.string.share)) }
    }
}
