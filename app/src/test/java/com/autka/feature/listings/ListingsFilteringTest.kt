package com.autka.feature.listings

import com.autka.core.model.CarOffer
import com.autka.core.model.Currency
import com.autka.core.model.ExchangeRates
import com.autka.core.model.FuelType
import com.autka.core.model.Money
import com.autka.core.model.Region
import com.autka.core.model.SearchFilter
import com.autka.core.model.SortOrder
import com.autka.core.model.Transmission
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The list reacts to filters/sorts client-side over mixed-currency offers, so the
 * load-bearing invariant is: every price comparison routes through the comparison
 * currency. These tests pin that behaviour with deliberately different currencies.
 */
class ListingsFilteringTest {

    // "PLN per 1 unit of currency": USD 1 = 4 PLN, EUR 1 = 4.3 PLN.
    private val rates = ExchangeRates(
        base = Currency.PLN,
        perUnit = mapOf(Currency.PLN to 1.0, Currency.USD to 4.0, Currency.EUR to 4.3),
        asOfEpochMs = 0L,
        isStale = false,
    )

    private fun offer(
        id: String,
        price: Double,
        currency: Currency,
        region: Region = Region.POLAND,
        fuel: FuelType = FuelType.PETROL,
        year: Int? = 2020,
        mileageKm: Int? = 50_000,
        make: String = "Toyota",
        model: String = "Corolla",
        postedAtEpochMs: Long? = 0L,
    ) = CarOffer(
        id = id,
        sourceId = "test",
        title = "$make $model",
        make = make,
        model = model,
        year = year,
        mileageKm = mileageKm,
        price = Money(price, currency),
        fuelType = fuel,
        transmission = Transmission.MANUAL,
        powerHp = null,
        location = null,
        region = region,
        thumbnailUrl = null,
        imageUrls = emptyList(),
        listingUrl = "https://example.test/$id",
        postedAtEpochMs = postedAtEpochMs,
    )

    @Test
    fun `maxPrice filter compares in the comparison currency, not the listing currency`() {
        // USD 9000 = 36000 PLN; PLN 40000 stays 40000. Cap at 38000 PLN keeps only the USD car.
        val offers = listOf(
            offer("usd", price = 9_000.0, currency = Currency.USD),
            offer("pln", price = 40_000.0, currency = Currency.PLN),
        )
        val filtered = offers.applyFilter(
            SearchFilter(maxPrice = 38_000.0),
            rates,
            comparisonCurrency = Currency.PLN,
        )
        assertEquals(listOf("usd"), filtered.map { it.id })
    }

    @Test
    fun `price ascending sort orders across currencies via conversion`() {
        // Converted to PLN: EUR 8000 = 34400, USD 9000 = 36000, PLN 30000 = 30000.
        val offers = listOf(
            offer("usd", 9_000.0, Currency.USD),
            offer("pln", 30_000.0, Currency.PLN),
            offer("eur", 8_000.0, Currency.EUR),
        )
        val sorted = offers.sortedWith(sortComparator(SortOrder.PRICE_ASC, rates, Currency.PLN))
        assertEquals(listOf("pln", "eur", "usd"), sorted.map { it.id })
    }

    @Test
    fun `region not in the filter set is excluded`() {
        val offers = listOf(
            offer("pl", 30_000.0, Currency.PLN, region = Region.POLAND),
            offer("us", 30_000.0, Currency.PLN, region = Region.USA),
        )
        val filtered = offers.applyFilter(
            SearchFilter(regions = setOf(Region.POLAND)),
            rates,
            Currency.PLN,
        )
        assertEquals(listOf("pl"), filtered.map { it.id })
    }

    @Test
    fun `empty fuel set matches all fuels but a non-empty set narrows`() {
        val offers = listOf(
            offer("petrol", 30_000.0, Currency.PLN, fuel = FuelType.PETROL),
            offer("ev", 30_000.0, Currency.PLN, fuel = FuelType.ELECTRIC),
        )
        assertEquals(2, offers.applyFilter(SearchFilter(), rates, Currency.PLN).size)
        val evOnly = offers.applyFilter(
            SearchFilter(fuelTypes = setOf(FuelType.ELECTRIC)),
            rates,
            Currency.PLN,
        )
        assertEquals(listOf("ev"), evOnly.map { it.id })
    }

    @Test
    fun `query matches against title make and model case-insensitively`() {
        val offers = listOf(
            offer("a", 30_000.0, Currency.PLN, make = "Toyota", model = "Yaris"),
            offer("b", 30_000.0, Currency.PLN, make = "Honda", model = "Civic"),
        )
        val filtered = offers.applyFilter(SearchFilter(query = "honda"), rates, Currency.PLN)
        assertEquals(listOf("b"), filtered.map { it.id })
    }

    @Test
    fun `activeCount counts each non-default facet once`() {
        val f = SearchFilter(
            make = "Toyota",
            maxPrice = 50_000.0,
            fuelTypes = setOf(FuelType.PETROL),
            sort = SortOrder.PRICE_ASC,
        )
        // make + maxPrice + fuelTypes + non-default sort = 4
        assertEquals(4, f.activeCount())
    }
}
