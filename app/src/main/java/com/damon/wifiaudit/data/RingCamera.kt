package com.damon.wifiaudit.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "ring_cameras")
data class RingCamera(
    @PrimaryKey
    val macAddress: String,
    val deviceName: String = "Ring Camera",
    val ssid: String = "",
    val signalStrength: Int = 0,
    val frequency: Int = 0,
    val firstSeen: LocalDateTime = LocalDateTime.now(),
    val lastSeen: LocalDateTime = LocalDateTime.now(),
    val latitude: Double? = null,
    val longitude: Double? = null
)
