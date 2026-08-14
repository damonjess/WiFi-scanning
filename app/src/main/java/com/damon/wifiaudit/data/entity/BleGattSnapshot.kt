package com.damon.wifiaudit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ble_gatt_snapshots")
data class BleGattSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val macAddress: String,
    val deviceName: String?,
    val servicesJson: String,        // JSON array of services + characteristics
    val serviceCount: Int,
    val characteristicCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)
