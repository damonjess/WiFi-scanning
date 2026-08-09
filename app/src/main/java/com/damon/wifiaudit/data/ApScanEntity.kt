package com.damon.wifiaudit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ap_scans")
data class ApScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val encryptionType: String,   // parsed from ScanResult.capabilities
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val gpsAccuracyMeters: Float,
    val timestampMillis: Long
)
