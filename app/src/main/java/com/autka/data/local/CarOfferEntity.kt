package com.autka.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.autka.core.model.CarOffer
import com.autka.core.model.Currency
import com.autka.core.model.FuelType
import com.autka.core.model.Money
import com.autka.core.model.Region
import com.autka.core.model.Transmission

@Entity(tableName = "car_offers")
data class CarOfferEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val title: String,
    val make: String,
    val model: String,
    val year: Int?,
    val mileageKm: Int?,
    val priceAmount: Double,
    val priceCurrency: String,
    val fuelType: String,
    val transmission: String,
    val powerHp: Int?,
    val location: String?,
    val region: String,
    val thumbnailUrl: String?,
    val imageUrls: String,        // ";"-joined; small payloads, no need for a relation
    val listingUrl: String,
    val postedAtEpochMs: Long?,
    val importTotalAmount: Double?,
    val importTotalCurrency: String?,
    val fetchedAtEpochMs: Long,
)

fun CarOffer.toEntity(fetchedAt: Long = System.currentTimeMillis()) = CarOfferEntity(
    id = id, sourceId = sourceId, title = title, make = make, model = model,
    year = year, mileageKm = mileageKm,
    priceAmount = price.amount, priceCurrency = price.currency.name,
    fuelType = fuelType.name, transmission = transmission.name, powerHp = powerHp,
    location = location, region = region.name,
    thumbnailUrl = thumbnailUrl, imageUrls = imageUrls.joinToString(";"),
    listingUrl = listingUrl, postedAtEpochMs = postedAtEpochMs,
    importTotalAmount = importEstimate?.total?.amount,
    importTotalCurrency = importEstimate?.total?.currency?.name,
    fetchedAtEpochMs = fetchedAt,
)

fun CarOfferEntity.toModel() = CarOffer(
    id = id, sourceId = sourceId, title = title, make = make, model = model,
    year = year, mileageKm = mileageKm,
    price = Money(priceAmount, Currency.valueOf(priceCurrency)),
    fuelType = runCatching { FuelType.valueOf(fuelType) }.getOrDefault(FuelType.UNKNOWN),
    transmission = runCatching { Transmission.valueOf(transmission) }.getOrDefault(Transmission.UNKNOWN),
    powerHp = powerHp,
    location = location, region = runCatching { Region.valueOf(region) }.getOrDefault(Region.EUROPE),
    thumbnailUrl = thumbnailUrl,
    imageUrls = if (imageUrls.isBlank()) emptyList() else imageUrls.split(";"),
    listingUrl = listingUrl, postedAtEpochMs = postedAtEpochMs,
    importEstimate = null, // full breakdown is recomputed on demand in detail view
)
