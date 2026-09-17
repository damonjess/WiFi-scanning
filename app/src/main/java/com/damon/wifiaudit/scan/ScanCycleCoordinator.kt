package com.damon.wifiaudit.scan

import android.net.wifi.ScanResult
import com.damon.wifiaudit.ble.BleDeviceInfo
import com.damon.wifiaudit.data.BleSighting
import com.damon.wifiaudit.data.LocationFix
import com.damon.wifiaudit.data.WardrivingRepository
import com.damon.wifiaudit.data.WifiSighting
import com.damon.wifiaudit.vendor.DeviceModelLookup
import com.damon.wifiaudit.vendor.OuiVendorLookup

import com.damon.wifiaudit.ble.TargetClassifier

class ScanCycleCoordinator(
    val repository: WardrivingRepository
) {
    suspend fun commitCycle(
        sessionId: Long,
        latitude: Double,
        longitude: Double,
        altitude: Double,
        wifiResults: List<ScanResult>,
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

        // 1. Intercept Wi-Fi Targets (Ring Setup Beacons)
        wifiResults.forEach { r ->
            val bssid = r.BSSID.uppercase()
            val cleanMac = bssid.replace(":", "").replace("-", "")
            val macPrefix = if (cleanMac.length >= 6) cleanMac.substring(0, 6) else ""
            val ssid = r.SSID ?: ""
            val vendorName = OuiVendorLookup.lookup(bssid)

            val isRingPrefix = TargetClassifier.ringOuiPrefixes.contains(macPrefix)
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
                    deviceName = if (ssid.isNotBlank()) "Ring ($ssid)" else "Ring Camera",
                    category = "CAMERA",
                    rssi = r.level,
                    latitude = latitude,
                    longitude = longitude
                )
            }
        }

        // 2. Intercept Multi-Vector BLE Targets using shared TargetClassifier
        bleResults.forEach { d ->
            val classification = TargetClassifier.classify(d)
            if (classification != null) {
                repository.processAndSaveTargetDevice(
                    macAddress = d.macAddress.uppercase(),
                    deviceName = classification.label,
                    category = classification.category,
                    rssi = d.rssi,
                    latitude = latitude,
                    longitude = longitude
                )
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
