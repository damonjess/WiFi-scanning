package com.damon.wifiaudit.scan

import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Reads /proc/net/arp to obtain MAC addresses for local IP addresses.
 *
 * Note: On Android 10 (API 29) and newer, Google restricted access to /proc/net/arp
 * for non-system apps to protect device privacy. On these OS versions, /proc/net/arp
 * returns empty or permission denied, so MAC addresses cannot be read directly via ARP.
 */
object ArpCacheReader {

    private const val ARP_PATH = "/proc/net/arp"
    private const val EMPTY_MAC = "00:00:00:00:00:00"

    fun isArpSupported(): Boolean {
        val file = File(ARP_PATH)
        return file.exists() && file.canRead()
    }

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
                        val flag = cols[2]
                        val mac = cols[3]
                        if (mac != EMPTY_MAC && flag != "0x0") ip to mac else null
                    } else null
                }
                .toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun macForIp(ip: String): String? = readArpTable()[ip]

    /**
     * Sends a light UDP probe to the target host to trigger the kernel ARP
     * resolution sequence, then re-reads the ARP cache.
     */
    fun resolveMacWithProbe(ip: String): String? {
        val existing = macForIp(ip)
        if (existing != null) return existing

        try {
            val socket = DatagramSocket()
            socket.soTimeout = 150
            val data = byteArrayOf(0x00)
            val packet = DatagramPacket(data, data.size, InetAddress.getByName(ip), 7)
            socket.send(packet)
            socket.close()
        } catch (_: Exception) {
            // Ignore socket errors
        }

        return macForIp(ip)
    }
}
