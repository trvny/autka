package com.autka.data.remote.backend

import com.autka.core.model.SearchFilter
import com.autka.core.model.SortOrder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendCarOfferSourceTest {

    @Test
    fun `fetch requests one complete snapshot before local price operations`() = runTest {
        val api = RecordingApi(total = 450)
        val source = BackendCarOfferSource(api)

        val offers = source.fetch(
            SearchFilter(
                minPrice = 50_000.0,
                maxPrice = 100_000.0,
                sort = SortOrder.PRICE_ASC,
            ),
        )

        assertEquals(450, offers.size)
        assertEquals(1, api.callCount)
        assertEquals(listOf(true), api.completeFlags)
        assertEquals(listOf(null), api.offsets)
        assertEquals(listOf(null), api.limits)
        assertTrue(api.minPrices.all { it == null })
        assertTrue(api.maxPrices.all { it == null })
        assertTrue(api.sorts.all { it == SortOrder.NEWEST.name })
    }

    private class RecordingApi(
        private val total: Int,
    ) : BackendApi {
        var callCount = 0
        val completeFlags = mutableListOf<Boolean?>()
        val offsets = mutableListOf<Int?>()
        val limits = mutableListOf<Int?>()
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
            complete: Boolean?,
            limit: Int?,
            offset: Int?,
        ): OffersResponse {
            callCount += 1
            completeFlags += complete
            offsets += offset
            limits += limit
            minPrices += minPrice
            maxPrices += maxPrice
            sorts += sort
            return OffersResponse(
                offers = (0 until total).map(::dto),
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
