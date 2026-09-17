package com.damon.wifiaudit.ble

import com.damon.wifiaudit.vendor.OuiVendorLookup

/**
 * Classifies BLE devices into target categories for the Targets tab.
 * Extracted from ScanCycleCoordinator so both wardriving and live scanning
 * can share the same detection logic.
 */
object TargetClassifier {

    // Known Ring IEEE OUI prefixes
    val ringOuiPrefixes = setOf(
        "9C7613", "AC233F", "24F5AA", "CC33BB", "B4E62D",
        "18742E", "68C63A", "347911", "0050C2", "649A12"
    )

    // Target Service UUID Dictionaries
    private val trackerUuids = setOf("FEED", "FE9F", "FD6F", "FEAA")
    private val smartHomeUuids = setOf("FE78", "FED7", "FED8", "FED9", "FEDA", "FEDB", "FED0")
    private val cameraUuids = setOf("FECB", "FECC", "FECE")
    private val autoUuids = setOf("FEF1", "FEF2", "FEF4", "FEF5")
    private val iotUuids = setOf("FE68", "FE59", "FEE0")

    data class Classification(
        val category: String,
        val label: String
    )

    /**
     * Classify a BLE device into a target category.
     * Returns null if the device doesn't match any known pattern.
     */
    fun classify(d: BleDeviceInfo): Classification? {
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

        // VECTOR B1: Recognised beacon formats
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

        // VECTOR C: Automotive / Smart Cars
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

        // VECTOR E: IoT & Development Hardware
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

        // VECTOR G: Vendor OUI fallback for known camera/smart home brands
        if (detectedCategory == null && macPrefix.isNotEmpty()) {
            val ouiVendor = OuiVendorLookup.lookup(mac)
            if (ouiVendor != null) {
                val v = ouiVendor.lowercase()
                when {
                    v.contains("hikvision") || v.contains("dahua") || v.contains("reolink") ||
                    v.contains("foscam") || v.contains("amcrest") || v.contains("arlo") ||
                    v.contains("wyze") || v.contains("blink") || v.contains("eufy") ||
                    v.contains("swann") || v.contains("lorex") || v.contains("annke") ||
                    v.contains("zosi") || v.contains("vivotek") || v.contains("axis") ->
                    {
                        detectedCategory = "CAMERA"
                        label = if (name.isNotBlank()) name else "$ouiVendor Camera"
                    }
                    v.contains("ring") || v.contains("bot home automation") -> {
                        detectedCategory = "CAMERA"
                        label = if (name.isNotBlank()) name else "Ring Device"
                    }
                    v.contains("espressif") || v.contains("shenzhen") || v.contains("tuya") -> {
                        detectedCategory = "IOT"
                        label = if (name.isNotBlank()) name else "$ouiVendor IoT Device"
                    }
                }
            }
        }

        return if (detectedCategory != null) {
            Classification(detectedCategory!!, label.ifBlank { "Identified $detectedCategory" })
        } else null
    }
}
