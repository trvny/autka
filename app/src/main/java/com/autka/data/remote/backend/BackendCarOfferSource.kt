package com.autka.data.remote.backend

import com.autka.core.model.CarOffer
import com.autka.core.model.SearchFilter
import com.autka.core.model.SortOrder
import com.autka.data.remote.CarOfferSource
import com.autka.data.remote.SourceId
import javax.inject.Inject

/**
 * Single transport that delegates marketplace aggregation to the backend. Marketplace
 * source ids remain inside [SearchFilter.sourceIds]; this adapter's own id is never used
 * as a marketplace filter.
 */
class BackendCarOfferSource @Inject constructor(
    private val api: BackendApi,
) : CarOfferSource {
    override val sourceId = SourceId.BACKEND
    override val displayName = "Autka backend"
    override val isEnabled = true

    override suspend fun fetch(filter: SearchFilter): List<CarOffer> {
        val serverSort = when (filter.sort) {
            // The backend stores mixed PLN/EUR/USD amounts. Until it has a normalized
            // price column, price filtering/sorting must stay client-side or it can drop
            // correct offers before the phone converts currencies.
            SortOrder.PRICE_ASC, SortOrder.PRICE_DESC -> SortOrder.NEWEST
            else -> filter.sort
        }

        val response = api.offers(
            query = filter.query.ifBlank { null },
            make = filter.make,
            model = filter.model,
            minPrice = null,
            maxPrice = null,
            minYear = filter.minYear,
            maxYear = filter.maxYear,
            maxMileageKm = filter.maxMileageKm,
            fuelTypes = filter.fuelTypes.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name },
            transmissions = filter.transmissions.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name },
            regions = filter.regions.joinToString(",") { it.name },
            sources = filter.sourceIds.takeIf { it.isNotEmpty() }?.joinToString(","),
            sort = serverSort.name,
            complete = true,
        )
        return response.offers.map { it.toModel() }
    }
}
