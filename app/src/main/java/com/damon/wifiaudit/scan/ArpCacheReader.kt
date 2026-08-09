package com.damon.wifiaudit.scan

import java.io.File

/**
 * Reads /proc/net/arp, which is world-readable on Android (no root/permission
 * required). Entries only appear once the kernel has actually exchanged an
 * ARP request/reply for that IP — i.e. after some L3 traffic (like our TCP
 * connect probes) has touched that host. Format:
 *
 * IP address       HW type     Flags       HW address            Mask     Device
 * 192.168.1.1      0x1         0x2         aa:bb:cc:dd:ee:ff     *        wlan0
 */
object ArpCacheReader {

    private const val ARP_PATH = "/proc/net/arp"
    private const val EMPTY_MAC = "00:00:00:00:00:00"

    fun readArpTable(): Map<String, String> {
        val file = File(ARP_PATH)
        if (!file.exists() || !file.canRead()) return emptyMap()

        return try {
            file.readLines()
                .drop(1) // header row
                .mapNotNull { line ->
                    val cols = line.trim().split(Regex("\\s+"))
                    if (cols.size >= 4) {
                        val ip = cols[0]
                        val mac = cols[3]
                        if (mac != EMPTY_MAC) ip to mac else null
                    } else null
                }
                .toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun macForIp(ip: String): String? = readArpTable()[ip]
}
