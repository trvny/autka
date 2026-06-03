package com.autka.data.remote.rates

import kotlinx.serialization.Serializable
import retrofit2.http.GET

/**
 * Narodowy Bank Polski public rates API (free, no API key).
 * Table A returns PLN mid-rates for major currencies.
 * Docs: https://api.nbp.pl/
 */
interface NbpApi {
    @GET("exchangerates/tables/A?format=json")
    suspend fun tableA(): List<NbpTable>
}

@Serializable
data class NbpTable(val rates: List<NbpRate>)

@Serializable
data class NbpRate(val code: String, val mid: Double)
