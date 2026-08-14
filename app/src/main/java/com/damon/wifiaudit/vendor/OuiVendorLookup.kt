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
        // Samsung
        "0000F0" to v("Samsung"), "0007AB" to v("Samsung"), "000DE5" to v("Samsung"), "001247" to v("Samsung"), "0012FB" to v("Samsung"),
        "001599" to v("Samsung"), "0015B9" to v("Samsung"), "001632" to v("Samsung"), "00166C" to v("Samsung"), "0017C9" to v("Samsung"),
        "0017D5" to v("Samsung"), "0018AF" to v("Samsung"), "001A8A" to v("Samsung"), "001C62" to v("Samsung"), "001D25" to v("Samsung"),
        "001E7D" to v("Samsung"), "001FCC" to v("Samsung"), "002119" to v("Samsung"), "00214C" to v("Samsung"), "0021D2" to v("Samsung"),
        "002339" to v("Samsung"), "0023D6" to v("Samsung"), "002454" to v("Samsung"), "002491" to v("Samsung"), "0024E4" to v("Samsung"),
        "002514" to v("Samsung"), "002567" to v("Samsung"), "002637" to v("Samsung"), "00265D" to v("Samsung"), "0026AB" to v("Samsung"),
        "1867B0" to v("Samsung"),
        // Google
        "001A11" to v("Google"), "08606E" to v("Google"), "1C5A6B" to v("Google"), "20DFB9" to v("Google"), "3C5AB4" to v("Google"),
        "40D599" to v("Google"), "48D6D5" to v("Google"), "546009" to v("Google"), "58CB52" to v("Google"), "64167F" to v("Google"),
        "6CADF8" to v("Google"), "70EE50" to v("Google"), "7C2CE4" to v("Google"), "840D8E" to v("Google"), "94EBCD" to v("Google"),
        "A47733" to v("Google"), "B4F7A1" to v("Google"), "D824BD" to v("Google"), "E4F042" to v("Google"), "F88FCA" to v("Google"),
        // Huawei
        "0009A7" to v("Huawei"), "001882" to v("Huawei"), "001E10" to v("Huawei"), "0022A1" to v("Huawei"), "002568" to v("Huawei"),
        "00259E" to v("Huawei"), "00464B" to v("Huawei"), "043389" to v("Huawei"), "04C06F" to v("Huawei"), "0819A6" to v("Huawei"),
        // Xiaomi
        "009EC1" to v("Xiaomi"), "14F65A" to v("Xiaomi"), "185936" to v("Xiaomi"), "286C07" to v("Xiaomi"), "3480B3" to v("Xiaomi"),
        "50642B" to v("Xiaomi"), "640980" to v("Xiaomi"), "686EE2" to v("Xiaomi"), "7C1DD9" to v("Xiaomi"), "8CBEBE" to v("Xiaomi"),
        // Oppo
        "0495E6" to v("Oppo"), "0C8BFD" to v("Oppo"), "1432D1" to v("Oppo"), "18A6F7" to v("Oppo"), "24698E" to v("Oppo"),
        "3412F9" to v("Oppo"), "4C9364" to v("Oppo"), "50093D" to v("Oppo"), "5C0B5B" to v("Oppo"), "643E8C" to v("Oppo"),
        // Vivo
        "00D0D0" to v("Vivo"), "04F128" to v("Vivo"), "08EEAF" to v("Vivo"), "102AB3" to v("Vivo"), "18F0E4" to v("Vivo"),
        "28B2BD" to v("Vivo"), "2CE2A8" to v("Vivo"), "38D2CA" to v("Vivo"), "4040A7" to v("Vivo"), "440D9E" to v("Vivo"),
        // Sony
        "00014A" to v("Sony"), "00041F" to v("Sony"), "000AD9" to v("Sony"), "000D4B" to v("Sony"), "000E07" to v("Sony"),
        "001315" to v("Sony"), "0013A9" to v("Sony"), "0015C1" to v("Sony"), "001648" to v("Sony"), "0019C5" to v("Sony"),
        // LG
        "0005C9" to v("LG"), "000E7B" to v("LG"), "001247" to v("LG"), "001480" to v("LG"), "00166B" to v("LG"),
        "0019A1" to v("LG"), "001C62" to v("LG"), "001E75" to v("LG"), "0021FB" to v("LG"), "0022A9" to v("LG"),
        // Garmin
        "001E7C" to v("Garmin"), "002126" to v("Garmin"), "10C6FC" to v("Garmin"), "148F21" to v("Garmin"), "587133" to v("Garmin"),
        // Fitbit
        "20107A" to v("Fitbit"), "88C255" to v("Fitbit"), "00234D" to v("Fitbit"), "30B5C2" to v("Fitbit"), "48A2E6" to v("Fitbit"),
        // Bose
        "000C8A" to v("Bose"), "0024D7" to v("Bose"), "0452F3" to v("Bose"), "08DF1F" to v("Bose"), "2811A5" to v("Bose"),
        // Motorola
        "00006B" to v("Motorola"), "000456" to v("Motorola"), "00080E" to v("Motorola"), "000A28" to v("Motorola"), "000CE5" to v("Motorola"),
        // OnePlus
        "302155" to v("OnePlus"), "508E49" to v("OnePlus"), "64A2F9" to v("OnePlus"), "98F170" to v("OnePlus"), "AC5F3E" to v("OnePlus"),
        // Others
        "0003FF" to v("Microsoft"), "0001E6" to v("HP"), "00065B" to v("Dell"), "001217" to v("Lenovo"),
        "0009BF" to v("Nintendo"), "00044B" to v("NVIDIA"), "0007CB" to v("Intel"), "00E04C" to v("Realtek"),
        "001018" to v("Broadcom"), "000B6B" to v("MediaTek"), "000AF5" to v("Qualcomm"),

        // IoT / Networking
        "000C41" to v("Cisco-Linksys"), "00E0FC" to v("Hikvision"), "00C0CA" to v("Hikvision"), "4447CC" to v("Hikvision"),
        "C05627" to v("Hikvision"), "54C415" to v("Hikvision"), "BC1485" to v("Samsung"), "BC7ABF" to v("Samsung"),
        "B0C4E7" to v("Samsung"), "001132" to v("Synology"), "001D63" to v("QNAP"), "6C198F" to v("D-Link"),
        "000F3D" to v("D-Link"), "C0A0BB" to v("D-Link"), "F07D68" to v("D-Link"), "001CF0" to v("D-Link"),
        "50C7BF" to v("TP-Link"), "C0C9E3" to v("TP-Link"), "14CC20" to v("TP-Link"), "60E327" to v("TP-Link"),
        "A0F3C1" to v("TP-Link"), "A040A0" to v("Netgear"), "28C68E" to v("Netgear"), "001E2A" to v("Netgear"),
        "C8D719" to v("Cisco-Linksys"), "04D4C4" to v("ASUS"), "2C4D54" to v("ASUS"), "24A43C" to v("Ubiquiti"),
        "FCEC38" to v("Ubiquiti"), "802AA8" to v("Ubiquiti"), "B4FBE4" to v("Ubiquiti"), "00408C" to v("Axis Communications"),
        "ACCC8E" to v("Axis Communications"), "EC71DB" to v("Reolink"), "9C8E99" to v("Reolink"), "A0CC2B" to v("Amcrest"),
        "C4AD34" to v("Amcrest"), "001CFA" to v("Foscam"), "2CFAA2" to v("Wyze"), "B0C554" to v("Amazon Technologies"),
        "747548" to v("Amazon Technologies"), "F0D2F1" to v("Amazon Technologies"), "68A40E" to v("Amazon Technologies"),
        "34D270" to v("Amazon Technologies"), "240AC4" to v("Espressif"), "30AEA4" to v("Espressif"),
        "84F3EB" to v("Espressif"), "A4CF12" to v("Espressif"), "7C9EBD" to v("Espressif"), "CC50E3" to v("Espressif"),
        "D8A01D" to v("Espressif"), "246F28" to v("Espressif"), "84CCAD" to v("Tuya"), "54EF92" to v("Google"),
        "F4F5E8" to v("Google"), "18B430" to v("Nest Labs"), "AC87A3" to v("Apple"), "F0D1A9" to v("Apple"),
        "A4C361" to v("Apple"), "00A0C6" to v("SpaceX Starlink"), "28E31F" to v("Xiaomi"), "64CC2E" to v("Xiaomi"),
        "B827EB" to v("Raspberry Pi Foundation"), "DC447D" to v("Arlo"), "04B167" to v("Anker (Eufy)"),
        "4C5E0C" to v("MikroTik"), "6C3B6B" to v("MikroTik"), "00000C" to v("Cisco"), "001A2F" to v("Cisco"),
        "001E10" to v("Huawei"), "0019E0" to v("ZTE"), "000E58" to v("Sonos"), "B8E937" to v("Sonos"),
        "D8DCE9" to v("Roku"), "001788" to v("Philips Hue"), "ECB5FA" to v("Philips Hue"),
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
            
            // Priority 1: assets/oui.csv
            loadFromAsset(context, "oui.csv", isCsv = true)
            
            // Priority 2: assets/oui.txt (as fallback or supplement)
            loadFromAsset(context, "oui.txt", isCsv = false)
            
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
        val prefix = normalizePrefix(parts[0]) ?: return null
        val name = parts[1].trim().removeSurrounding("\"")
        return prefix to name
    }

    private fun parseTxtLine(line: String): Pair<String, String>? {
        val match = Regex("""^([0-9A-Fa-f]{2})[-:]?([0-9A-Fa-f]{2})[-:]?([0-9A-Fa-f]{2})\s+(.+)$""")
            .find(line) ?: return null
        val prefix = (match.groupValues[1] + match.groupValues[2] + match.groupValues[3]).uppercase()
        val name = match.groupValues[4].trim()
        return prefix to name
    }

    /** Returns manufacturer name or null if unknown. */
    fun lookup(macAddress: String?): String? = lookupInfo(macAddress)?.name

    /** Returns full vendor metadata or null. */
    fun lookupInfo(macAddress: String?): VendorInfo? {
        if (macAddress.isNullOrBlank()) return null
        val prefix = normalizePrefix(macAddress) ?: return null
        // Exact 6-hex prefix
        return cache[prefix] ?: seed[prefix]
    }

    private fun normalizePrefix(mac: String): String? {
        val hex = mac.replace(":", "")
            .replace("-", "")
            .replace(".", "")
            .uppercase()
            .filter { it in '0'..'9' || it in 'A'..'F' }
        if (hex.length < 6) return null
        return hex.take(6)
    }
}
