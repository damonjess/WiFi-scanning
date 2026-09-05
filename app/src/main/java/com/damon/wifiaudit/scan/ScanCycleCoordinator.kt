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
                deviceModel = identifiedModel,
                scanRecord = d.rawBytes
            )
        }

        repository.recordFix(location, wifiSightings, bleSightings)

        // Known IEEE OUI prefixes registered to Ring LLC and Bot Home Automation
        val ringOuiPrefixes = setOf(
            "9C7613", "AC233F", "24F5AA", "CC33BB", "B4E62D",
            "18742E", "68C63A", "347911", "0050C2", "649A12"
        )

        // 1. Intercept Wi-Fi Ring Cameras
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
                repository.processAndSaveRingCamera(
                    macAddress = bssid,
                    deviceName = if (ssid.isNotBlank()) "Ring ($ssid)" else "Ring WiFi Camera",
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
            val cleanMac = mac.replace(":", "").replace("-", "")
            val macPrefix = if (cleanMac.length >= 6) cleanMac.substring(0, 6) else ""
            val vendorName = d.vendorName ?: d.manufacturerFromAdv
            val name = d.deviceName ?: ""

            val isRingPrefix = ringOuiPrefixes.contains(macPrefix)
            val isRingVendor = vendorName != null && (
                Regex("\\bRing\\b", RegexOption.IGNORE_CASE).containsMatchIn(vendorName) ||
                vendorName.contains("Bot Home Automation", ignoreCase = true)
            )
            val isRingName = name.startsWith("Ring", ignoreCase = true)
            val hasRingUuid = d.serviceUuids.any { it.contains("fecb", ignoreCase = true) }

            if (isRingPrefix || isRingVendor || isRingName || hasRingUuid) {
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

        // UUID Dictionaries for categorization
        val trackerUuids = setOf("FEED", "FE9F", "FD6F", "FEAA") 
        val smartHomeUuids = setOf("FECC", "FECE", "FE78", "FED7", "FED8", "FED9", "FEDA", "FEDB", "FED0")
        val autoUuids = setOf("FEF1", "FEF2", "FEF4", "FEF5")
        val iotUuids = setOf("FE68", "FE59", "FEE0")

        // 3. Intercept Categorized Targets
        bleResults.forEach { d ->
            val mac = d.macAddress.uppercase()
            val name = d.deviceName ?: d.vendorName ?: "Unknown Device"

            d.serviceUuids.forEach { fullUuid ->
                // Extract standard 16-bit UUID (e.g. from 0000FEED-0000-1000-8000-...)
                if (fullUuid.length >= 8) {
                    val shortUuid = fullUuid.substring(4, 8).uppercase()
                    
                    var category: String? = null
                    
                    if (trackerUuids.contains(shortUuid)) category = "TRACKER"
                    else if (smartHomeUuids.contains(shortUuid)) category = "SMART_HOME"
                    else if (autoUuids.contains(shortUuid)) category = "AUTO"
                    else if (iotUuids.contains(shortUuid)) category = "IOT"

                    if (category != null) {
                        repository.processAndSaveTargetDevice(
                            macAddress = mac,
                            deviceName = name,
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
