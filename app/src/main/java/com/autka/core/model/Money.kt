package com.autka.core.model

/** A monetary amount with an explicit currency. All prices in the app carry one. */
data class Money(
    val amount: Double,
    val currency: Currency,
)

enum class Currency(val symbol: String) {
    PLN("zł"), EUR("€"), USD("$");
}
