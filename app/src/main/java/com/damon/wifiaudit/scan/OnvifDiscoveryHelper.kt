package com.damon.wifiaudit.scan

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * ONVIF WS-Discovery.
 *
 * Many IP cameras (Hikvision, Dahua, Axis, Reolink, Amcrest, etc.) respond to
 * ONVIF WS-Discovery but NOT to SSDP/UPnP. WS-Discovery uses SOAP-over-UDP
 * multicast to 239.255.255.250:3702 — a different multicast group and port
 * than SSDP (which uses 239.255.255.250:1900).
 *
 * This sends a WS-Discovery Probe message and listens for ProbeMatches
 * responses, extracting the device's XAddrs (service URLs), Types, and
 * Scopes (which often contain the device model and manufacturer).
 *
 * Spec: WS-Discovery (Web Services Dynamic Discovery) — devices.oasis-open.org
 */
class OnvifDiscoveryHelper {

    private val tag = "OnvifDiscoveryHelper"
    private val multicastGroup = InetAddress.getByName("239.255.255.250")
    private val wsDiscoveryPort = 3702

    private val probeMessage = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        append("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\"")
        append(" xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\"")
        append(" xmlns:wsd=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\"")
        append(" xmlns:wsdd=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\">")
        append("<soap:Header>")
        append("<wsa:MessageID>urn:uuid:${java.util.UUID.randomUUID()}</wsa:MessageID>")
        append("<wsa:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</wsa:To>")
        append("<wsa:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</wsa:Action>")
        append("</soap:Header>")
        append("<soap:Body>")
        append("<wsd:Probe/>")
        append("</soap:Body>")
        append("</soap:Envelope>")
    }

    data class OnvifDevice(
        val ip: String,
        val xAddrs: String?,
        val types: String?,
        val scopes: String?
    )

    suspend fun discover(
        timeoutMs: Int = 4000,
        onResult: (OnvifDevice) -> Unit
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
            val probeBytes = probeMessage.toByteArray()
            val probePacket = DatagramPacket(
                probeBytes, probeBytes.size, multicastGroup, wsDiscoveryPort
            )

            // Send probe 2 times for reliability
            socket.send(probePacket)
            Thread.sleep(200)
            socket.send(probePacket)

            val buffer = ByteArray(4096)
            val deadline = System.currentTimeMillis() + timeoutMs
            val seenIps = mutableSetOf<String>()

            while (System.currentTimeMillis() < deadline) {
                val response = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(response)
                    val text = String(response.data, 0, response.length)
                    val ip = response.address.hostAddress ?: continue

                    if (ip in seenIps) continue
                    seenIps.add(ip)

                    val parsed = parseProbeMatch(text) ?: continue
                    val device = OnvifDevice(
                        ip = ip,
                        xAddrs = parsed.xAddrs,
                        types = parsed.types,
                        scopes = parsed.scopes
                    )

                    // Derive a human-readable info string from scopes/types
                    val info = buildInfoString(device)
                    onResult(device.copy(ip = ip, xAddrs = parsed.xAddrs, types = info, scopes = parsed.scopes))
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                } catch (e: Exception) {
                    Log.e(tag, "Error receiving ONVIF response", e)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "ONVIF discovery error", e)
        } finally {
            socket.close()
        }
    }

    private data class ParsedResponse(
        val xAddrs: String?,
        val types: String?,
        val scopes: String?
    )

    private fun parseProbeMatch(xml: String): ParsedResponse? {
        // Extract XAddrs — contains the device service URL
        val xAddrs = Regex("XAddrs>(.*?)</[^>]*XAddrs", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)?.trim()

        // Extract Types — e.g. "NetworkVideoTransmitter"
        val types = Regex("Types>(.*?)</[^>]*Types", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)?.trim()
            ?.let { Regex("[a-zA-Z]+:([a-zA-Z]+)").findAll(it).map { m -> m.groupValues[1] }.joinToString(", ") }

        // Extract Scopes — often contains manufacturer/model/onvif://www.onvif.org/Model/...
        val scopes = Regex("Scopes>(.*?)</[^>]*Scopes", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)?.trim()

        if (xAddrs == null && types == null && scopes == null) return null
        return ParsedResponse(xAddrs, types, scopes)
    }

    private fun buildInfoString(device: OnvifDevice): String {
        val parts = mutableListOf<String>()

        // Try to extract model/manufacturer from scopes
        val scopes = device.scopes ?: ""
        val modelMatch = Regex("onvif://www\\.onvif\\.org/Model/([^\\s\"<>]+)", RegexOption.IGNORE_CASE)
            .find(scopes)?.groupValues?.get(1)
        val nameMatch = Regex("onvif://www\\.onvif\\.org/Name/([^\\s\"<>]+)", RegexOption.IGNORE_CASE)
            .find(scopes)?.groupValues?.get(1)
        val hardwareMatch = Regex("onvif://www\\.onvif\\.org/Hardware/([^\\s\"<>]+)", RegexOption.IGNORE_CASE)
            .find(scopes)?.groupValues?.get(1)

        nameMatch?.let { parts.add(it) }
        hardwareMatch?.let { parts.add(it) }
        modelMatch?.let { parts.add("Model: $it") }

        // Add device type if we have it
        device.types?.let { parts.add(it) }

        return if (parts.isEmpty()) "ONVIF Device" else parts.joinToString(" | ")
    }
}
