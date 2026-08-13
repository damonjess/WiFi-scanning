package com.damon.wifiaudit.scan

import com.damon.wifiaudit.ble.BleDeviceInfo
import com.damon.wifiaudit.data.*

/**
 * Bridges the independent WiFi + BLE scan sources into a single atomic
 * write. Call this once per "cycle" — e.g. every time a fresh GPS fix
 * arrives with whatever WiFi/BLE results are currently buffered.
 */
class ScanCycleCoordinator(
    val repository: WardrivingRepository
) {
    suspend fun commitCycle(
        sessionId: Long,
        latitude: Double,
        longitude: Double,
        altitude: Double,
        wifiResults: List<android.net.wifi.ScanResult>,
        bleResults: List<BleDeviceInfo>
    ) {
        val location = LocationFix(
            sessionId = sessionId,
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            timestamp = System.currentTimeMillis()
        )

        val wifiSightings = wifiResults.map { r ->
            WifiSighting(
                locationId = 0, // overwritten inside recordFix() with real FK
                ssid = r.SSID.ifBlank { "<hidden>" },
                bssid = r.BSSID,
                rssi = r.level,
                frequency = r.frequency,
                encryption = parseEncryption(r.capabilities)
            )
        }

        val bleSightings = bleResults.map { d ->
            BleSighting(
                locationId = 0,
                macAddress = d.macAddress,
                deviceName = d.deviceName,
                rssi = d.rssi,
                txPower = d.txPowerLevel,
                proximityUuid = d.iBeaconUuid
            )
        }

        repository.recordFix(location, wifiSightings, bleSightings)
    }

    private fun parseEncryption(capabilities: String): String = when {
        capabilities.contains("WPA3") -> "WPA3"
        capabilities.contains("WPA2") -> "WPA2"
        capabilities.contains("WPA") -> "WPA"
        capabilities.contains("WEP") -> "WEP"
        capabilities.contains("ESS") && !capabilities.contains("WPA") -> "OPEN"
        else -> "UNKNOWN"
    }
}
