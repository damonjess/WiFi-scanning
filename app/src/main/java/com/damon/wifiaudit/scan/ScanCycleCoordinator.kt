package com.damon.wifiaudit.scan

import android.net.wifi.ScanResult
import com.damon.wifiaudit.ble.BleDeviceInfo
import com.damon.wifiaudit.data.BleSighting
import com.damon.wifiaudit.data.LocationFix
import com.damon.wifiaudit.data.WardrivingRepository
import com.damon.wifiaudit.data.WifiSighting
import com.damon.wifiaudit.vendor.DeviceModelLookup
import com.damon.wifiaudit.vendor.OuiVendorLookup

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

        // Known Ring IEEE OUI prefixes
        val ringOuiPrefixes = setOf(
            "9C7613", "AC233F", "24F5AA", "CC33BB", "B4E62D",
            "18742E", "68C63A", "347911", "0050C2", "649A12"
        )

        // Target Service UUID Dictionaries
        val trackerUuids = setOf("FEED", "FE9F", "FD6F", "FEAA") 
        val smartHomeUuids = setOf("FE78", "FED7", "FED8", "FED9", "FEDA", "FEDB", "FED0")
        val cameraUuids = setOf("FECB", "FECC", "FECE")
        val autoUuids = setOf("FEF1", "FEF2", "FEF4", "FEF5")
        val iotUuids = setOf("FE68", "FE59", "FEE0")

        // 1. Intercept Wi-Fi Targets (Ring Setup Beacons)
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
                    deviceName = if (ssid.isNotBlank()) "Ring ($ssid)" else "Ring Camera",
                    category = "CAMERA",
                    rssi = r.level,
                    latitude = latitude,
                    longitude = longitude
                )
            }
        }

        // 2. Intercept Multi-Vector BLE Targets
        bleResults.forEach { d ->
            val mac = d.macAddress.uppercase()
            val cleanMac = mac.replace(":", "").replace("-", "")
            val macPrefix = if (cleanMac.length >= 6) cleanMac.substring(0, 6) else ""
            val vendorName = d.vendorName ?: d.manufacturerFromAdv ?: ""
            val name = d.deviceName ?: ""
            val nameUpper = name.uppercase()

            var detectedCategory: String? = null
            var label = name

            // VECTOR A: Apple iBeacon & Find My (AirTags / Apple Tracker Ecosystem)
            if (d.iBeaconUuid != null) {
                detectedCategory = "TRACKER"
                label = if (name.isNotBlank()) name else "Apple iBeacon"
            } else if (vendorName.contains("Apple", ignoreCase = true) && nameUpper.contains("AIRTAG")) {
                detectedCategory = "TRACKER"
                label = "Apple AirTag"
            }

            // VECTOR B1: Recognised beacon formats (Eddystone, AltBeacon, Tile, Ruuvi,
            // Xiaomi, Fast Pair, Microsoft, Samsung) — detected by BeaconDecoder in
            // the scan path. Takes priority over the generic service-UUID fallback
            // because the format is positively identified, not just inferred.
            if (detectedCategory == null && d.beaconType != null) {
                val bt = d.beaconType
                when {
                    bt == "Tile Tracker" || bt.startsWith("Eddystone") || bt == "AltBeacon" -> {
                        detectedCategory = "TRACKER"
                        label = if (name.isNotBlank()) name else "$bt beacon"
                    }
                    bt.startsWith("RuuviTag") || bt == "Xiaomi MiBeacon" ||
                        bt == "Google Fast Pair" || bt == "Microsoft Beacon" -> {
                        detectedCategory = "IOT"
                        label = if (name.isNotBlank()) name else bt
                    }
                    bt.startsWith("Samsung") -> {
                        detectedCategory = "SMART_HOME"
                        label = if (name.isNotBlank()) name else bt
                    }
                }
            }

            // VECTOR B: Tile & Samsung SmartTags
            if (detectedCategory == null) {
                if (vendorName.contains("Tile", ignoreCase = true) || nameUpper.contains("TILE")) {
                    detectedCategory = "TRACKER"
                    label = if (name.isNotBlank()) name else "Tile Tracker"
                } else if (vendorName.contains("Samsung", ignoreCase = true) && nameUpper.contains("TAG")) {
                    detectedCategory = "TRACKER"
                    label = if (name.isNotBlank()) name else "Samsung SmartTag"
                }
            }

            // VECTOR C: Automotive / Smart Cars (Names & Infotainment)
            if (detectedCategory == null) {
                val carKeywords = listOf(
                    "TESLA", "BMW", "MERCEDES", "AUDI", "VOLKSWAGEN", "VW",
                    "FORD", "SYNC", "CARPLAY", "PORSCHE", "NISSAN"
                )
                if (carKeywords.any { nameUpper.contains(it) }) {
                    detectedCategory = "AUTO"
                    label = name
                }
            }

            // VECTOR D: Smart Cameras (Ring, Wyze, Arlo)
            if (detectedCategory == null) {
                val isRing = ringOuiPrefixes.contains(macPrefix) ||
                             Regex("\\bRing\\b", RegexOption.IGNORE_CASE).containsMatchIn(vendorName) ||
                             vendorName.contains("Bot Home Automation", ignoreCase = true) ||
                             nameUpper.startsWith("RING")

                val isWyze = vendorName.contains("Wyze", ignoreCase = true) || nameUpper.contains("WYZE")
                val isArlo = vendorName.contains("Arlo", ignoreCase = true) || nameUpper.contains("ARLO")

                if (isRing) {
                    detectedCategory = "CAMERA"
                    label = if (name.isNotBlank()) name else "Ring BLE Device"
                } else if (isWyze || isArlo) {
                    detectedCategory = "CAMERA"
                    label = if (name.isNotBlank()) name else (if (isWyze) "Wyze Cam" else "Arlo Cam")
                }
            }

            // VECTOR E: IoT & Development Hardware (Espressif ESP32/ESP8266, Raspberry Pi)
            if (detectedCategory == null) {
                if (vendorName.contains("Espressif", ignoreCase = true) ||
                    nameUpper.contains("ESP32") ||
                    nameUpper.contains("ESP8266")
                ) {
                    detectedCategory = "IOT"
                    label = if (name.isNotBlank()) name else "Espressif IoT Device"
                } else if (vendorName.contains("Raspberry", ignoreCase = true)) {
                    detectedCategory = "IOT"
                    label = if (name.isNotBlank()) name else "Raspberry Pi"
                }
            }

            // VECTOR F: Fallback to Service UUID inspection
            if (detectedCategory == null) {
                for (fullUuid in d.serviceUuids) {
                    if (fullUuid.length >= 8) {
                        val shortUuid = fullUuid.substring(4, 8).uppercase()
                        when {
                            trackerUuids.contains(shortUuid) -> {
                                detectedCategory = "TRACKER"
                                label = if (name.isNotBlank()) name else "Tracking Beacon ($shortUuid)"
                            }
                            cameraUuids.contains(shortUuid) -> {
                                detectedCategory = "CAMERA"
                                label = if (name.isNotBlank()) name else "Security Camera ($shortUuid)"
                            }
                            smartHomeUuids.contains(shortUuid) -> {
                                detectedCategory = "SMART_HOME"
                                label = if (name.isNotBlank()) name else "Smart Home Device ($shortUuid)"
                            }
                            autoUuids.contains(shortUuid) -> {
                                detectedCategory = "AUTO"
                                label = if (name.isNotBlank()) name else "Vehicle Telemetry ($shortUuid)"
                            }
                            iotUuids.contains(shortUuid) -> {
                                detectedCategory = "IOT"
                                label = if (name.isNotBlank()) name else "IoT Hardware ($shortUuid)"
                            }
                        }
                        if (detectedCategory != null) break
                    }
                }
            }

            // Commit match to database
            if (detectedCategory != null) {
                repository.processAndSaveTargetDevice(
                    macAddress = mac,
                    deviceName = label.ifBlank { "Identified $detectedCategory" },
                    category = detectedCategory,
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
