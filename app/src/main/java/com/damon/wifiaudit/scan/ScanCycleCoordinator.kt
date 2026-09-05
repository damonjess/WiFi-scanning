package com.damon.wifiaudit.scan

import com.damon.wifiaudit.ble.BleDeviceInfo
import com.damon.wifiaudit.data.*
import com.damon.wifiaudit.vendor.DeviceModelLookup
import com.damon.wifiaudit.vendor.OuiVendorLookup

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
                locationId = 0,
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
                iBeaconMajor = d.iBeaconMajor,
                iBeaconMinor = d.iBeaconMinor,
                deviceModel = identifiedModel,
                scanRecord = d.rawBytes
            )
        }

        repository.recordFix(location, wifiSightings, bleSightings)

        // Known Ring IEEE OUI prefixes
        val ringOuiPrefixes = setOf("9C7613", "AC233F", "24F5AA", "CC33BB", "B4E62D", "18742E", "68C63A", "347911", "0050C2", "649A12")
        
        // Target UUID Dictionaries
        val trackerUuids = setOf("FEED", "FE9F", "FD6F", "FEAA") 
        val smartHomeUuids = setOf("FE78", "FED7", "FED8", "FED9", "FEDA", "FEDB", "FED0")
        val cameraUuids = setOf("FECB", "FECC", "FECE") // Ring, Wyze, Arlo
        val autoUuids = setOf("FEF1", "FEF2", "FEF4", "FEF5")
        val iotUuids = setOf("FE68", "FE59", "FEE0")

        // 1. Intercept Wi-Fi Targets (Ring Cameras)
        wifiResults.forEach { r ->
            val bssid = r.BSSID.uppercase()
            val cleanMac = bssid.replace(":", "").replace("-", "")
            val macPrefix = if (cleanMac.length >= 6) cleanMac.substring(0, 6) else ""
            val ssid = r.SSID ?: ""
            val vendorName = OuiVendorLookup.lookup(bssid)

            val isRingPrefix = ringOuiPrefixes.contains(macPrefix)
            val isRingVendor = vendorName != null && (
                Regex("\\bRing\\b", RegexOption.IGNORE_CASE).containsMatchIn(vendorName) ||
                vendorName.contains("Bot Home Automation", ignoreCase = true)
            )
            val isRingSsid = ssid.startsWith("Ring-", ignoreCase = true) ||
                             ssid.startsWith("RingSetup", ignoreCase = true) ||
                             ssid.contains("Ring", ignoreCase = true)

            if (isRingPrefix || isRingVendor || isRingSsid) {
                repository.processAndSaveTargetDevice(
                    macAddress = bssid,
                    deviceName = if (ssid.isNotBlank()) "Ring ($ssid)" else "Ring WiFi Camera",
                    category = "CAMERA",
                    rssi = r.level,
                    latitude = latitude,
                    longitude = longitude
                )
            }
        }

        // 2. Intercept BLE Targets
        bleResults.forEach { d ->
            val mac = d.macAddress.uppercase()
            val cleanMac = mac.replace(":", "").replace("-", "")
            val macPrefix = if (cleanMac.length >= 6) cleanMac.substring(0, 6) else ""
            val vendorName = d.vendorName ?: d.manufacturerFromAdv
            val name = d.deviceName ?: ""

            // Check if it's a Ring device via name or MAC
            val isRingPrefix = ringOuiPrefixes.contains(macPrefix)
            val isRingVendor = vendorName != null && (
                Regex("\\bRing\\b", RegexOption.IGNORE_CASE).containsMatchIn(vendorName) ||
                vendorName.contains("Bot Home Automation", ignoreCase = true)
            )
            val isRingName = name.startsWith("Ring", ignoreCase = true)

            if (isRingPrefix || isRingVendor || isRingName) {
                repository.processAndSaveTargetDevice(
                    macAddress = mac,
                    deviceName = name.ifBlank { "Ring BLE Device" },
                    category = "CAMERA",
                    rssi = d.rssi,
                    latitude = latitude,
                    longitude = longitude
                )
                return@forEach // Skip the UUID check below since we already saved it
            }

            // Check UUID dictionaries for other targets
            d.serviceUuids.forEach { fullUuid ->
                if (fullUuid.length >= 8) {
                    val shortUuid = fullUuid.substring(4, 8).uppercase()
                    var category: String? = null
                    
                    if (trackerUuids.contains(shortUuid)) category = "TRACKER"
                    else if (cameraUuids.contains(shortUuid)) category = "CAMERA"
                    else if (smartHomeUuids.contains(shortUuid)) category = "SMART_HOME"
                    else if (autoUuids.contains(shortUuid)) category = "AUTO"
                    else if (iotUuids.contains(shortUuid)) category = "IOT"

                    if (category != null) {
                        repository.processAndSaveTargetDevice(
                            macAddress = mac,
                            deviceName = name.ifBlank { "Unknown $category" },
                            category = category,
                            rssi = d.rssi,
                            latitude = latitude,
                            longitude = longitude
                        )
                    }
                }
            }
        }
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
