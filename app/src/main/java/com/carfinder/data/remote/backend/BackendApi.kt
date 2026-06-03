package com.carfinder.data.remote.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Client for the CarGate aggregation backend (Cloudflare Worker). The backend already
 * fans out to every marketplace server-side, normalizes, and caches, so the app talks
 * to this one endpoint instead of holding per-marketplace adapters + credentials.
 */
interface BackendApi {
    @GET("offers")
    suspend fun offers(
        @Query("query") query: String? = null,
        @Query("make") make: String? = null,
        @Query("model") model: String? = null,
        @Query("minPrice") minPrice: Double? = null,
        @Query("maxPrice") maxPrice: Double? = null,
        @Query("minYear") minYear: Int? = null,
        @Query("maxYear") maxYear: Int? = null,
        @Query("maxMileageKm") maxMileageKm: Int? = null,
        @Query("fuelTypes") fuelTypes: String? = null, // comma-joined
        @Query("regions") regions: String? = null,     // comma-joined
        @Query("sources") sources: String? = null,     // comma-joined
        @Query("sort") sort: String? = null,
        @Query("limit") limit: Int? = null,
    ): OffersResponse
}

@Serializable
data class OffersResponse(val offers: List<OfferDto>, val count: Int)

@Serializable
data class OfferDto(
    val id: String,
    val sourceId: String,
    val title: String,
    val make: String,
    val model: String,
    val year: Int? = null,
    val mileageKm: Int? = null,
    val price: MoneyDto,
    val fuelType: String,
    val transmission: String,
    val powerHp: Int? = null,
    val location: String? = null,
    val region: String,
    val thumbnailUrl: String? = null,
    val imageUrls: List<String> = emptyList(),
    val listingUrl: String,
    val postedAtEpochMs: Long? = null,
    val listingCount: Int? = null,
    val otherSources: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class MoneyDto(val amount: Double, val currency: String)
