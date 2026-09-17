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

    /**
     * Returns true if the ARP cache is actually readable and contains entries.
     * On Android 10+ the file may exist and be "readable" but return empty.
     */
    fun isArpUsable(): Boolean {
        return readArpTable().isNotEmpty()
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
     * resolution sequence, then re-reads the ARP cache with short retries.
     *
     * The kernel may need a moment to process the ARP response and write it
     * to /proc/net/arp, so we retry a few times with a small delay.
     */
    fun resolveMacWithProbe(ip: String): String? {
        val existing = macForIp(ip)
        if (existing != null) return existing

        // Send UDP probes to multiple common ports to increase the chance
        // of triggering ARP resolution.
        val probePorts = intArrayOf(7, 9, 137, 161, 5353, 9100)
        for (port in probePorts) {
            try {
                val socket = DatagramSocket()
                socket.soTimeout = 100
                val data = byteArrayOf(0x00)
                val packet = DatagramPacket(data, data.size, InetAddress.getByName(ip), port)
                socket.send(packet)
                socket.close()
            } catch (_: Exception) {
                // Ignore socket errors
            }
        }

        // Retry reading the ARP cache a few times with a small delay to allow
        // the kernel to process and write the ARP entry.
        repeat(5) { attempt ->
            val mac = macForIp(ip)
            if (mac != null) return mac
            try {
                Thread.sleep(50L + attempt * 30L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
        }

        return null
    }

    /**
     * Normalizes a MAC address to uppercase colon-separated format.
     * Handles various input formats: "a4:cf:12:34:56:78", "A4CF12345678", etc.
     */
    fun normalizeMac(mac: String): String {
        val hex = mac.uppercase().replace(":", "").replace("-", "").replace(".", "")
        return if (hex.length == 12) {
            hex.chunked(2).joinToString(":")
        } else {
            mac.uppercase()
        }
    }
}
