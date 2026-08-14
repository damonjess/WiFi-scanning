package com.damon.wifiaudit.data

import androidx.room.*

@Dao
interface BleGattDao {
    @Insert
    suspend fun insertService(service: BleGattService): Long

    @Insert
    suspend fun insertCharacteristics(chars: List<BleGattCharacteristic>)

    @Query("SELECT * FROM ble_gatt_services WHERE deviceMac = :mac ORDER BY timestamp DESC")
    suspend fun getServicesForDevice(mac: String): List<BleGattService>

    @Query("SELECT * FROM ble_gatt_characteristics WHERE serviceId = :serviceId")
    suspend fun getCharacteristicsForService(serviceId: Long): List<BleGattCharacteristic>

    @Query("DELETE FROM ble_gatt_services WHERE deviceMac = :mac")
    suspend fun clearServicesForDevice(mac: String)

    @Insert
    suspend fun insertRawFragment(fragment: BleRawFragment)

    @Query("SELECT * FROM ble_raw_fragments WHERE deviceMac = :mac ORDER BY timestamp DESC LIMIT 50")
    suspend fun getRawFragmentsForDevice(mac: String): List<BleRawFragment>

    @Query("SELECT COUNT(*) FROM ble_raw_fragments WHERE deviceMac = :mac")
    suspend fun getRawFragmentCount(mac: String): Int
}
