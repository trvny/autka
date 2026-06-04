package com.autka.data.imports

import com.autka.core.model.ImportService
import com.autka.data.remote.backend.BackendApi
import com.autka.data.remote.backend.toModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first import-company directory, mirroring [ExchangeRateRepository]'s shape:
 * seed with the compiled-in [DefaultImportServices] (so the offline-first app always has
 * data, even on a cold start with no network), then [refresh] from the backend to
 * override. A failed fetch silently keeps the current list (seed or last good).
 *
 * In-memory only: the compiled seed already guarantees offline availability, so disk
 * persistence isn't needed. If you later want last-good-across-restart, persist the list
 * to DataStore here exactly as the rates repo does — no call-site change required.
 */
@Singleton
class ImportServicesRepository @Inject constructor(
    private val api: BackendApi,
) {
    private val state = MutableStateFlow(DefaultImportServices.ALL)

    /** All known import companies; filter by [ImportService.origin] at the call site. */
    fun services(): StateFlow<List<ImportService>> = state.asStateFlow()

    suspend fun refresh() {
        runCatching { api.importServices() }
            .onSuccess { resp ->
                val mapped = resp.services.map { it.toModel() }
                if (mapped.isNotEmpty()) state.value = mapped // never blank out the seed
            }
        // onFailure: keep current value (seed or last good) — offline-safe.
    }
}
