package com.autka.data.repository

import com.autka.core.model.CarOffer
import com.autka.core.model.SearchFilter
import kotlinx.coroutines.flow.Flow

interface CarOfferRepository {
    /** Local cache is the source of truth the UI observes. */
    fun observeOffers(): Flow<List<CarOffer>>

    fun observeOffer(id: String): Flow<CarOffer?>

    /**
     * Fans [filter] out to every enabled source in parallel, merges, and caches.
     * Returns the list of source ids that failed (so the UI can surface a partial-result hint).
     */
    suspend fun refresh(filter: SearchFilter): List<String>

    /** Source toggles for the UI (id -> display name, enabled). */
    fun availableSources(): List<SourceInfo>
}

data class SourceInfo(val id: String, val displayName: String, val enabled: Boolean)
