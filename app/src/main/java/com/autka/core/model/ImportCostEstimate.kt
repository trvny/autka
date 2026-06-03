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
    val exciseDuty: Money,    // PL akcyza -- depends on engine capacity
    val vat: Money,           // 23% on (price + shipping + duty + excise)
    val total: Money,
)

object ImportCostCalculator {
    // Indicative rates -- externalize/configure for production use.
    private const val EU_CUSTOMS_DUTY_RATE = 0.10
    private const val PL_VAT_RATE = 0.23

    /** Excise depends on engine capacity in Poland: 3.1% up to 2.0L, otherwise 18.6%. */
    fun exciseRate(engineCapacityCc: Int?): Double =
        if ((engineCapacityCc ?: 0) <= 2000) 0.031 else 0.186

    fun estimate(
        vehiclePriceUsd: Double,
        shippingUsd: Double,
        engineCapacityCc: Int?,
    ): ImportCostEstimate {
        val price = vehiclePriceUsd
        val shipping = shippingUsd
        val customs = (price + shipping) * EU_CUSTOMS_DUTY_RATE
        val excise = (price + shipping + customs) * exciseRate(engineCapacityCc)
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
