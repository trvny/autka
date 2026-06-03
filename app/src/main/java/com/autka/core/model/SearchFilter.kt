package com.autka.core.model

/** User-driven query passed down to every active source adapter. */
data class SearchFilter(
    val query: String = "",
    val make: String? = null,
    val model: String? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val minYear: Int? = null,
    val maxYear: Int? = null,
    val maxMileageKm: Int? = null,
    val fuelTypes: Set<FuelType> = emptySet(),
    val regions: Set<Region> = Region.entries.toSet(),
    val sourceIds: Set<String> = emptySet(), // empty = all enabled sources
    val sort: SortOrder = SortOrder.NEWEST,
)

enum class SortOrder { NEWEST, PRICE_ASC, PRICE_DESC, MILEAGE_ASC, YEAR_DESC }
