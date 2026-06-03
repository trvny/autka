package com.autka.data.remote.backend

import com.autka.core.model.CarOffer
import com.autka.core.model.Currency
import com.autka.core.model.FuelType
import com.autka.core.model.Money
import com.autka.core.model.Region
import com.autka.core.model.Transmission

fun OfferDto.toModel(): CarOffer = CarOffer(
    id = id, sourceId = sourceId, title = title, make = make, model = model,
    year = year, mileageKm = mileageKm,
    price = Money(price.amount, runCatching { Currency.valueOf(price.currency) }.getOrDefault(Currency.PLN)),
    fuelType = runCatching { FuelType.valueOf(fuelType) }.getOrDefault(FuelType.UNKNOWN),
    transmission = runCatching { Transmission.valueOf(transmission) }.getOrDefault(Transmission.UNKNOWN),
    powerHp = powerHp,
    location = location,
    region = runCatching { Region.valueOf(region) }.getOrDefault(Region.EUROPE),
    thumbnailUrl = thumbnailUrl, imageUrls = imageUrls,
    listingUrl = listingUrl, postedAtEpochMs = postedAtEpochMs,
    importEstimate = null, // recomputed on demand in the detail view
    listingCount = listingCount,
    latitude = latitude, longitude = longitude,
)
