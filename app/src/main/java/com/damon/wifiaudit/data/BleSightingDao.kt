package com.damon.wifiaudit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface BleSightingDao {

    @Insert
    suspend fun insertAll(sightings: List<BleSighting>)

    @Query("SELECT * FROM ble_sightings WHERE locationId = :locationId")
    suspend fun getForLocation(locationId: Long): List<BleSighting>

    @Query("SELECT * FROM ble_sightings WHERE macAddress = :macAddress ORDER BY id DESC")
    suspend fun getHistoryForMac(macAddress: String): List<BleSighting>

    @Query("SELECT COUNT(*) FROM ble_sightings")
    suspend fun count(): Int
}
