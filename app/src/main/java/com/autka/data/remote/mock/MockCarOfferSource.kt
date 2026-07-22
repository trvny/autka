package com.autka.data.remote.mock

import com.autka.BuildConfig
import com.autka.core.model.CarOffer
import com.autka.core.model.Currency
import com.autka.core.model.FuelType
import com.autka.core.model.ImportCostCalculator
import com.autka.core.model.Money
import com.autka.core.model.Region
import com.autka.core.model.SearchFilter
import com.autka.core.model.Transmission
import com.autka.data.remote.CarOfferSource
import com.autka.data.remote.SourceId
import kotlinx.coroutines.delay
import javax.inject.Inject

/** Debug-only sample source so the app can run end-to-end with zero configuration. */
class MockCarOfferSource @Inject constructor() : CarOfferSource {
    override val sourceId = SourceId.MOCK
    override val displayName = "Sample data"
    override val isEnabled = BuildConfig.ENABLE_MOCK_SOURCE

    override suspend fun fetch(filter: SearchFilter): List<CarOffer> {
        if (!isEnabled || (filter.sourceIds.isNotEmpty() && sourceId !in filter.sourceIds)) {
            return emptyList()
        }
        delay(400) // simulate network latency

        // Return the complete tiny snapshot. The shared client-side filter performs all
        // comparisons, including mixed-currency price filtering, consistently.
        return sample
    }

    private val sample: List<CarOffer> = listOf(
        CarOffer(
            id = "mock:1", sourceId = sourceId,
            title = "BMW 320d 2018 Touring", make = "BMW", model = "320d",
            year = 2018, mileageKm = 142_000,
            price = Money(78_900.0, Currency.PLN),
            fuelType = FuelType.DIESEL, transmission = Transmission.AUTOMATIC, powerHp = 190,
            location = "Krakow, PL", region = Region.POLAND,
            thumbnailUrl = "https://picsum.photos/seed/mock1/640/480", imageUrls = listOf("https://picsum.photos/seed/mock1/640/480", "https://picsum.photos/seed/mock1b/640/480"),
            listingUrl = "https://example.com/listing/1", postedAtEpochMs = nowMinusHours(3),
            latitude = 50.0647, longitude = 19.9450,
        ),
        CarOffer(
            id = "mock:2", sourceId = sourceId,
            title = "Audi A4 2.0 TFSI 2019", make = "Audi", model = "A4",
            year = 2019, mileageKm = 98_000,
            price = Money(19_500.0, Currency.EUR),
            fuelType = FuelType.PETROL, transmission = Transmission.AUTOMATIC, powerHp = 190,
            location = "Berlin, DE", region = Region.EUROPE,
            thumbnailUrl = "https://picsum.photos/seed/mock2/640/480", imageUrls = listOf("https://picsum.photos/seed/mock2/640/480", "https://picsum.photos/seed/mock2b/640/480"),
            listingUrl = "https://example.com/listing/2", postedAtEpochMs = nowMinusHours(20),
            latitude = 52.5200, longitude = 13.4050,
        ),
        CarOffer(
            id = "mock:3", sourceId = sourceId,
            title = "Ford Mustang GT 5.0 2020 (salvage)", make = "Ford", model = "Mustang",
            year = 2020, mileageKm = 35_000,
            price = Money(18_000.0, Currency.USD),
            fuelType = FuelType.PETROL, transmission = Transmission.AUTOMATIC, powerHp = 460,
            location = "Newark, NJ, USA", region = Region.USA,
            thumbnailUrl = "https://picsum.photos/seed/mock3/640/480", imageUrls = listOf("https://picsum.photos/seed/mock3/640/480", "https://picsum.photos/seed/mock3b/640/480"),
            listingUrl = "https://example.com/listing/3", postedAtEpochMs = nowMinusHours(50),
            latitude = 40.7357, longitude = -74.1724,
            importEstimate = ImportCostCalculator.estimate(
                vehiclePriceUsd = 18_000.0, shippingUsd = 2_400.0, engineCapacityCc = 5000,
            ),
        ),
    )

    private fun nowMinusHours(h: Int) = System.currentTimeMillis() - h * 3_600_000L
}
