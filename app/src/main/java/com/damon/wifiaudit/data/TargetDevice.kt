package com.damon.wifiaudit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "targeted_devices")
data class TargetDevice(
    @PrimaryKey
    val macAddress: String,
    val deviceName: String,
    val category: String, // Will be "TRACKER", "SMART_HOME", "AUTO", or "IOT"
    val signalStrength: Int,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null
)
