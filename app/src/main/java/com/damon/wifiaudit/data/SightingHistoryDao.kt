package com.damon.wifiaudit.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class WifiSightingRecord(
    val id: Long,
    val locationId: Long,
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val encryption: String,
    val deviceModel: String?,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val vendorName: String? = null
)

data class BleSightingRecord(
    val id: Long,
    val locationId: Long,
    val macAddress: String,
    val deviceName: String?,
    val rssi: Int,
    val txPower: Int?,
    val proximityUuid: String?,
    val deviceModel: String?,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val hasGatt: Boolean = false,
    val vendorName: String? = null,
    val primaryGattService: String? = null
)

@Dao
interface SightingHistoryDao {

    @Query("""
        SELECT w.id, l.id as locationId, w.ssid, w.bssid, w.rssi, w.frequency, w.encryption, w.deviceModel,
               l.latitude, l.longitude, l.timestamp, v.vendorName
        FROM wifi_sightings w
        INNER JOIN location_fixes l ON w.locationId = l.id
        LEFT JOIN oui_vendors v ON v.oui = SUBSTR(REPLACE(REPLACE(REPLACE(UPPER(w.bssid), ':', ''), '-', ''), '.', ''), 1, 6)
        ORDER BY l.timestamp DESC
    """)
    suspend fun getWifiHistory(): List<WifiSightingRecord>

    @Query("""
        SELECT b.id, l.id as locationId, b.macAddress, b.deviceName, b.rssi, b.txPower, b.proximityUuid, b.deviceModel,
               l.latitude, l.longitude, l.timestamp, v.vendorName,
               ((SELECT COUNT(*) FROM ble_gatt_snapshots WHERE macAddress = b.macAddress) > 0) as hasGatt
        FROM ble_sightings b
        INNER JOIN location_fixes l ON b.locationId = l.id
        LEFT JOIN oui_vendors v ON v.oui = SUBSTR(REPLACE(REPLACE(REPLACE(UPPER(b.macAddress), ':', ''), '-', ''), '.', ''), 1, 6)
        ORDER BY l.timestamp DESC
    """)
    suspend fun getBleHistory(): List<BleSightingRecord>

    @Query("""
        SELECT w.id, l.id as locationId, w.ssid, w.bssid, w.rssi, w.frequency, w.encryption, w.deviceModel,
               l.latitude, l.longitude, MAX(l.timestamp) as timestamp, v.vendorName
        FROM wifi_sightings w
        INNER JOIN location_fixes l ON w.locationId = l.id
        LEFT JOIN oui_vendors v ON v.oui = SUBSTR(REPLACE(REPLACE(REPLACE(UPPER(w.bssid), ':', ''), '-', ''), '.', ''), 1, 6)
        WHERE (:query = '' OR w.ssid LIKE '%' || :query || '%' OR w.bssid LIKE '%' || :query || '%' OR v.vendorName LIKE '%' || :query || '%')
        AND (:encFilter IS NULL OR w.encryption = :encFilter)
        GROUP BY w.bssid
        ORDER BY
            CASE WHEN :sortByRssi = 1 THEN MAX(w.rssi) END DESC,
            CASE WHEN :sortByRssi = 0 THEN timestamp END DESC
    """)
    fun pagedWifiHistory(
        query: String,
        encFilter: String?,
        sortByRssi: Boolean
    ): PagingSource<Int, WifiSightingRecord>

    @Query("""
        SELECT b.id, l.id as locationId, b.macAddress, b.deviceName, b.rssi, b.txPower, b.proximityUuid, b.deviceModel,
               l.latitude, l.longitude, MAX(l.timestamp) as timestamp, v.vendorName,
               ((SELECT COUNT(*) FROM ble_gatt_snapshots WHERE macAddress = b.macAddress) > 0) as hasGatt
        FROM ble_sightings b
        INNER JOIN location_fixes l ON b.locationId = l.id
        LEFT JOIN oui_vendors v ON v.oui = SUBSTR(REPLACE(REPLACE(REPLACE(UPPER(b.macAddress), ':', ''), '-', ''), '.', ''), 1, 6)
        WHERE (:query = '' OR b.deviceName LIKE '%' || :query || '%' OR b.macAddress LIKE '%' || :query || '%' OR v.vendorName LIKE '%' || :query || '%')
        GROUP BY b.macAddress
        ORDER BY
            CASE WHEN :sortByRssi = 1 THEN MAX(b.rssi) END DESC,
            CASE WHEN :sortByRssi = 0 THEN timestamp END DESC
    """)
    fun pagedBleHistory(
        query: String,
        sortByRssi: Boolean
    ): PagingSource<Int, BleSightingRecord>

    @Query("""
        SELECT w.id, l.id as locationId, w.ssid, w.bssid, w.rssi, w.frequency, w.encryption, w.deviceModel,
               l.latitude, l.longitude, l.timestamp, v.vendorName
        FROM wifi_sightings w
        INNER JOIN location_fixes l ON w.locationId = l.id
        LEFT JOIN oui_vendors v ON v.oui = SUBSTR(REPLACE(REPLACE(REPLACE(UPPER(w.bssid), ':', ''), '-', ''), '.', ''), 1, 6)
        ORDER BY l.timestamp DESC
    """)
    fun getAllWifiSightings(): Flow<List<WifiSightingRecord>>

    @Query("""
        SELECT b.id, l.id as locationId, b.macAddress, b.deviceName, b.rssi, b.txPower, b.proximityUuid, b.deviceModel,
               l.latitude, l.longitude, l.timestamp, v.vendorName,
               ((SELECT COUNT(*) FROM ble_gatt_snapshots WHERE macAddress = b.macAddress) > 0) as hasGatt
        FROM ble_sightings b
        INNER JOIN location_fixes l ON b.locationId = l.id
        LEFT JOIN oui_vendors v ON v.oui = SUBSTR(REPLACE(REPLACE(REPLACE(UPPER(b.macAddress), ':', ''), '-', ''), '.', ''), 1, 6)
        ORDER BY l.timestamp DESC
    """)
    fun getAllBleSightings(): Flow<List<BleSightingRecord>>

    @Query("SELECT DISTINCT encryption FROM wifi_sightings ORDER BY encryption")

    suspend fun getDistinctEncryptionTypes(): List<String>
}
