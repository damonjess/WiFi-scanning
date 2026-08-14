package com.damon.wifiaudit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracked_devices")
data class TrackedDevice(
    @PrimaryKey val macAddress: String,
    val displayName: String?,
    val vendor: String?,
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    val isFavorite: Boolean = false,
    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis(),
    val detectCount: Int = 1,
    val tags: String = "", // comma-separated
    val rawAdvData: String? = null, // hex string
    val connectionState: ConnectionState = ConnectionState.SCANNED
)

enum class DeviceType { WIFI, BLE, UNKNOWN }
enum class ConnectionState { SCANNED, CONNECTING, CONNECTED, DISCONNECTED, ERROR }
