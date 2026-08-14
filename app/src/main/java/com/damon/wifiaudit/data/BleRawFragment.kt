package com.damon.wifiaudit.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ble_raw_fragments",
    foreignKeys = [
        ForeignKey(
            entity = TrackedDevice::class,
            parentColumns = ["macAddress"],
            childColumns = ["deviceMac"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["deviceMac"]), Index(value = ["timestamp"])]
)
data class BleRawFragment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceMac: String,
    val hexData: String,
    val rssi: Int,
    val timestamp: Long = System.currentTimeMillis()
)
