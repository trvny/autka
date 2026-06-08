package com.autka.core.model

/**
 * Rough landed-cost breakdown for importing a US vehicle into Poland.
 * These are ESTIMATES for comparison only -- real figures depend on the
 * customs valuation, the specific vehicle, and current rates.
 */
data class ImportCostEstimate(
    val vehiclePrice: Money,
    val shipping: Money,
    val customsDuty: Money,   // EU passenger-car duty (commonly 10%)
    val exciseDuty: Money,    // PL akcyza -- depends on engine capacity and drivetrain
    val vat: Money,           // 23% on (price + shipping + duty + excise)
    val total: Money,
)

object ImportCostCalculator {
    // Indicative rates -- externalize/configure for production use.
    private const val EU_CUSTOMS_DUTY_RATE = 0.10
    private const val PL_VAT_RATE = 0.23

    /**
     * Polish excise (akcyza) as a fraction of the dutiable value. Depends on BOTH engine
     * capacity and drivetrain (2026 rates, ustawa o podatku akcyzowym art. 105/109a):
     *   - Petrol/diesel/LPG:       3.1% up to 2.0L, else 18.6%
     *   - Full hybrid (HEV/MHEV):  1.55% up to 2.0L, 9.3% for 2.0-3.5L, else 18.6%
     *   - Plug-in hybrid (PHEV):   0% up to 2.0L (exempt), 9.3% for 2.0-3.5L, else 18.6%
     *   - Electric (BEV)/hydrogen: 0% (exempt through 2029)
     * Unknown capacity is treated as <=2.0L (lowest-rate, optimistic -- matches prior
     * behaviour); the detail screen lets the user enter the real cc.
     */
    fun exciseRate(engineCapacityCc: Int?, fuelType: FuelType = FuelType.UNKNOWN): Double {
        val cc = engineCapacityCc ?: 0
        return when (fuelType) {
            FuelType.ELECTRIC -> 0.0
            FuelType.PLUGIN_HYBRID -> when {
                cc <= 2000 -> 0.0
                cc <= 3500 -> 0.093
                else -> 0.186
            }
            FuelType.HYBRID -> when {
                cc <= 2000 -> 0.0155
                cc <= 3500 -> 0.093
                else -> 0.186
            }
            else -> if (cc <= 2000) 0.031 else 0.186
        }
    }

    fun estimate(
        vehiclePriceUsd: Double,
        shippingUsd: Double,
        engineCapacityCc: Int?,
        fuelType: FuelType = FuelType.UNKNOWN,
    ): ImportCostEstimate {
        val price = vehiclePriceUsd
        val shipping = shippingUsd
        val customs = (price + shipping) * EU_CUSTOMS_DUTY_RATE
        val excise = (price + shipping + customs) * exciseRate(engineCapacityCc, fuelType)
        val vat = (price + shipping + customs + excise) * PL_VAT_RATE
        val total = price + shipping + customs + excise + vat
        fun usd(v: Double) = Money(v, Currency.USD)
        return ImportCostEstimate(
            vehiclePrice = usd(price),
            shipping = usd(shipping),
            customsDuty = usd(customs),
            exciseDuty = usd(excise),
            vat = usd(vat),
            total = usd(total),
        )
    }
}
