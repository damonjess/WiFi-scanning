package com.damon.wifiaudit.scan

import com.damon.wifiaudit.ble.BleDeviceInfo
import com.damon.wifiaudit.data.*
import com.damon.wifiaudit.vendor.DeviceModelLookup

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
                encryption = parseEncryption(r.capabilities),
                deviceModel = DeviceModelLookup.identify(r)
            )
        }

        val bleSightings = bleResults.map { d ->
            val identifiedModel = DeviceModelLookup.identify(d)
            BleSighting(
                locationId = 0,
                macAddress = d.macAddress,
                deviceName = d.deviceName ?: identifiedModel,
                rssi = d.rssi,
                txPower = d.txPowerLevel,
                proximityUuid = d.iBeaconUuid,
                deviceModel = identifiedModel,
                scanRecord = d.rawBytes
            )
        }

        // 1. Intercept Wi-Fi Ring Cameras
        wifiResults.forEach { r ->
            val bssid = r.BSSID.uppercase()
            val ssid = r.SSID ?: ""
            val vendorName = com.damon.wifiaudit.vendor.OuiVendorLookup.lookup(bssid)
            
            if (vendorName?.contains("Ring", ignoreCase = true) == true || ssid.startsWith("Ring-", ignoreCase = true)) {
                repository.processAndSaveRingCamera(
                    macAddress = bssid,
                    deviceName = "Ring WiFi Camera",
                    ssid = ssid.ifBlank { "<hidden>" },
                    rssi = r.level,
                    frequency = r.frequency,
                    latitude = latitude,
                    longitude = longitude
                )
            }
        }

        // 2. Intercept BLE Ring Devices
        bleResults.forEach { d ->
            val mac = d.macAddress.uppercase()
            val vendorName = d.vendorName ?: d.manufacturerFromAdv
            val name = d.deviceName ?: ""
            
            if (vendorName?.contains("Ring", ignoreCase = true) == true || 
                name.contains("Ring", ignoreCase = true) || 
                d.serviceUuids.contains("0000fecb-0000-1000-8000-00805f9b34fb")) {
                
                repository.processAndSaveRingCamera(
                    macAddress = mac,
                    deviceName = name.ifBlank { "Ring BLE Device" },
                    ssid = "N/A (BLE)",
                    rssi = d.rssi,
                    frequency = 2400,
                    latitude = latitude,
                    longitude = longitude
                )
            }
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
