package com.damon.wifiaudit.scan

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * NetBIOS Name Service (NBNS) discovery.
 *
 * Sends a NetBIOS Name Service broadcast query on UDP 137. Windows machines,
 * NAS devices, Samba shares, network printers, and some routers respond with
 * their NetBIOS name and MAC address — even when they have no open TCP ports
 * visible to a port scanner.
 *
 * The query is a NBSTAT (Node Status) request for the wildcard name "*",
 * which asks the device to return its entire NetBIOS name table.
 *
 * Spec: RFC 1001/1002 — NetBIOS over TCP/IP
 */
class NbnsDiscoveryHelper {

    private val tag = "NbnsDiscoveryHelper"
    private val nbnsPort = 137

    /**
     * Build a NetBIOS Name Service NBSTAT request packet.
     *
     * Header (12 bytes):
     *   Transaction ID (2) | Flags (2) | Questions (2) | Answers (2) |
     *   Authority (2) | Additional (2)
     * Question:
     *   Encoded name "*" (32 bytes) | Null terminator (1) | Type NBSTAT (2) | Class IN (2)
     */
    private fun buildNbstatRequest(): ByteArray {
        val buffer = ByteBuffer.allocate(50).order(ByteOrder.BIG_ENDIAN)

        // Transaction ID
        buffer.putShort(0x1337)

        // Flags: Standard query, recursion not desired
        buffer.putShort(0x0000)

        // Question count = 1
        buffer.putShort(1)

        // Answer, Authority, Additional counts = 0
        buffer.putShort(0)
        buffer.putShort(0)
        buffer.putShort(0)

        // NetBIOS encoded name for "*" — each char becomes 2 bytes (0x41 + (char >> 4), 0x41 + (char & 0xF))
        // The wildcard "*" has ASCII value 0x2A
        // Encoded: first nibble 0x2 → 'C' (0x43), second nibble 0xA → 'K' (0x4B)
        val encodedName = ByteArray(32)
        for (i in encodedName.indices step 2) {
            encodedName[i] = 0x43  // 'A' + 2
            encodedName[i + 1] = 0x4B  // 'A' + 10
        }
        buffer.put(encodedName)

        // Null terminator
        buffer.put(0)

        // Type: NBSTAT (Node Status) = 0x0021
        buffer.putShort(0x0021)

        // Class: IN = 0x0001
        buffer.putShort(0x0001)

        return buffer.array()
    }

    data class NbnsDevice(
        val ip: String,
        val netbiosName: String?,
        val macAddress: String?
    )

    suspend fun discover(
        broadcastAddress: String = "255.255.255.255",
        timeoutMs: Int = 3000,
        onResult: (NbnsDevice) -> Unit
    ) = withContext(Dispatchers.IO) {
        val socket = try {
            DatagramSocket().apply {
                broadcast = true
                soTimeout = 1000
                reuseAddress = true
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to create socket", e)
            return@withContext
        }

        try {
            val requestBytes = buildNbstatRequest()
            val broadcastAddr = InetAddress.getByName(broadcastAddress)
            val requestPacket = DatagramPacket(
                requestBytes, requestBytes.size, broadcastAddr, nbnsPort
            )

            // Send query
            socket.send(requestPacket)

            val buffer = ByteArray(1024)
            val deadline = System.currentTimeMillis() + timeoutMs
            val seenIps = mutableSetOf<String>()

            while (System.currentTimeMillis() < deadline) {
                val response = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(response)
                    val ip = response.address.hostAddress ?: continue
                    if (ip in seenIps) continue
                    seenIps.add(ip)

                    val data = response.data
                    val length = response.length
                    val parsed = parseNbstatResponse(data, length) ?: continue

                    onResult(NbnsDevice(ip = ip, netbiosName = parsed.name, macAddress = parsed.mac))
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                } catch (e: Exception) {
                    Log.e(tag, "Error receiving NBNS response", e)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "NBNS discovery error", e)
        } finally {
            socket.close()
        }
    }

    /**
     * Sends a direct NetBIOS Node Status query to a specific IP address.
     * Devices responding to NetBIOS return their hardware MAC address in the packet payload.
     */
    suspend fun queryHost(ip: String, timeoutMs: Int = 1000): NbnsDevice? = withContext(Dispatchers.IO) {
        val socket = try {
            DatagramSocket().apply { soTimeout = timeoutMs }
        } catch (_: Exception) { return@withContext null }

        try {
            val requestBytes = buildNbstatRequest()
            val targetAddr = InetAddress.getByName(ip)
            val requestPacket = DatagramPacket(requestBytes, requestBytes.size, targetAddr, nbnsPort)
            socket.send(requestPacket)

            val buffer = ByteArray(1024)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            val data = response.data
            val length = response.length
            val parsed = parseNbstatResponse(data, length) ?: return@withContext null
            NbnsDevice(ip = ip, netbiosName = parsed.name, macAddress = parsed.mac)
        } catch (_: Exception) {
            null
        } finally {
            socket.close()
        }
    }

    private data class ParsedNbns(val name: String?, val mac: String?)

    private fun parseNbstatResponse(data: ByteArray, length: Int): ParsedNbns? {
        if (length < 57) return null

        var offset = 0

        // Skip transaction ID (2), flags (2), question count (2),
        // answer count (2), authority count (2), additional count (2) = 12 bytes
        offset = 12

        // Skip the question name (encoded name = 32 bytes + null terminator = 33 bytes)
        // Plus type (2) + class (2) = 37 bytes total for the question
        // Actually, the question name starts at offset 12. The encoded name is 32 bytes + 1 null = 33.
        // Then type (2) + class (2) = 4. Total question = 37 bytes.
        offset += 37

        // Now we're at the answer section.
        // The answer starts with a name pointer (2 bytes, typically 0xC00C)
        offset += 2

        // Type (2 bytes) - should be 0x0021 (NBSTAT)
        if (offset + 2 > length) return null
        offset += 2

        // Class (2 bytes)
        if (offset + 2 > length) return null
        offset += 2

        // TTL (4 bytes)
        if (offset + 4 > length) return null
        offset += 4

        // Data length (2 bytes)
        if (offset + 2 > length) return null
        val dataLength = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
        offset += 2

        if (offset + dataLength > length) return null

        // Number of names (1 byte)
        if (offset >= length) return null
        val numNames = data[offset].toInt() and 0xFF
        offset += 1

        // Each name entry is 18 bytes: 15 bytes name + 1 byte suffix + 2 bytes flags
        var firstName: String? = null
        for (i in 0 until numNames) {
            if (offset + 18 > length) break
            val nameBytes = data.copyOfRange(offset, offset + 15)
            val name = String(nameBytes, Charsets.ISO_8859_1).trim().trimEnd('\u0000')
            if (firstName == null && name.isNotEmpty() && !name.startsWith("\u0000")) {
                firstName = name
            }
            offset += 18
        }

        // After the name table, the MAC address (6 bytes) follows
        if (offset + 6 <= length) {
            val macBytes = data.copyOfRange(offset, offset + 6)
            val mac = macBytes.joinToString(":") { "%02X".format(it) }
            return ParsedNbns(firstName, mac)
        }

        return ParsedNbns(firstName, null)
    }
}
