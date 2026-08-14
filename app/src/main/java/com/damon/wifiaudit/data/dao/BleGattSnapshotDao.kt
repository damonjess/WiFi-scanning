package com.damon.wifiaudit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.damon.wifiaudit.data.entity.BleGattSnapshot
import kotlinx.coroutines.flow.Flow

@Dao
interface BleGattSnapshotDao {
    @Insert
    suspend fun insert(snapshot: BleGattSnapshot)

    @Query("SELECT * FROM ble_gatt_snapshots WHERE macAddress = :mac ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestForMac(mac: String): BleGattSnapshot?

    @Query("SELECT * FROM ble_gatt_snapshots WHERE macAddress = :mac ORDER BY timestamp DESC")
    fun getHistoryForMac(mac: String): Flow<List<BleGattSnapshot>>

    @Query("SELECT DISTINCT macAddress FROM ble_gatt_snapshots")
    fun getAllMacsWithGatt(): Flow<List<String>>
}
