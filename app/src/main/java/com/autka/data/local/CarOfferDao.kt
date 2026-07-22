package com.autka.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CarOfferDao {

    @Upsert
    suspend fun upsertAll(offers: List<CarOfferEntity>)

    @Query("SELECT * FROM car_offers ORDER BY postedAtEpochMs DESC")
    fun observeAll(): Flow<List<CarOfferEntity>>

    @Query("SELECT * FROM car_offers WHERE id = :id")
    fun observeById(id: String): Flow<CarOfferEntity?>

    @Query("DELETE FROM car_offers WHERE sourceId IN (:sourceIds)")
    suspend fun deleteBySourceIds(sourceIds: List<String>)

    @Query("DELETE FROM car_offers WHERE fetchedAtEpochMs < :olderThanEpochMs")
    suspend fun deleteStale(olderThanEpochMs: Long)

    @Query("DELETE FROM car_offers")
    suspend fun clear()
}
