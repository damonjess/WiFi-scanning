package com.damon.wifiaudit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BleSightingDao {

    @Insert
    suspend fun insertAll(sightings: List<BleSighting>)

    @Query("SELECT * FROM ble_sightings WHERE locationId = :locationId")
    suspend fun getForLocation(locationId: Long): List<BleSighting>

    @Query("""
        SELECT b.id, l.id as locationId, b.macAddress, b.deviceName, b.rssi, b.txPower, b.proximityUuid, b.deviceModel,
               l.latitude, l.longitude, l.timestamp,
               ((SELECT COUNT(*) FROM ble_gatt_snapshots WHERE macAddress = b.macAddress) > 0) as hasGatt
        FROM ble_sightings b
        INNER JOIN location_fixes l ON b.locationId = l.id
        WHERE b.macAddress = :mac
        ORDER BY l.timestamp DESC
    """)
    fun getHistoryForMac(mac: String): Flow<List<BleSightingRecord>>

    @Query("SELECT COUNT(*) FROM ble_sightings")
    suspend fun count(): Int
}
