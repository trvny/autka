package com.autka.data.repository

import com.autka.core.model.CarOffer
import com.autka.core.model.SearchFilter
import kotlinx.coroutines.flow.Flow

interface CarOfferRepository {
    /** Local cache is the source of truth the UI observes. */
    fun observeOffers(): Flow<List<CarOffer>>

    fun observeOffer(id: String): Flow<CarOffer?>

    /**
     * Fans [filter] out to every enabled transport in parallel, merges, and caches.
     * Marketplace filtering is handled by each transport and by the local UI cache;
     * transport ids such as "backend" must never be confused with offer source ids.
     * Returns the list of transport ids that failed.
     */
    suspend fun refresh(filter: SearchFilter): List<String>
}

data class SourceInfo(val id: String, val displayName: String, val enabled: Boolean)
