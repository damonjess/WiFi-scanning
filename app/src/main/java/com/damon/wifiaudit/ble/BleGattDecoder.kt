package com.damon.wifiaudit.ble

import java.util.Locale
import java.util.UUID

object BleGattDecoder {

    data class SecurityFlag(
        val title: String,
        val severity: Severity,
        val description: String
    ) {
        enum class Severity { INFO, WARNING, CRITICAL }
    }

    data class VendorFingerprint(
        val vendorName: String,
        val icon: String,
        val description: String
    )

    /**
     * Attempts to decode raw byte arrays from standard BLE characteristics into
     * human-readable strings.
     */
    fun decodeValue(uuid: UUID, bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        val short = BleUuidResolver.shortUuid(uuid)

        return when (short) {
            "2A19" -> { // Battery Level
                val level = bytes[0].toInt() and 0xFF
                "$level%"
            }
            "2A07" -> { // Tx Power Level
                if (bytes.size >= 1) {
                    val power = bytes[0].toInt()
                    "${power} dBm"
                } else null
            }
            "2A00", "2A24", "2A25", "2A26", "2A27", "2A28", "2A29" -> {
                // Device Name, Model Number, Serial Number, Firmware, Hardware, Software, Manufacturer
                try {
                    val str = String(bytes, Charsets.UTF_8).trim { it <= ' ' || it.code == 0 }
                    if (str.isNotBlank()) str else null
                } catch (_: Exception) { null }
            }
            "2A01" -> { // Appearance
                if (bytes.size >= 2) {
                    val raw = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
                    decodeAppearance(raw)
                } else null
            }
            "2A1C", "2A6E" -> { // Temperature Measurement / Temperature Type
                decodeTemperature(bytes)
            }
            "2A6F" -> { // Humidity
                if (bytes.size >= 2) {
                    val humidity = ((bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)).toFloat() / 100f
                    String.format(Locale.US, "%.1f%%", humidity)
                } else null
            }
            "2A76" -> { // UV Index
                if (bytes.size >= 1) {
                    val uv = bytes[0].toInt() and 0xFF
                    "UV Index $uv"
                } else null
            }
            "2A37" -> { // Heart Rate Measurement
                decodeHeartRate(bytes)
            }
            "2A08" -> { // Date Time
                decodeDateTime(bytes)
            }
            "2A2B" -> { // Current Time
                decodeCurrentTime(bytes)
            }
            "2A46" -> { // Blood Pressure Measurement
                decodeBloodPressure(bytes)
            }
            "2A53" -> { // RSC Measurement
                decodeRscMeasurement(bytes)
            }
            "2A89" -> { // Weight Measurement
                decodeWeightMeasurement(bytes)
            }
            else -> {
                // Fallback: UTF-8 decoding if all characters are printable ASCII
                if (bytes.all { it in 32..126 || it == 10.toByte() || it == 13.toByte() }) {
                    val str = String(bytes, Charsets.UTF_8).trim()
                    if (str.length >= 2 && str.any { it.isLetterOrDigit() }) str else null
                } else null
            }
        }
    }

    private fun decodeAppearance(value: Int): String {
        val category = value shr 6
        return when (category) {
            0 -> "Generic / Unknown (0x${value.toString(16).uppercase()})"
            1 -> "Phone"
            2 -> "Computer"
            3 -> "Watch"
            4 -> "Clock"
            5 -> "Display"
            6 -> "Remote Control"
            7 -> "Eye-glasses"
            8 -> "Tag"
            9 -> "Keyring"
            10 -> "Media Player"
            11 -> "Barcode Scanner"
            12 -> "Thermometer"
            13 -> "Heart Rate Sensor"
            14 -> "Blood Pressure"
            15 -> "Human Interface Device (HID)"
            16 -> "Glucose Monitor"
            17 -> "Running Speed & Cadence"
            18 -> "Pulse Oximeter"
            19 -> "Weight Scale"
            else -> "Category $category (0x${value.toString(16).uppercase()})"
        }
    }

    private fun decodeTemperature(bytes: ByteArray): String? {
        if (bytes.size < 4) return null
        return try {
            val flags = bytes[0].toInt() and 0xFF
            val isFahrenheit = (flags and 0x01) != 0
            val mantissa = (bytes[1].toInt() and 0xFF) or
                    ((bytes[2].toInt() and 0xFF) shl 8) or
                    ((bytes[3].toInt() and 0xFF) shl 16)
            val signedMantissa = if ((mantissa and 0x800000) != 0) mantissa or -0x1000000 else mantissa
            val exponent = if (bytes.size >= 5) bytes[4].toInt() else 0
            val temp = signedMantissa * Math.pow(10.0, exponent.toDouble())
            val unit = if (isFahrenheit) "°F" else "°C"
            String.format(Locale.US, "%.1f %s", temp, unit)
        } catch (_: Exception) { null }
    }

