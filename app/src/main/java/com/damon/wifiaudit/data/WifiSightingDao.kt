package com.damon.wifiaudit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface WifiSightingDao {

    @Insert
    suspend fun insertAll(sightings: List<WifiSighting>)

    @Query("SELECT * FROM wifi_sightings WHERE locationId = :locationId")
    suspend fun getForLocation(locationId: Long): List<WifiSighting>

    @Query("SELECT * FROM wifi_sightings WHERE bssid = :bssid ORDER BY id DESC")
    suspend fun getHistoryForBssid(bssid: String): List<WifiSighting>

    @Query("SELECT COUNT(*) FROM wifi_sightings")
    suspend fun count(): Int
}
