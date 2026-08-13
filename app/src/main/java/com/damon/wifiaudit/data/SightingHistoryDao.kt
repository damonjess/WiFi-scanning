package com.damon.wifiaudit.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query

data class WifiSightingRecord(
    val id: Long,
    val locationId: Long,
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val encryption: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

data class BleSightingRecord(
    val id: Long,
    val locationId: Long,
    val macAddress: String,
    val deviceName: String?,
    val rssi: Int,
    val txPower: Int?,
    val proximityUuid: String?,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

@Dao
interface SightingHistoryDao {

    @Query("""
        SELECT w.id, l.id as locationId, w.ssid, w.bssid, w.rssi, w.frequency, w.encryption,
               l.latitude, l.longitude, l.timestamp
        FROM wifi_sightings w
        INNER JOIN location_fixes l ON w.locationId = l.id
        ORDER BY l.timestamp DESC
    """)
    suspend fun getWifiHistory(): List<WifiSightingRecord>

    @Query("""
        SELECT b.id, l.id as locationId, b.macAddress, b.deviceName, b.rssi, b.txPower, b.proximityUuid,
               l.latitude, l.longitude, l.timestamp
        FROM ble_sightings b
        INNER JOIN location_fixes l ON b.locationId = l.id
        ORDER BY l.timestamp DESC
    """)
    suspend fun getBleHistory(): List<BleSightingRecord>

    @Query("""
        SELECT w.id, l.id as locationId, w.ssid, w.bssid, w.rssi, w.frequency, w.encryption,
               l.latitude, l.longitude, l.timestamp
        FROM wifi_sightings w
        INNER JOIN location_fixes l ON w.locationId = l.id
        WHERE (:query = '' OR w.ssid LIKE '%' || :query || '%' OR w.bssid LIKE '%' || :query || '%')
        AND (:encFilter IS NULL OR w.encryption = :encFilter)
        ORDER BY
            CASE WHEN :sortByRssi = 1 THEN w.rssi END DESC,
            CASE WHEN :sortByRssi = 0 THEN l.timestamp END DESC
    """)
    fun pagedWifiHistory(
        query: String,
        encFilter: String?,
        sortByRssi: Boolean
    ): PagingSource<Int, WifiSightingRecord>

    @Query("""
        SELECT b.id, l.id as locationId, b.macAddress, b.deviceName, b.rssi, b.txPower, b.proximityUuid,
               l.latitude, l.longitude, l.timestamp
        FROM ble_sightings b
        INNER JOIN location_fixes l ON b.locationId = l.id
        WHERE (:query = '' OR b.deviceName LIKE '%' || :query || '%' OR b.macAddress LIKE '%' || :query || '%')
        ORDER BY
            CASE WHEN :sortByRssi = 1 THEN b.rssi END DESC,
            CASE WHEN :sortByRssi = 0 THEN l.timestamp END DESC
    """)
    fun pagedBleHistory(
        query: String,
        sortByRssi: Boolean
    ): PagingSource<Int, BleSightingRecord>

    @Query("SELECT DISTINCT encryption FROM wifi_sightings ORDER BY encryption")
    suspend fun getDistinctEncryptionTypes(): List<String>
}
