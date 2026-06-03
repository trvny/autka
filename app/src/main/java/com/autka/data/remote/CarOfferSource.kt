package com.autka.data.remote

import com.autka.core.model.CarOffer
import com.autka.core.model.SearchFilter

/**
 * The contract every source plugs into. Add a new marketplace by implementing this
 * and binding it into the multibinding Set in [com.autka.di.SourcesModule].
 * The repository fans a query out to all enabled sources and merges the results.
 */
interface CarOfferSource {
    /** Stable id, see [SourceId]. */
    val sourceId: String

    /** Human-readable label for UI source toggles. */
    val displayName: String

    /** Whether this source can serve requests right now (e.g. has credentials configured). */
    val isEnabled: Boolean

    /**
     * Fetch offers matching [filter]. Implementations should map their raw payload
     * into the normalized [CarOffer] and must not throw for empty results -- return
     * an empty list instead. Network/parse errors may throw; the repository isolates
     * a failing source so the rest still return.
     */
    suspend fun fetch(filter: SearchFilter): List<CarOffer>
}
