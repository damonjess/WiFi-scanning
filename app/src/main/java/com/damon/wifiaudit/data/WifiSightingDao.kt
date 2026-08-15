package com.damon.wifiaudit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WifiSightingDao {

    @Insert
    suspend fun insertAll(sightings: List<WifiSighting>)

    @Query("SELECT * FROM wifi_sightings WHERE locationId = :locationId")
    suspend fun getForLocation(locationId: Long): List<WifiSighting>

    @Query("""
        SELECT w.id, l.id as locationId, w.ssid, w.bssid, w.rssi, w.frequency, w.encryption, w.deviceModel,
               l.latitude, l.longitude, l.timestamp
        FROM wifi_sightings w
        INNER JOIN location_fixes l ON w.locationId = l.id
        WHERE w.bssid = :bssid
        ORDER BY l.timestamp DESC
    """)
    fun getHistoryForBssid(bssid: String): Flow<List<WifiSightingRecord>>

    @Query("SELECT COUNT(*) FROM wifi_sightings")
    suspend fun count(): Int

    @Query("""
        SELECT w.id, l.id as locationId, w.ssid, w.bssid, w.rssi, w.frequency, w.encryption, w.deviceModel,
               l.latitude, l.longitude, l.timestamp
        FROM wifi_sightings w
        INNER JOIN location_fixes l ON w.locationId = l.id
        ORDER BY l.timestamp DESC
    """)
    fun getAllSightings(): Flow<List<WifiSightingRecord>>
}
