package com.autka.data.repository

import com.autka.core.model.CarOffer
import com.autka.core.model.SearchFilter
import com.autka.data.local.CarOfferDao
import com.autka.data.local.toEntity
import com.autka.data.local.toModel
import com.autka.data.remote.CarOfferSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineFirstCarOfferRepository @Inject constructor(
    private val dao: CarOfferDao,
    private val sources: Set<@JvmSuppressWildcards CarOfferSource>,
) : CarOfferRepository {

    override fun observeOffers(): Flow<List<CarOffer>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observeOffer(id: String): Flow<CarOffer?> =
        dao.observeById(id).map { it?.toModel() }

    override suspend fun refresh(filter: SearchFilter): List<String> = coroutineScope {
        val active = sources.filter { source ->
            source.isEnabled &&
                (filter.sourceIds.isEmpty() || source.sourceId in filter.sourceIds)
        }
        // Each coroutine returns its own (source, result) pair, so there is no shared
        // mutable state across the concurrent fetches.
        val outcomes = active.map { source ->
            async { source to runCatching { source.fetch(filter) } }
        }.awaitAll()

        val results = outcomes.mapNotNull { (_, result) -> result.getOrNull() }.flatten()
        if (results.isNotEmpty()) {
            dao.upsertAll(results.map { it.toEntity() })
        }
        outcomes.filter { it.second.isFailure }.map { it.first.sourceId }
    }

    override fun availableSources(): List<SourceInfo> =
        sources.map { SourceInfo(it.sourceId, it.displayName, it.isEnabled) }
            .sortedBy { it.displayName }
}
