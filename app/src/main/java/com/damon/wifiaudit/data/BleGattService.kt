package com.damon.wifiaudit.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ble_gatt_services",
    foreignKeys = [
        ForeignKey(
            entity = TrackedDevice::class,
            parentColumns = ["macAddress"],
            childColumns = ["deviceMac"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["deviceMac"])]
)
data class BleGattService(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceMac: String,
    val uuid: String,
    val serviceType: Int,
    val instanceId: Int,
    val timestamp: Long = System.currentTimeMillis()
)
