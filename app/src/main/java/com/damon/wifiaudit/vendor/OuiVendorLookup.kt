package com.damon.wifiaudit.vendor

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * OUI (Organizationally Unique Identifier) vendor lookup.
 *
 * Loads a compact prefix → manufacturer map from assets/oui.txt if present,
 * otherwise falls back to a built-in seed of common IoT / networking vendors.
 * Thread-safe; safe to call from any thread after [initialize].
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

    // Compact seed covering the vendors this app cares about most
    // (cameras, routers, doorbells, common IoT). Keys are uppercase, no separators.
    private val seed: Map<String, VendorInfo> = mapOf(
        // Hikvision / Dahua family
        "000C41" to v("Cisco-Linksys"),
        "00E0FC" to v("Hikvision"),
        "00C0CA" to v("Hikvision"),
        "4447CC" to v("Hikvision"),
        "C05627" to v("Hikvision"),
        "54C415" to v("Hikvision"),
        "BC1485" to v("Samsung"),
        "BC7ABF" to v("Samsung"),
        "B0C4E7" to v("Samsung"),
        "001132" to v("Synology"),
        "001D63" to v("QNAP"),
        // D-Link
        "6C198F" to v("D-Link"),
        "000F3D" to v("D-Link"),
        "C0A0BB" to v("D-Link"),
        "F07D68" to v("D-Link"),
        "001CF0" to v("D-Link"),
        // TP-Link
        "50C7BF" to v("TP-Link"),
        "C0C9E3" to v("TP-Link"),
        "14CC20" to v("TP-Link"),
        "60E327" to v("TP-Link"),
        "A0F3C1" to v("TP-Link"),
        // Netgear / Linksys / ASUS
        "A040A0" to v("Netgear"),
        "28C68E" to v("Netgear"),
        "001E2A" to v("Netgear"),
        "C8D719" to v("Cisco-Linksys"),
        "04D4C4" to v("ASUS"),
        "2C4D54" to v("ASUS"),
        // Ubiquiti
        "24A43C" to v("Ubiquiti"),
        "FCEC38" to v("Ubiquiti"),
        "802AA8" to v("Ubiquiti"),
        "B4FBE4" to v("Ubiquiti"),
        // Axis
        "00408C" to v("Axis Communications"),
        "ACCC8E" to v("Axis Communications"),
        // Reolink / Amcrest / Foscam / Wyze
        "EC71DB" to v("Reolink"),
        "9C8E99" to v("Reolink"),
        "A0CC2B" to v("Amcrest"),
        "C4AD34" to v("Amcrest"),
        "001CFA" to v("Foscam"),
        "2CFAA2" to v("Wyze"),
        // Ring / Amazon
        "B0C554" to v("Amazon Technologies"),
        "747548" to v("Amazon Technologies"),
        "F0D2F1" to v("Amazon Technologies"),
        "68A40E" to v("Amazon Technologies"),
        "34D270" to v("Amazon Technologies"),
        // Espressif / Tuya / generic IoT
        "240AC4" to v("Espressif"),
        "30AEA4" to v("Espressif"),
        "84F3EB" to v("Espressif"),
        "A4CF12" to v("Espressif"),
        "7C9EBD" to v("Espressif"),
        "CC50E3" to v("Espressif"),
        "D8A01D" to v("Espressif"),
        "246F28" to v("Espressif"),
        "84CCAD" to v("Tuya"),
        // Google / Nest
        "54EF92" to v("Google"),
        "F4F5E8" to v("Google"),
        "18B430" to v("Nest Labs"),
        // Apple (common)
        "AC87A3" to v("Apple"),
        "F0D1A9" to v("Apple"),
        "A4C361" to v("Apple"),
        // SpaceX Starlink
        "00A0C6" to v("SpaceX"),  // placeholder; real OUIs vary
        // Xiaomi / Arlo / Eufy
        "28E31F" to v("Xiaomi"),
        "64CC2E" to v("Xiaomi"),
        "B827EB" to v("Raspberry Pi Foundation"),
        "DC447D" to v("Arlo"),
        "04B167" to v("Anker Innovations"), // Eufy
        // MikroTik / Cisco / Huawei / ZTE
        "4C5E0C" to v("MikroTik"),
        "6C3B6B" to v("MikroTik"),
        "00000C" to v("Cisco"),
        "001A2F" to v("Cisco"),
        "00E0FC" to v("Huawei"),
        "00E0FC" to v("Hikvision"), // conflict resolved by last-write; seed order matters less
        "001E10" to v("Huawei"),
        "0019E0" to v("ZTE"),
        // Sonos / Roku / Philips Hue
        "000E58" to v("Sonos"),
        "B8E937" to v("Sonos"),
        "D8DCE9" to v("Roku"),
        "001788" to v("Philips Lighting"),
        "ECB5FA" to v("Philips Lighting"),
    )

    private fun v(name: String, private: Boolean = false) =
        VendorInfo(name = name, prefix = "", blockType = "MA-L", isPrivate = private)

    /**
     * Optional: load a larger OUI database from assets/oui.txt.
     * Expected line format (IEEE-ish):
     *   AA-BB-CC   (hex)		Manufacturer Name
     * or simply:
     *   AABBCC Manufacturer Name
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                context.assets.open("oui.txt").use { stream ->
                    BufferedReader(InputStreamReader(stream)).useLines { lines ->
                        lines.forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                            // Match "AA-BB-CC" or "AABBCC" then rest of line as name
                            val match = Regex("""^([0-9A-Fa-f]{2})[-:]?([0-9A-Fa-f]{2})[-:]?([0-9A-Fa-f]{2})\s+(.+)$""")
                                .find(trimmed) ?: return@forEach
                            val prefix = (match.groupValues[1] + match.groupValues[2] + match.groupValues[3]).uppercase()
                            val name = match.groupValues[4].trim()
                            if (name.isNotEmpty()) {
                                cache[prefix] = VendorInfo(
                                    name = name,
                                    prefix = prefix,
                                    blockType = "MA-L"
                                )
                            }
                        }
                    }
                }
                Log.i("OuiVendorLookup", "Loaded ${cache.size} OUI entries from assets")
            } catch (e: Exception) {
                // Asset optional — seed map is enough for core functionality
                Log.d("OuiVendorLookup", "No assets/oui.txt (using seed only): ${e.message}")
            }
            // Seed fills gaps; do not overwrite asset entries
            seed.forEach { (prefix, info) ->
                cache.putIfAbsent(prefix, info.copy(prefix = prefix))
            }
            initialized = true
        }
    }

    /** Returns manufacturer name or null if unknown. */
    fun lookup(macAddress: String?): String? = lookupInfo(macAddress)?.name

    /** Returns full vendor metadata or null. */
    fun lookupInfo(macAddress: String?): VendorInfo? {
        if (macAddress.isNullOrBlank()) return null
        val prefix = normalizePrefix(macAddress) ?: return null
        // Check exact 6-hex prefix first, then try longer blocks if present
        return cache[prefix]
            ?: cache[prefix.take(6)]
            ?: seed[prefix]
            ?: seed[prefix.take(6)]
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
