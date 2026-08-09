package com.damon.wifiaudit.data

import androidx.room.*

@Dao
interface LocationFixDao {

    @Insert
    suspend fun insert(location: LocationFix): Long

    @Delete
    suspend fun delete(location: LocationFix)

    @Query("DELETE FROM location_fixes WHERE id = :locationId")
    suspend fun deleteById(locationId: Long)

    @Query("SELECT * FROM location_fixes ORDER BY timestamp ASC")
    suspend fun getAll(): List<LocationFix>

    @Transaction
    @Query("SELECT * FROM location_fixes ORDER BY timestamp ASC")
    suspend fun getAllWithSightings(): List<LocationWithSightings>

    @Transaction
    @Query("SELECT * FROM location_fixes WHERE id = :locationId")
    suspend fun getWithSightings(locationId: Long): LocationWithSightings?

    @Query("SELECT COUNT(*) FROM location_fixes")
    suspend fun count(): Int
}
