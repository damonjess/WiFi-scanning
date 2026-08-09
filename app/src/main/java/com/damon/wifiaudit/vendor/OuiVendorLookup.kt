package com.damon.wifiaudit.vendor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object OuiVendorLookup {

    private var vendorMap: Map<String, String>? = null
    private val lock = Any()

    suspend fun initialize(context: Context) {
        if (vendorMap != null) return
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                if (vendorMap != null) return@withContext
                val map = HashMap<String, String>(40000)
                try {
                    context.assets.open("oui.csv").use { stream ->
                        BufferedReader(InputStreamReader(stream)).forEachLine { line ->
                            val parts = line.split(",", limit = 2)
                            if (parts.size == 2) {
                                map[parts[0].trim().uppercase()] = parts[1].trim()
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Missing/malformed asset — lookups will just return null (Unknown)
                }
                vendorMap = map
            }
        }
    }

    /**
     * Returns the vendor name, "Randomized address" if the MAC has the
     * locally-administered bit set (common on modern phones/wearables that
     * rotate BLE addresses for privacy), or null if genuinely not found
     * in the OUI table.
     */
    fun lookup(macAddress: String): String? {
        if (isRandomizedAddress(macAddress)) return "Randomized address"
        val prefix = macAddress.replace(":", "").replace("-", "").uppercase().take(6)
        return vendorMap?.get(prefix)
    }

    /**
     * The 2nd least-significant bit of the first octet is the
     * "locally administered" flag (IEEE 802 spec). If set, this address
     * was NOT assigned by a manufacturer's registered OUI block — it's
     * either randomized (common BLE privacy behavior) or virtual/software-set.
     */
    private fun isRandomizedAddress(macAddress: String): Boolean {
        val firstOctetHex = macAddress.replace(":", "").replace("-", "").take(2)
        val firstOctet = firstOctetHex.toIntOrNull(16) ?: return false
        return (firstOctet and 0x02) != 0
    }
}
