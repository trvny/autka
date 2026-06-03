package com.carfinder.feature.listings

import androidx.compose.ui.res.stringResource
import com.carfinder.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.carfinder.core.model.Currency
import com.carfinder.core.model.FuelType
import com.carfinder.core.model.Region
import com.carfinder.core.model.SearchFilter
import com.carfinder.core.model.SortOrder
import com.carfinder.data.repository.SourceInfo

private val FUEL_CHOICES = listOf(
    FuelType.PETROL, FuelType.DIESEL, FuelType.HYBRID,
    FuelType.PLUGIN_HYBRID, FuelType.ELECTRIC, FuelType.LPG,
)

private const val MIN_YEAR = 1990f
private const val MAX_YEAR = 2026f
private const val MAX_MILEAGE = 300_000f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    filter: SearchFilter,
    availableMakes: List<String>,
    availableSources: List<SourceInfo>,
    priceCurrency: Currency,
    onApply: (SearchFilter) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(filter) { mutableStateOf(filter) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(stringResource(R.string.filters), fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)

            if (availableMakes.isNotEmpty()) {
                Section(stringResource(R.string.filter_make)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableMakes.forEach { make ->
                            FilterChip(
                                selected = draft.make == make,
                                onClick = { draft = draft.copy(make = if (draft.make == make) null else make) },
                                label = { Text(make) },
                            )
                        }
                    }
                }
            }

            Section(stringResource(R.string.filter_price_range, priceCurrency.symbol)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draft.minPrice?.toLong()?.toString() ?: "",
                        onValueChange = { draft = draft.copy(minPrice = it.toDoubleOrNull()) },
                        label = { Text(stringResource(R.string.filter_min)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = draft.maxPrice?.toLong()?.toString() ?: "",
                        onValueChange = { draft = draft.copy(maxPrice = it.toDoubleOrNull()) },
                        label = { Text(stringResource(R.string.filter_max)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Section(stringResource(R.string.filter_min_year, draft.minYear?.toString() ?: stringResource(R.string.filter_any))) {
                Slider(
                    value = (draft.minYear ?: MIN_YEAR.toInt()).toFloat(),
                    onValueChange = { v ->
                        val y = v.toInt()
                        draft = draft.copy(minYear = if (y <= MIN_YEAR.toInt()) null else y)
                    },
                    valueRange = MIN_YEAR..MAX_YEAR,
                    steps = (MAX_YEAR - MIN_YEAR).toInt() - 1,
                )
            }

            Section(stringResource(R.string.filter_max_mileage, draft.maxMileageKm?.let { "${it / 1000}k km" } ?: stringResource(R.string.filter_any))) {
                Slider(
                    value = (draft.maxMileageKm ?: MAX_MILEAGE.toInt()).toFloat(),
                    onValueChange = { v ->
                        val km = (v / 5_000).toInt() * 5_000
                        draft = draft.copy(maxMileageKm = if (km >= MAX_MILEAGE.toInt()) null else km)
                    },
                    valueRange = 0f..MAX_MILEAGE,
                    steps = (MAX_MILEAGE / 5_000).toInt() - 1,
                )
            }

            Section(stringResource(R.string.spec_fuel)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FUEL_CHOICES.forEach { fuel ->
                        FilterChip(
                            selected = fuel in draft.fuelTypes,
                            onClick = {
                                draft = draft.copy(
                                    fuelTypes = draft.fuelTypes.toMutableSet().apply {
                                        if (!add(fuel)) remove(fuel)
                                    },
                                )
                            },
                            label = { Text(fuel.label()) },
                        )
                    }
                }
            }

            Section(stringResource(R.string.filter_region)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Region.entries.forEach { region ->
                        FilterChip(
                            selected = region in draft.regions,
                            onClick = {
                                val next = draft.regions.toMutableSet().apply {
                                    if (!add(region)) remove(region)
                                }
                                // never allow zero regions -> treat empty as "all"
                                draft = draft.copy(regions = if (next.isEmpty()) Region.entries.toSet() else next)
                            },
                            label = { Text(region.label()) },
                        )
                    }
                }
            }

            if (availableSources.isNotEmpty()) {
                Section(stringResource(R.string.filter_sources)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableSources.forEach { source ->
                            FilterChip(
                                enabled = source.enabled,
                                selected = source.id in draft.sourceIds,
                                onClick = {
                                    draft = draft.copy(
                                        sourceIds = draft.sourceIds.toMutableSet().apply {
                                            if (!add(source.id)) remove(source.id)
                                        },
                                    )
                                },
                                label = { Text(source.displayName) },
                            )
                        }
                    }
                }
            }

            Section(stringResource(R.string.filter_sort_by)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SortOrder.entries.forEach { sort ->
                        FilterChip(
                            selected = draft.sort == sort,
                            onClick = { draft = draft.copy(sort = sort) },
                            label = { Text(sort.label()) },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.reset)) }
                Button(
                    onClick = { onApply(draft) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.apply)) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun FuelType.label() = when (this) {
    FuelType.PETROL -> stringResource(R.string.fuel_petrol)
    FuelType.DIESEL -> stringResource(R.string.fuel_diesel)
    FuelType.HYBRID -> stringResource(R.string.fuel_hybrid)
    FuelType.PLUGIN_HYBRID -> stringResource(R.string.fuel_plugin)
    FuelType.ELECTRIC -> stringResource(R.string.fuel_electric)
    FuelType.LPG -> stringResource(R.string.fuel_lpg)
    FuelType.OTHER -> stringResource(R.string.fuel_other)
    FuelType.UNKNOWN -> stringResource(R.string.fuel_unknown)
}

@Composable
private fun Region.label() = when (this) {
    Region.POLAND -> stringResource(R.string.region_poland)
    Region.EUROPE -> stringResource(R.string.region_europe)
    Region.USA -> stringResource(R.string.region_usa)
}

@Composable
private fun SortOrder.label() = when (this) {
    SortOrder.NEWEST -> stringResource(R.string.sort_newest)
    SortOrder.PRICE_ASC -> stringResource(R.string.sort_price_up)
    SortOrder.PRICE_DESC -> stringResource(R.string.sort_price_down)
    SortOrder.MILEAGE_ASC -> stringResource(R.string.sort_mileage)
    SortOrder.YEAR_DESC -> stringResource(R.string.sort_year)
}