    private fun decodeHeartRate(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val flags = bytes[0].toInt() and 0xFF
        val is16Bit = (flags and 0x01) != 0
        val bpm = if (is16Bit && bytes.size >= 3) {
            (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
        } else if (bytes.size >= 2) {
            bytes[1].toInt() and 0xFF
        } else {
            return null
        }

        return "$bpm BPM"
    }

    private fun decodeDateTime(bytes: ByteArray): String? {
        if (bytes.size < 7) return null
        val year = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
        val month = bytes[2].toInt() and 0xFF
        val day = bytes[3].toInt() and 0xFF
        val hour = bytes[4].toInt() and 0xFF
        val min = bytes[5].toInt() and 0xFF
        val sec = bytes[6].toInt() and 0xFF
        return String.format(Locale.US, "%04d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, min, sec)
    }

    private fun decodeCurrentTime(bytes: ByteArray): String? {
        if (bytes.size < 7) return null
        val year = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
        val month = bytes[2].toInt() and 0xFF
        val day = bytes[3].toInt() and 0xFF
        val hour = bytes[4].toInt() and 0xFF
        val min = bytes[5].toInt() and 0xFF
        val sec = bytes[6].toInt() and 0xFF
        val dayOfWeek = if (bytes.size >= 8) bytes[7].toInt() and 0xFF else 0
        val dayNames = arrayOf("Unknown", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val dayName = if (dayOfWeek in 0..7) dayNames[dayOfWeek] else "Unknown"
        return String.format(Locale.US, "%04d-%02d-%02d %02d:%02d:%02d (%s)", year, month, day, hour, min, sec, dayName)
    }

    private fun decodeBloodPressure(bytes: ByteArray): String? {
        if (bytes.size < 7) return null
        val flags = bytes[0].toInt() and 0xFF
        val isKpa = (flags and 0x01) != 0
        val sys = ((bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)).toFloat() / 1000f
        val dia = ((bytes[3].toInt() and 0xFF) or ((bytes[4].toInt() and 0xFF) shl 8)).toFloat() / 1000f
        val unit = if (isKpa) "kPa" else "mmHg"
        return String.format(Locale.US, "%.0f/%.0f %s", sys, dia, unit)
    }

    private fun decodeRscMeasurement(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val flags = bytes[0].toInt() and 0xFF
        var offset = 1
        val sb = StringBuilder()

        if (bytes.size >= offset + 2) {
            val speed = ((bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)).toFloat() / 256f
            sb.append(String.format(Locale.US, "Speed: %.2f m/s", speed))
            offset += 2
        }

        if ((flags and 0x01) != 0 && bytes.size >= offset + 1) {
            val cadence = bytes[offset].toInt() and 0xFF
            sb.append(", Cadence: $cadence rpm")
            offset += 1
        }

        if ((flags and 0x02) != 0 && bytes.size >= offset + 2) {
            val stride = ((bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)).toFloat() / 10f
            sb.append(", Stride: ${stride}cm")
        }

        return sb.toString().ifBlank { null }
    }

    private fun decodeWeightMeasurement(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val flags = bytes[0].toInt() and 0xFF
        val isImperial = (flags and 0x01) != 0
        if (bytes.size < 3) return null
        val weightRaw = ((bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)).toFloat() / 200f
        val weight = if (isImperial) weightRaw * 2.20462f else weightRaw
        val unit = if (isImperial) "lb" else "kg"
        return String.format(Locale.US, "%.1f %s", weight, unit)
    }

    /**
     * Performs automated security analysis on a single GATT characteristic.
     */
    fun assessSecurity(charUuid: UUID, serviceUuid: UUID, properties: Int): List<SecurityFlag> {
        val flags = mutableListOf<SecurityFlag>()
        val charShort = BleUuidResolver.shortUuid(charUuid)
        val svcShort = BleUuidResolver.shortUuid(serviceUuid)

        val writable = (properties and 0x08 != 0) || (properties and 0x04 != 0)
        val readable = (properties and 0x02 != 0)

        if (charShort.contains("DFU", ignoreCase = true) || svcShort == "FE59" || charShort == "8EC90001") {
            flags.add(
                SecurityFlag(
                    title = "DFU Bootloader Endpoint",
                    severity = SecurityFlag.Severity.CRITICAL,
                    description = "Exposes direct Device Firmware Update / bootloader access."
                )
            )
        }

        if (svcShort == "1812") {
            flags.add(
                SecurityFlag(
                    title = "HID Input Endpoint",
                    severity = SecurityFlag.Severity.WARNING,
                    description = "Human Interface Device protocol — potential wireless keylogger or keystroke injector."
                )
            )
        }

        if (writable && (properties and 0x04 != 0)) {
            flags.add(
                SecurityFlag(
                    title = "Unauthenticated Write",
                    severity = SecurityFlag.Severity.WARNING,
                    description = "Accepts raw write commands without requiring connection response or pairing confirmation."
                )
            )
        }

        if (readable && (charShort == "2A25" || charShort == "2A23")) {
            flags.add(
                SecurityFlag(
                    title = "Sensitive Asset Identifier",
                    severity = SecurityFlag.Severity.INFO,
                    description = "Broadcasts unencrypted serial/hardware ID for tracking or fingerprinting."
                )
            )
        }

        return flags
    }

    /**
     * Identifies vendor hardware/ecosystem fingerprints from discovered GATT services.
     */
    fun identifyVendorFingerprint(services: List<LightGattManager.BleService>): VendorFingerprint? {
        val uuids = services.map { BleUuidResolver.shortUuid(it.uuid) }.toSet()

        return when {
            uuids.contains("FEF3") -> VendorFingerprint("Google Fast Pair", "📱", "Supports Google Fast Pair automated BLE proximity pairing.")
            uuids.contains("FD43") || uuids.contains("FD44") || uuids.contains("FD4D") -> VendorFingerprint("Apple HomeKit", "🏠", "Apple HomeKit smart home accessory protocol.")
            uuids.contains("FEED") -> VendorFingerprint("Tile Tracker", "🔷", "Tile asset tracker beacon service.")
            uuids.contains("FE59") -> VendorFingerprint("Nordic DFU Bootloader", "🔄", "Nordic Semiconductor Device Firmware Update active.")
            uuids.contains("FEE0") -> VendorFingerprint("Tuya Smart IoT", "⚡", "Tuya smart home hardware module.")
            uuids.contains("FE68") -> VendorFingerprint("Espressif System", "🛠️", "ESP32/ESP8266 microcontroller service.")
            uuids.contains("FEF1") -> VendorFingerprint("Tesla Key System", "🚗", "Tesla vehicle digital key BLE service.")
            uuids.contains("FE55") || uuids.contains("FE2E") -> VendorFingerprint("Bose Audio", "🎧", "Bose wireless audio control protocol.")
            uuids.contains("FE4B") -> VendorFingerprint("Fitbit", "⌚", "Fitbit activity tracking service.")
            uuids.contains("FE0F") || uuids.contains("FE13") -> VendorFingerprint("Philips Hue", "💡", "Philips Hue smart lighting BLE mesh service.")
            uuids.contains("FED5") -> VendorFingerprint("SwitchBot", "🤖", "SwitchBot automation controller service.")
            uuids.contains("FEF8") -> VendorFingerprint("Oura Ring", "💍", "Oura health tracking smart ring BLE service.")
            uuids.contains("FEA3") -> VendorFingerprint("Withings", "❤️", "Withings health device BLE service.")
            uuids.contains("FEA1") -> VendorFingerprint("Polar", "🏃", "Polar fitness sensor BLE service.")
            uuids.contains("FEA8") -> VendorFingerprint("Whoop", "💪", "Whoop fitness band BLE service.")
            uuids.contains("FE48") -> VendorFingerprint("Garmin", "⌚", "Garmin fitness device BLE service.")
            uuids.contains("FE50") -> VendorFingerprint("Sony", "🎵", "Sony audio device BLE service.")
            uuids.contains("FE5A") -> VendorFingerprint("Bang & Olufsen", "🔊", "Bang & Olufsen audio BLE service.")
            uuids.contains("FE4D") -> VendorFingerprint("Sennheiser", "🎧", "Sennheiser audio BLE service.")
            uuids.contains("FE3C") -> VendorFingerprint("JBL", "🔊", "JBL audio BLE service.")
            uuids.contains("FE60") -> VendorFingerprint("Huawei", "📱", "Huawei smart device BLE service.")
            uuids.contains("FE95") || uuids.contains("FE96") -> VendorFingerprint("Xiaomi", "📱", "Xiaomi MiBeacon/Flora smart device service.")
            uuids.contains("FE58") || uuids.contains("FE61") -> VendorFingerprint("Amazon", "📦", "Amazon device BLE service.")
            uuids.contains("FECB") -> VendorFingerprint("Ring", "🔔", "Ring doorbell or security device BLE service.")
            uuids.contains("FECC") -> VendorFingerprint("Wyze", "📹", "Wyze smart home device BLE service.")
            uuids.contains("FECD") -> VendorFingerprint("Eve Systems", "🏠", "Eve smart home device BLE service.")
            uuids.contains("FECE") -> VendorFingerprint("Arlo", "📹", "Arlo security camera BLE service.")
            uuids.contains("FECF") -> VendorFingerprint("Nanoleaf", "💡", "Nanoleaf smart lighting BLE service.")
            uuids.contains("FED0") -> VendorFingerprint("Sonos", "🔊", "Sonos audio speaker BLE service.")
            uuids.contains("FED1") -> VendorFingerprint("LIFX", "💡", "LIFX smart lighting BLE service.")
            uuids.contains("FED2") -> VendorFingerprint("TP-Link Kasa", "🔌", "TP-Link Kasa smart home BLE service.")
            uuids.contains("FED3") -> VendorFingerprint("Wiz", "💡", "Wiz smart lighting BLE service.")
            uuids.contains("FED4") -> VendorFingerprint("IKEA TRÅDFRI", "🪑", "IKEA smart home device BLE service.")
            uuids.contains("FED6") -> VendorFingerprint("Ecobee", "🌡️", "Ecobee smart thermostat BLE service.")
            uuids.contains("FED7") -> VendorFingerprint("Nest", "🏠", "Google Nest device BLE service.")
            uuids.contains("FED8") -> VendorFingerprint("August Lock", "🔒", "August smart lock BLE service.")
            uuids.contains("FED9") -> VendorFingerprint("Yale Lock", "🔒", "Yale smart lock BLE service.")
            uuids.contains("FEDB") -> VendorFingerprint("Schlage Lock", "🔒", "Schlage smart lock BLE service.")
            uuids.contains("FEDC") -> VendorFingerprint("MyQ Garage", "🚪", "Chamberlain/LiftMaster smart garage BLE service.")
            uuids.contains("FEF6") -> VendorFingerprint("Govee", "💡", "Govee smart lighting BLE service.")
            uuids.contains("FEF7") -> VendorFingerprint("Nuki", "🔒", "Nuki smart lock BLE service.")
            uuids.contains("FEF4") -> VendorFingerprint("BMW", "🚗", "BMW vehicle digital key BLE service.")
            uuids.contains("FEF5") -> VendorFingerprint("Mercedes", "🚗", "Mercedes-Benz vehicle digital key BLE service.")
            uuids.contains("FEF2") -> VendorFingerprint("Volkswagen", "🚗", "VW vehicle connected car BLE service.")
            uuids.contains("FE63") -> VendorFingerprint("GoPro", "📷", "GoPro action camera BLE service.")
            uuids.contains("FE72") -> VendorFingerprint("DJI", "🚁", "DJI drone BLE control service.")
            uuids.contains("FE70") -> VendorFingerprint("Nintendo", "🎮", "Nintendo game console BLE service.")
            uuids.contains("FE71") -> VendorFingerprint("Logitech", "🖱️", "Logitech wireless peripheral BLE service.")
            uuids.contains("FE73") -> VendorFingerprint("Anker", "🔋", "Anker device BLE service.")
            uuids.contains("FE74") -> VendorFingerprint("Belkin", "📡", "Belkin IoT device BLE service.")
            uuids.contains("FE75") -> VendorFingerprint("Roku", "📺", "Roku streaming device BLE service.")
            uuids.contains("FE76") -> VendorFingerprint("Chromecast", "📺", "Google Chromecast streaming device BLE service.")
            uuids.contains("FE77") || uuids.contains("FE78") -> VendorFingerprint("Nest Cam", "📹", "Google Nest Cam surveillance device BLE service.")
            uuids.contains("FE62") -> VendorFingerprint("Ledger", "🔐", "Ledger hardware crypto wallet BLE service.")
            uuids.contains("FE6A") -> VendorFingerprint("Canon", "🖨️", "Canon printer or camera BLE service.")
            uuids.contains("FE6D") -> VendorFingerprint("Panasonic", "📺", "Panasonic device BLE service.")
            else -> null
        }
    }
}
