package com.damon.wifiaudit.vendor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object OuiVendorLookup {

    data class VendorInfo(
        val name: String,
        val prefix: String,
        val isPrivate: Boolean = false,
        val blockType: String? = null,
        val lastUpdate: String? = null
    )

    private var vendorMap: Map<String, VendorInfo>? = null
    private val lock = Any()

    suspend fun initialize(context: Context) {
        if (vendorMap != null) return
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                if (vendorMap != null) return@withContext
                val map = HashMap<String, VendorInfo>(40000)
                try {
                    context.assets.open("oui.csv").use { stream ->
                        BufferedReader(InputStreamReader(stream)).use { reader ->
                            // Skip header
                            reader.readLine()
                            reader.forEachLine { line ->
                                val parts = parseCsvLine(line)
                                if (parts.size >= 2) {
                                    val prefix = parts[0].trim().uppercase()
                                    map[prefix] = VendorInfo(
                                        name = parts[1].trim().trim('"'),
                                        prefix = prefix,
                                        isPrivate = parts.getOrNull(2)?.toBoolean() ?: false,
                                        blockType = parts.getOrNull(3),
                                        lastUpdate = parts.getOrNull(4)
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Missing/malformed asset
                }
                vendorMap = map
            }
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result
    }

    /**
     * Returns the vendor info, "Randomized address" if the MAC has the
     * locally-administered bit set, or null if not found.
     */
    fun lookup(macAddress: String): String? {
        val info = lookupInfo(macAddress)
        return info?.name
    }

    fun lookupInfo(macAddress: String): VendorInfo? {
        if (isRandomizedAddress(macAddress)) {
            return VendorInfo("Randomized address", macAddress.take(8))
        }
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
