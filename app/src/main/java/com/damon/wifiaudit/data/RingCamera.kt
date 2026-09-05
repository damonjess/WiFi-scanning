package com.damon.wifiaudit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ring_cameras")
data class RingCamera(
    @PrimaryKey
    val macAddress: String,
    val deviceName: String = "Ring Camera",
    val ssid: String = "",
    val signalStrength: Int = 0,
    val frequency: Int = 0,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null
)
