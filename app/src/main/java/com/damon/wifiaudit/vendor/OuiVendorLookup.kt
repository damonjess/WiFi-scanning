package com.damon.wifiaudit.vendor

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * OUI (Organizationally Unique Identifier) vendor lookup.
 *
 * Loads a prefix → manufacturer map from assets (oui.csv or oui.txt).
 * Falls back to a comprehensive seed of common mobile, wearable, and IoT vendors.
 */
object OuiVendorLookup {

    data class VendorInfo(
        val name: String,
        val prefix: String,
        val blockType: String? = "MA-L",
        val lastUpdate: String? = null,
        val isPrivate: Boolean = false
    )

    private val cache = ConcurrentHashMap<String, VendorInfo?>()
    @Volatile private var initialized = false

    // Comprehensive seed covering common mobile, Bluetooth, and IoT vendors.
    private val seed: Map<String, VendorInfo> = mapOf(
        // Mobile / Wearables / Audio
        // Apple
        "000393" to v("Apple"), "000502" to v("Apple"), "000A27" to v("Apple"), "000A95" to v("Apple"), "0010FA" to v("Apple"),
        "001124" to v("Apple"), "001451" to v("Apple"), "0016CB" to v("Apple"), "0017F2" to v("Apple"), "0019E3" to v("Apple"),
        "001B63" to v("Apple"), "001CB3" to v("Apple"), "001D4F" to v("Apple"), "001E52" to v("Apple"), "001F5B" to v("Apple"),
        "001FF3" to v("Apple"), "0021E9" to v("Apple"), "002241" to v("Apple"), "002312" to v("Apple"), "002332" to v("Apple"),
        "00236C" to v("Apple"), "002436" to v("Apple"), "002500" to v("Apple"), "00254B" to v("Apple"), "0025BC" to v("Apple"),
        "002608" to v("Apple"), "00264A" to v("Apple"), "0026B0" to v("Apple"), "0026BB" to v("Apple"), "28CFE9" to v("Apple"),
        "DC2B61" to v("Apple"), "F81ED3" to v("Apple"), "F0DBF8" to v("Apple"), "F4F15A" to v("Apple"), "F4DBE6" to v("Apple"),
        "E4CE8F" to v("Apple"), "D4909C" to v("Apple"), "C0847A" to v("Apple"), "B8C75D" to v("Apple"), "A4D18C" to v("Apple"),
        "9C04ED" to v("Apple"), "88665D" to v("Apple"), "705681" to v("Apple"), "64B9E8" to v("Apple"), "5C97F3" to v("Apple"),
        "4C7C5F" to v("Apple"), "38C986" to v("Apple"), "2CBE08" to v("Apple"), "1C1A68" to v("Apple"), "AC87A3" to v("Apple"),
        // Samsung
        "0000F0" to v("Samsung"), "0007AB" to v("Samsung"), "000DE5" to v("Samsung"), "001247" to v("Samsung"), "0012FB" to v("Samsung"),
        "001599" to v("Samsung"), "0015B9" to v("Samsung"), "001632" to v("Samsung"), "00166C" to v("Samsung"), "0017C9" to v("Samsung"),
        "0017D5" to v("Samsung"), "0018AF" to v("Samsung"), "001A8A" to v("Samsung"), "001C62" to v("Samsung"), "001D25" to v("Samsung"),
        "001E7D" to v("Samsung"), "001FCC" to v("Samsung"), "002119" to v("Samsung"), "00214C" to v("Samsung"), "0021D2" to v("Samsung"),
        "002339" to v("Samsung"), "0023D6" to v("Samsung"), "002454" to v("Samsung"), "002491" to v("Samsung"), "0024E4" to v("Samsung"),
        "002514" to v("Samsung"), "002567" to v("Samsung"), "002637" to v("Samsung"), "00265D" to v("Samsung"), "0026AB" to v("Samsung"),
        "1867B0" to v("Samsung"), "E470B8" to v("Samsung"), "DC0E77" to v("Samsung"), "D022BE" to v("Samsung"), "C43018" to v("Samsung"),
        "B46921" to v("Samsung"), "A02195" to v("Samsung"), "94350A" to v("Samsung"), "883660" to v("Samsung"), "784040" to v("Samsung"),
        // Google
        "001A11" to v("Google"), "08606E" to v("Google"), "1C5A6B" to v("Google"), "20DFB9" to v("Google"), "3C5AB4" to v("Google"),
        "40D599" to v("Google"), "48D6D5" to v("Google"), "546009" to v("Google"), "58CB52" to v("Google"), "64167F" to v("Google"),
        "6CADF8" to v("Google"), "70EE50" to v("Google"), "7C2CE4" to v("Google"), "840D8E" to v("Google"), "94EBCD" to v("Google"),
        "A47733" to v("Google"), "B4F7A1" to v("Google"), "D824BD" to v("Google"), "E4F042" to v("Google"), "F88FCA" to v("Google"),
        "F4F5E8" to v("Google"), "D83B91" to v("Google"), "C869CD" to v("Google"), "9C9B1E" to v("Google"), "74D435" to v("Google"),
        // Xiaomi
        "009EC1" to v("Xiaomi"), "14F65A" to v("Xiaomi"), "185936" to v("Xiaomi"), "286C07" to v("Xiaomi"), "3480B3" to v("Xiaomi"),
        "50642B" to v("Xiaomi"), "640980" to v("Xiaomi"), "686EE2" to v("Xiaomi"), "7C1DD9" to v("Xiaomi"), "8CBEBE" to v("Xiaomi"),
        "FC7C02" to v("Xiaomi"), "F8A45D" to v("Xiaomi"), "E4AAEA" to v("Xiaomi"), "D4619D" to v("Xiaomi"), "C8F742" to v("Xiaomi"),
        // Sony / Audio / Wearables
        "00014A" to v("Sony"), "00041F" to v("Sony"), "000AD9" to v("Sony"), "000D4B" to v("Sony"), "000E07" to v("Sony"),
        "FC0FDE" to v("Sony"), "F4428F" to v("Sony"), "D49D4B" to v("Sony"), "B4527D" to v("Sony"), "A0E6F8" to v("Sony"),
        "001E7C" to v("Garmin"), "002126" to v("Garmin"), "10C6FC" to v("Garmin"), "148F21" to v("Garmin"), "587133" to v("Garmin"),
        "20107A" to v("Fitbit"), "88C255" to v("Fitbit"), "00234D" to v("Fitbit"), "30B5C2" to v("Fitbit"), "48A2E6" to v("Fitbit"),
        "000C8A" to v("Bose"), "0024D7" to v("Bose"), "0452F3" to v("Bose"), "08DF1F" to v("Bose"), "2811A5" to v("Bose"),

        // Espressif & Tuya (ESP32 / ESP8266 IoT)
        "240AC4" to v("Espressif"), "30AEA4" to v("Espressif"), "84F3EB" to v("Espressif"), "A4CF12" to v("Espressif"),
        "7C9EBD" to v("Espressif"), "CC50E3" to v("Espressif"), "D8A01D" to v("Espressif"), "246F28" to v("Espressif"),
        "E0E2E6" to v("Espressif"), "E868E7" to v("Espressif"), "ECFABC" to v("Espressif"), "F412FA" to v("Espressif"),
        "1097BD" to v("Espressif"), "18FE34" to v("Espressif"), "2C3AE8" to v("Espressif"), "348518" to v("Espressif"),
        "40F520" to v("Espressif"), "483FDA" to v("Espressif"), "48E729" to v("Espressif"), "545A06" to v("Espressif"),
        "600194" to v("Espressif"), "68C63A" to v("Espressif"), "70039F" to v("Espressif"), "840D8E" to v("Espressif"),
        "9097D5" to v("Espressif"), "94B97E" to v("Espressif"), "98CDAC" to v("Espressif"), "A020A6" to v("Espressif"),
        "84CCAD" to v("Tuya"), "D4A651" to v("Tuya"), "CC8CBF" to v("Tuya"), "C44EAC" to v("Tuya"), "708976" to v("Tuya"),
        "508A06" to v("Tuya"), "10D561" to v("Tuya"), "005B94" to v("Tuya"),

        // Networking & Routers & Smart Home
        "50C7BF" to v("TP-Link"), "C0C9E3" to v("TP-Link"), "14CC20" to v("TP-Link"), "60E327" to v("TP-Link"),
        "A0F3C1" to v("TP-Link"), "F8A5C8" to v("TP-Link"), "E848B8" to v("TP-Link"), "DC396F" to v("TP-Link"),
        "D807B6" to v("TP-Link"), "B0A7B9" to v("TP-Link"), "98DAC4" to v("TP-Link"), "808F00" to v("TP-Link"),
        "A040A0" to v("Netgear"), "28C68E" to v("Netgear"), "001E2A" to v("Netgear"), "EC1A59" to v("Netgear"),
        "E0469A" to v("Netgear"), "C86000" to v("Netgear"), "94163E" to v("Netgear"), "841B5E" to v("Netgear"),
        "04D4C4" to v("ASUS"), "2C4D54" to v("ASUS"), "FC3497" to v("ASUS"), "F832E4" to v("ASUS"), "E03F49" to v("ASUS"),
        "24A43C" to v("Ubiquiti"), "FCEC38" to v("Ubiquiti"), "802AA8" to v("Ubiquiti"), "B4FBE4" to v("Ubiquiti"),
        "B827EB" to v("Raspberry Pi Foundation"), "E45F01" to v("Raspberry Pi Foundation"), "DCA632" to v("Raspberry Pi Foundation"),
        "D8DCE9" to v("Roku"), "CC6EA4" to v("Roku"), "B0A73B" to v("Roku"), "AC3A7A" to v("Roku"), "84EA99" to v("Roku"),
        "000E58" to v("Sonos"), "B8E937" to v("Sonos"), "F40343" to v("Sonos"), "E89E94" to v("Sonos"), "98E7F5" to v("Sonos"),
        "001788" to v("Philips Hue"), "ECB5FA" to v("Philips Hue"), "EC1BBD" to v("Philips Hue"),
        "EC71DB" to v("Reolink"), "9C8E99" to v("Reolink"), "2CFAA2" to v("Wyze"), "04B167" to v("Anker (Eufy)"),
        "DC447D" to v("Arlo"), "A0CC2B" to v("Amcrest"), "C4AD34" to v("Amcrest"), "001CFA" to v("Foscam"),
        "00E0FC" to v("Hikvision"), "00C0CA" to v("Hikvision"), "4447CC" to v("Hikvision"), "C05627" to v("Hikvision"),
        "FC4D96" to v("Dahua"), "E0508B" to v("Dahua"), "A0BD1D" to v("Dahua"), "5C04A0" to v("Dahua"),
        "001132" to v("Synology"), "001D63" to v("QNAP"), "00A0C6" to v("SpaceX Starlink"), "4C5E0C" to v("MikroTik"),
        "B0C554" to v("Amazon Technologies"), "747548" to v("Amazon Technologies"), "F0D2F1" to v("Amazon Technologies"),
        "68A40E" to v("Amazon Technologies"), "34D270" to v("Amazon Technologies"), "FCA667" to v("Amazon Technologies"),
        "18B430" to v("Nest Labs"), "0009BF" to v("Nintendo"), "00044B" to v("NVIDIA"), "0007CB" to v("Intel"),
    )

