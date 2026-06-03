package com.autka.core.model

/**
 * A snapshot of exchange rates expressed as "units of [base] per 1 unit of currency".
 * [base] itself maps to 1.0. Conversion between any two listed currencies routes
 * through the base, so a PLN-based snapshot can still convert USD -> EUR.
 *
 * This is a pure value object -- no Android or network dependencies -- so the
 * conversion math is trivially unit-testable.
 */
data class ExchangeRates(
    val base: Currency,
    val perUnit: Map<Currency, Double>,
    val asOfEpochMs: Long,
    /** true when these are the built-in fallback rates rather than a live fetch. */
    val isStale: Boolean,
) {
    /** Convert [money] into [target]. Returns the input unchanged if a rate is missing. */
    fun convert(money: Money, target: Currency): Money {
        if (money.currency == target) return money
        val fromRate = perUnit[money.currency] ?: return money
        val toRate = perUnit[target] ?: return money
        val amountInBase = money.amount * fromRate
        return Money(amountInBase / toRate, target)
    }
}
