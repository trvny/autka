package com.autka.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the landed-cost math down. These figures are the product's whole reason to
 * exist, so the chain (duty -> excise on top of duty -> VAT on top of both) and the
 * engine-capacity excise threshold must not drift silently.
 */
class ImportCostCalculatorTest {

    private val delta = 0.001

    @Test
    fun `excise rate is 3_1 percent up to and including 2_0L`() {
        assertEquals(0.031, ImportCostCalculator.exciseRate(1600), delta)
        assertEquals(0.031, ImportCostCalculator.exciseRate(2000), delta) // boundary, inclusive
    }

    @Test
    fun `excise rate jumps to 18_6 percent above 2_0L`() {
        assertEquals(0.186, ImportCostCalculator.exciseRate(2001), delta)
        assertEquals(0.186, ImportCostCalculator.exciseRate(3000), delta)
    }

    @Test
    fun `null engine capacity is treated as small engine`() {
        assertEquals(0.031, ImportCostCalculator.exciseRate(null), delta)
    }

    @Test
    fun `small-engine breakdown compounds duty then excise then VAT`() {
        val e = ImportCostCalculator.estimate(
            vehiclePriceUsd = 20_000.0,
            shippingUsd = 2_000.0,
            engineCapacityCc = 1_800,
        )
        // customs = (20000 + 2000) * 0.10
        assertEquals(2_200.0, e.customsDuty.amount, delta)
        // excise = (22000 + 2200) * 0.031
        assertEquals(750.2, e.exciseDuty.amount, delta)
        // vat = (22000 + 2200 + 750.2) * 0.23
        assertEquals(5_738.546, e.vat.amount, delta)
        // total = price + shipping + customs + excise + vat
        assertEquals(30_688.746, e.total.amount, delta)
    }

    @Test
    fun `large-engine breakdown uses the higher excise rate`() {
        val e = ImportCostCalculator.estimate(
            vehiclePriceUsd = 20_000.0,
            shippingUsd = 2_000.0,
            engineCapacityCc = 3_000,
        )
        assertEquals(2_200.0, e.customsDuty.amount, delta)
        // excise = 24200 * 0.186
        assertEquals(4_501.2, e.exciseDuty.amount, delta)
        // vat = (22000 + 2200 + 4501.2) * 0.23
        assertEquals(6_601.276, e.vat.amount, delta)
        assertEquals(35_302.476, e.total.amount, delta)
    }

    @Test
    fun `total equals the sum of its parts`() {
        val e = ImportCostCalculator.estimate(15_000.0, 1_500.0, 2_500)
        val sum = e.vehiclePrice.amount + e.shipping.amount + e.customsDuty.amount +
            e.exciseDuty.amount + e.vat.amount
        assertEquals(e.total.amount, sum, delta)
    }

    @Test
    fun `every component is denominated in USD`() {
        val e = ImportCostCalculator.estimate(10_000.0, 1_000.0, 1_500)
        assertTrue(
            listOf(e.vehiclePrice, e.shipping, e.customsDuty, e.exciseDuty, e.vat, e.total)
                .all { it.currency == Currency.USD },
        )
    }
}
