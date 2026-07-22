package com.autka.data.repository

import com.autka.core.model.CarOffer
import com.autka.core.model.Currency
import com.autka.core.model.FuelType
import com.autka.core.model.Money
import com.autka.core.model.Region
import com.autka.core.model.SearchFilter
import com.autka.core.model.Transmission
import com.autka.data.local.CarOfferDao
import com.autka.data.local.CarOfferEntity
import com.autka.data.remote.CarOfferSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineFirstCarOfferRepositoryTest {

    @Test
    fun `marketplace filter does not suppress backend transport`() = runTest {
        val dao = FakeDao()
        val backend = RecordingSource(
            sourceId = "backend",
            offers = listOf(offer(id = "otomoto:1", sourceId = "otomoto")),
        )
        val repository = OfflineFirstCarOfferRepository(dao, setOf(backend))
        val filter = SearchFilter(sourceIds = setOf("otomoto"))

        val failed = repository.refresh(filter)

        assertTrue(failed.isEmpty())
        assertEquals(filter, backend.receivedFilter)
        assertNotNull(dao.rows.value.singleOrNull { it.id == "otomoto:1" })
        assertNotNull(dao.lastDeleteStaleCutoff)
    }

    private class RecordingSource(
        override val sourceId: String,
        private val offers: List<CarOffer>,
    ) : CarOfferSource {
        override val displayName = sourceId
        override val isEnabled = true
        var receivedFilter: SearchFilter? = null

        override suspend fun fetch(filter: SearchFilter): List<CarOffer> {
            receivedFilter = filter
            return offers
        }
    }

    private class FakeDao : CarOfferDao {
        val rows = MutableStateFlow<List<CarOfferEntity>>(emptyList())
        var lastDeleteStaleCutoff: Long? = null

        override suspend fun upsertAll(offers: List<CarOfferEntity>) {
            val incoming = offers.associateBy { it.id }
            rows.value = (rows.value.filterNot { it.id in incoming } + offers)
        }

        override fun observeAll(): Flow<List<CarOfferEntity>> = rows

        override fun observeById(id: String): Flow<CarOfferEntity?> =
            MutableStateFlow(rows.value.firstOrNull { it.id == id })

        override suspend fun deleteStale(olderThanEpochMs: Long) {
            lastDeleteStaleCutoff = olderThanEpochMs
            rows.value = rows.value.filter { it.fetchedAtEpochMs >= olderThanEpochMs }
        }

        override suspend fun clear() {
            rows.value = emptyList()
        }
    }

    private fun offer(id: String, sourceId: String) = CarOffer(
        id = id,
        sourceId = sourceId,
        title = "Toyota Corolla",
        make = "Toyota",
        model = "Corolla",
        year = 2020,
        mileageKm = 50_000,
        price = Money(50_000.0, Currency.PLN),
        fuelType = FuelType.PETROL,
        transmission = Transmission.MANUAL,
        powerHp = null,
        location = null,
        region = Region.POLAND,
        thumbnailUrl = null,
        imageUrls = emptyList(),
        listingUrl = "https://example.test/$id",
        postedAtEpochMs = 0L,
    )
}