    private fun v(name: String, private: Boolean = false) =
        VendorInfo(name = name, prefix = "", isPrivate = private)

    /**
     * Initializes the lookup cache from assets/oui.csv or assets/oui.txt.
     */
    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            
            try {
                // Priority 1: current compact master export. It includes MA-L,
                // MA-M, MA-S, IAB, and community vendor records without shipping
                // the larger address-rich CSV format in the APK.
                loadFromAsset(context, "oui_master.txt", isCsv = false)

                // Priority 2: retain the app's prior CSV as a compatibility fallback.
                loadFromAsset(context, "oui.csv", isCsv = true)
            } catch (e: Exception) {
                Log.e("OuiVendorLookup", "Failed to load OUI assets", e)
            }

            // Priority 3: Built-in seed fills any remaining gaps
            seed.forEach { (prefix, info) ->
                cache.putIfAbsent(prefix, info.copy(prefix = prefix))
            }
            
            Log.i("OuiVendorLookup", "Initialized with ${cache.size} entries")
            initialized = true
        }
    }

    private fun loadFromAsset(context: Context, fileName: String, isCsv: Boolean) {
        try {
            context.assets.open(fileName).use { stream ->
                BufferedReader(InputStreamReader(stream)).useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        if (isCsv && index == 0) return@forEachIndexed // Skip header
                        val trimmed = line.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachIndexed
                        
                        val (prefix, name) = if (isCsv) {
                            parseCsvLine(trimmed)
                        } else {
                            parseTxtLine(trimmed)
                        } ?: return@forEachIndexed
                        
                        if (name.isNotEmpty()) {
                            cache.putIfAbsent(prefix, VendorInfo(name = name, prefix = prefix))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Asset optional
        }
    }

    private fun parseCsvLine(line: String): Pair<String, String>? {
        val parts = mutableListOf<String>()
        var inQuotes = false
        var current = StringBuilder()
        for (char in line) {
            when {
                char == '\"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    parts.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        parts.add(current.toString())

        if (parts.size < 2) return null
        val prefix = normalizeAssignmentPrefix(parts[0]) ?: return null
        val name = parts[1].trim().removeSurrounding("\"")
        return prefix to name
    }

    private fun parseTxtLine(line: String): Pair<String, String>? {
        val parts = line.trim().split(Regex("\\s+"), limit = 2)
        if (parts.size < 2) return null
        val prefix = normalizeAssignmentPrefix(parts[0]) ?: return null
        val name = parts[1].trim()
        return prefix to name
    }

    /** Returns manufacturer name or null if unknown. */
    fun lookup(macAddress: String?): String? = lookupInfo(macAddress)?.name

    /** Returns full vendor metadata or null. */
    fun lookupInfo(macAddress: String?): VendorInfo? {
        if (macAddress.isNullOrBlank()) return null
        val address = normalizeAddress(macAddress) ?: return null

        // Prefer the most specific allocation (MA-S/IAB 36-bit, then MA-M
        // 28-bit, then the conventional MA-L 24-bit OUI).
        for (length in EXTENDED_PREFIX_LENGTHS) {
            val prefix = address.takeIf { it.length >= length }?.take(length)
            val info = prefix?.let { cache[it] ?: seed[it] }
            if (info != null) return info
        }
        return null
    }

    private fun normalizeAddress(value: String): String? {
        val hex = value.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
        return hex.takeIf { it.length >= 6 }
    }

    /** Parses `AA:BB:CC`, `AA:BB:CC:D0/28`, and `AA:BB:CC:DD:E0/36`. */
    private fun normalizeAssignmentPrefix(value: String): String? {
        val rawPrefix = value.substringBefore('/')
        val hex = normalizeAddress(rawPrefix) ?: return null
        val bits = value.substringAfter('/', missingDelimiterValue = "24").toIntOrNull() ?: 24
        val nibbleLength = when (bits) {
            24 -> 6
            28 -> 7
            36 -> 9
            else -> 6
        }
        return hex.takeIf { it.length >= nibbleLength }?.take(nibbleLength)
    }

    private val EXTENDED_PREFIX_LENGTHS = intArrayOf(9, 7, 6)
}
