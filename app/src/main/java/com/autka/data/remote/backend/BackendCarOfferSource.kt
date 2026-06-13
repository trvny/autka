package com.autka.data.remote.backend

import com.autka.core.model.CarOffer
import com.autka.core.model.SearchFilter
import com.autka.data.remote.CarOfferSource
import com.autka.data.remote.SourceId
import javax.inject.Inject

/**
 * Single source that delegates aggregation to the backend. Replaces the per-marketplace
 * client adapters: the backend merges Otomoto/OLX/US-auction/... server-side, so the app
 * no longer holds marketplace credentials or scraping logic.
 */
class BackendCarOfferSource @Inject constructor(
    private val api: BackendApi,
) : CarOfferSource {
    override val sourceId = SourceId.BACKEND
    override val displayName = "Autka backend"
    override val isEnabled = true

    override suspend fun fetch(filter: SearchFilter): List<CarOffer> {
        val resp = api.offers(
            query = filter.query.ifBlank { null },
            make = filter.make,
            model = filter.model,
            minPrice = filter.minPrice,
            maxPrice = filter.maxPrice,
            minYear = filter.minYear,
            maxYear = filter.maxYear,
            maxMileageKm = filter.maxMileageKm,
            fuelTypes = filter.fuelTypes.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name },
            transmissions = filter.transmissions.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name },
            regions = filter.regions.joinToString(",") { it.name },
            sources = filter.sourceIds.takeIf { it.isNotEmpty() }?.joinToString(","),
            sort = filter.sort.name,
        )
        return resp.offers.map { it.toModel() }
    }
}
