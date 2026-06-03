package com.autka.core.model

/**
 * The single normalized representation of a used-car listing, regardless of which
 * source it came from (OLX, Otomoto, Facebook, a US auction, ...).
 *
 * Every source adapter is responsible for mapping its raw payload into this shape.
 */
data class CarOffer(
    val id: String,                 // stable, namespaced by source: "otomoto:12345"
    val sourceId: String,           // see data.remote.SourceId
    val title: String,
    val make: String,
    val model: String,
    val year: Int?,
    val mileageKm: Int?,
    val price: Money,
    val fuelType: FuelType,
    val transmission: Transmission,
    val powerHp: Int?,
    val location: String?,          // human-readable, e.g. "Krakow, PL"
    val region: Region,
    val thumbnailUrl: String?,
    val imageUrls: List<String>,
    val listingUrl: String,         // deep link back to the original listing
    val postedAtEpochMs: Long?,
    /** Filled in for USA offers so the user sees true landed cost, null otherwise. */
    val importEstimate: ImportCostEstimate? = null,
    /** >1 when this offer represents several de-duplicated cross-source listings. */
    val listingCount: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
