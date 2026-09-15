package com.damon.wifiaudit.scan

import java.net.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SsdpDiscoveryHelper {

    private val multicastGroup = InetAddress.getByName("239.255.255.250")
    private val ssdpPort = 1900

    private val searchMessage = buildString {
        append("M-SEARCH * HTTP/1.1\r\n")
        append("HOST: 239.255.255.250:1900\r\n")
        append("MAN: \"ssdp:discover\"\r\n")
        append("MX: 2\r\n")
        append("ST: ssdp:all\r\n")
        append("\r\n")
    }

    suspend fun discover(timeoutMs: Int = 3000, onResult: (ip: String, info: String) -> Unit) = withContext(Dispatchers.IO) {
        // Try binding to port 1900 with SO_REUSEADDR so we can receive multicast
        // SSDP responses. Many devices reply via multicast to 239.255.255.250:1900
        // rather than unicasting back to the sender's ephemeral port.
        // Order matters: set reuseAddress BEFORE bind for it to take effect.
        var socket: MulticastSocket? = null
        try {
            socket = MulticastSocket(null).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = timeoutMs
                bind(InetSocketAddress(ssdpPort))
                // Join the SSDP multicast group to receive multicast responses.
                @Suppress("DEPRECATION")
                joinGroup(multicastGroup)
            }
        } catch (e: Exception) {
            // Port 1900 may already be in use; fall back to an ephemeral port.
            // We can still send the M-SEARCH and receive unicast responses.
            socket = MulticastSocket(null).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = timeoutMs
            }
            try {
                @Suppress("DEPRECATION")
                socket.joinGroup(multicastGroup)
            } catch (_: Exception) {
                // If we can't join the group, unicast responses will still work.
            }
        }

        try {
            val packet = DatagramPacket(
                searchMessage.toByteArray(),
                searchMessage.length,
                multicastGroup,
                ssdpPort
            )
            socket.send(packet)

            val buffer = ByteArray(2048)
            val deadline = System.currentTimeMillis() + timeoutMs

            while (System.currentTimeMillis() < deadline) {
                try {
                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    val text = String(response.data, 0, response.length)
                    parseResponse(text)?.let { (ip, info) ->
                        onResult(ip, info)
                    }
                } catch (e: SocketTimeoutException) {
                    // No data in this receive window; loop again until deadline.
                    continue
                } catch (e: Exception) {
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                @Suppress("DEPRECATION")
                socket?.leaveGroup(multicastGroup)
            } catch (_: Exception) {}
            socket?.close()
        }
    }

    private fun parseResponse(data: String): Pair<String, String>? {
        val location = Regex("LOCATION:\\s*(.+)", RegexOption.IGNORE_CASE)
            .find(data)?.groupValues?.get(1)?.trim() ?: return null
        val ip = Regex("http://([0-9.]+)").find(location)?.groupValues?.get(1) ?: return null

        val server = Regex("SERVER:\\s*(.+)", RegexOption.IGNORE_CASE)
            .find(data)?.groupValues?.get(1)?.trim()
        val usn = Regex("USN:\\s*(.+)", RegexOption.IGNORE_CASE)
            .find(data)?.groupValues?.get(1)?.trim()

        val info = server ?: usn ?: "SSDP Device"
        return ip to info
    }
}
