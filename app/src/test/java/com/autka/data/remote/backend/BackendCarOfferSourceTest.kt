package com.autka.data.remote.backend

import com.autka.core.model.SearchFilter
import com.autka.core.model.SortOrder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendCarOfferSourceTest {

    @Test
    fun `fetch loads every page before local price operations`() = runTest {
        val api = PagingApi(total = 450)
        val source = BackendCarOfferSource(api)

        val offers = source.fetch(
            SearchFilter(
                minPrice = 50_000.0,
                maxPrice = 100_000.0,
                sort = SortOrder.PRICE_ASC,
            ),
        )

        assertEquals(450, offers.size)
        assertEquals(listOf(0, 200, 400), api.offsets)
        assertTrue(api.minPrices.all { it == null })
        assertTrue(api.maxPrices.all { it == null })
        assertTrue(api.sorts.all { it == SortOrder.NEWEST.name })
    }

    private class PagingApi(
        private val total: Int,
    ) : BackendApi {
        val offsets = mutableListOf<Int>()
        val minPrices = mutableListOf<Double?>()
        val maxPrices = mutableListOf<Double?>()
        val sorts = mutableListOf<String?>()

        override suspend fun offers(
            query: String?,
            make: String?,
            model: String?,
            minPrice: Double?,
            maxPrice: Double?,
            minYear: Int?,
            maxYear: Int?,
            maxMileageKm: Int?,
            fuelTypes: String?,
            transmissions: String?,
            regions: String?,
            sources: String?,
            sort: String?,
            limit: Int?,
            offset: Int?,
        ): OffersResponse {
            val start = offset ?: 0
            val pageSize = limit ?: 50
            val end = minOf(start + pageSize, total)
            offsets += start
            minPrices += minPrice
            maxPrices += maxPrice
            sorts += sort
            return OffersResponse(
                offers = (start until end).map(::dto),
                count = total,
            )
        }

        override suspend fun importServices(region: String?): ImportServicesResponse =
            ImportServicesResponse()
    }

    private companion object {
        fun dto(index: Int) = OfferDto(
            id = "test:$index",
            sourceId = "test",
            title = "Test car $index",
            make = "Test",
            model = "Car",
            year = 2020,
            mileageKm = index,
            price = MoneyDto(index.toDouble(), "PLN"),
            fuelType = "PETROL",
            transmission = "MANUAL",
            listingUrl = "https://example.test/$index",
            region = "POLAND",
        )
    }
}
