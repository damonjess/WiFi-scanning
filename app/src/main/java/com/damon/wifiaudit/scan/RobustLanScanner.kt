package com.damon.wifiaudit.scan

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.damon.wifiaudit.vendor.OuiVendorLookup
import com.damon.wifiaudit.watchdog.SurveillanceDeviceWatchdog
import kotlinx.coroutines.*
import java.io.FileReader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class RobustLanScanner(private val context: Context) {

    private val tag = "RobustLanScanner"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Fallback top 10 ports for unhinted hosts
    val fallbackPorts = intArrayOf(80, 443, 22, 445, 554, 8080, 8443, 23, 21, 1900)

    // Full set of ports commonly used by CCTV / IP cameras and vulnerable services
    private val probePorts = intArrayOf(
        80, 81, 82, 83, 84, 85, 86, 87, 88, 89,
        443, 554, 8554, 8000, 8001, 8080, 8443, 8899, 37777, 34567,
        21, 22, 23, 111, 135, 139, 445, 161, 5000, 5001,
        1024, 1025, 1026, 1027, 1028, 1029, 1030,
        32400, 8008, 8009, 5353, 1900
    )

    data class Device(
        val ip: String,
        val mac: String? = null,
        val hostname: String? = null,
        val openPorts: List<Int> = emptyList(),
        val source: String = "unknown",
        val vendor: String? = null,
        val securityMatches: List<SurveillanceDeviceWatchdog.Match> = emptyList()
    )

    fun scan(
        timeoutMs: Int = 400,
        hostPortsMap: Map<String, IntArray>? = null,
        onResult: suspend (Device) -> Unit,
        onProgress: suspend (scanned: Int, total: Int) -> Unit,
        onFinished: suspend () -> Unit
    ): Job = scope.launch {

        val targets = enumerateTargets()
        if (targets.isEmpty()) {
            Log.w(tag, "No targets generated")
            onFinished()
            return@launch
        }

        val foundIps = ConcurrentHashMap.newKeySet<String>()
        val progress = AtomicInteger(0)
        val total = targets.size

        Log.i(tag, "Scanning $total hosts with timeout ${timeoutMs}ms…")

        val socketSemaphore = Semaphore(128)
        val hostSemaphore = Semaphore(32)

        targets.map { ip ->
            launch {
                hostSemaphore.withPermit {
                    val customPorts = hostPortsMap?.get(ip)
                    val dev = probeHost(ip, timeoutMs, socketSemaphore, customPorts)
                    if (dev != null && foundIps.add(ip)) {
                        onResult(dev)
                    }
                    val done = progress.incrementAndGet()
                    if (done % 10 == 0 || done == total) {
                        onProgress(done, total)
                    }
                }
            }
        }.joinAll()

        onFinished()
    }

    /**
     * Probes a single host using an adaptive or default port list.
     * Fingerprinting (HTTP/RTSP/FTP) is parallelized as soon as the respective port is found open.
     * ICMP is performed before targeted ARP lookup for hosts with zero open ports.
     */
    private suspend fun probeHost(
        ip: String,
        timeoutMs: Int,
        socketSemaphore: Semaphore,
        customPorts: IntArray? = null
    ): Device? = coroutineScope {
        val openPorts = mutableListOf<Int>()
        var httpVendorJob: Deferred<String?>? = null
        var rtspVendorJob: Deferred<String?>? = null
        var ftpVendorJob: Deferred<String?>? = null

        val portsToProbe = customPorts ?: fallbackPorts

        val portJobs = portsToProbe.map { port ->
            async(Dispatchers.IO) {
                socketSemaphore.withPermit {
                    if (tcpConnect(ip, port, timeoutMs)) {
                        synchronized(openPorts) { openPorts.add(port) }
                        synchronized(openPorts) {
                            if ((port == 80 || port == 8080) && httpVendorJob == null) {
                                httpVendorJob = async(Dispatchers.IO) { httpFingerprint(ip, port) }
                            } else if (port == 554 && rtspVendorJob == null) {
                                rtspVendorJob = async(Dispatchers.IO) { rtspFingerprint(ip, 554) }
                            } else if (port == 21 && ftpVendorJob == null) {
                                ftpVendorJob = async(Dispatchers.IO) { ftpFingerprint(ip, 21) }
                            }
                        }
                    }
                }
            }
        }
        portJobs.awaitAll()

        var mac = ArpCacheReader.readArpTable()[ip]
        var isReachable = false

        if (openPorts.isEmpty() && mac == null) {
            // Do ICMP first (cheaper), then ARP-probe only if ICMP fails
            isReachable = try {
                InetAddress.getByName(ip).isReachable(timeoutMs.coerceAtLeast(500))
            } catch (_: Exception) {
                false
            }
            if (!isReachable) {
                mac = ArpCacheReader.resolveMacWithProbe(ip)
            }
        } else if (openPorts.isNotEmpty() && mac == null) {
            mac = ArpCacheReader.resolveMacWithProbe(ip)
        }

        val isAlive = openPorts.isNotEmpty() || mac != null || isReachable
        if (!isAlive) return@coroutineScope null

        val hostname = resolveHostname(ip)

        val httpVendor = httpVendorJob?.await()
        val rtspVendor = rtspVendorJob?.await()
        val ftpVendor = ftpVendorJob?.await()

        val vendor = mac?.let { OuiVendorLookup.lookup(it) }
            ?: httpVendor
            ?: rtspVendor
            ?: ftpVendor
            ?: guessVendorFromHostname(hostname)
            ?: mac?.let { guessVendorFromMac(it) }

        val wifiMatch = SurveillanceDeviceWatchdog.classifyWifi(hostname ?: "", vendor)
        val vulnMatches = SurveillanceDeviceWatchdog.analyzeVulnerabilities(vendor, openPorts)
        val allMatches = (listOfNotNull(wifiMatch) + vulnMatches).distinctBy { it.category to it.matchedOn }

        Device(
            ip = ip,
            mac = mac,
            hostname = hostname,
            openPorts = openPorts.sorted(),
            source = if (openPorts.isNotEmpty()) "tcp" else if (isReachable) "icmp" else "arp",
            vendor = vendor,
            securityMatches = allMatches
        )
    }

    private suspend fun ftpFingerprint(ip: String, port: Int): String? {
        return withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 1500)
                    socket.getInputStream().bufferedReader().use { reader ->
                        val banner = reader.readLine()?.lowercase() ?: ""
                        when {
                            banner.contains("d-link") -> "D-Link"
                            banner.contains("tp-link") -> "TP-Link"
                            banner.contains("vs-ftp") || banner.contains("vsftpd") -> "Linux/NAS"
                            banner.contains("filezilla") -> "FileZilla Server"
                            else -> null
                        }
                    }
                }
            } catch (_: Exception) { null }
        }
    }

    private suspend fun rtspFingerprint(ip: String, port: Int): String? {
        return withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 1500)
                    val out = socket.getOutputStream()
                    val request = "OPTIONS rtsp://$ip:$port RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: WiFiAudit\r\n\r\n"
                    out.write(request.toByteArray())

                    val reader = socket.getInputStream().bufferedReader()
                    val sb = StringBuilder()
                    var line: String?
                    while (true) {
                        line = reader.readLine()
                        if (line.isNullOrEmpty()) break
                        sb.append(line).append("\n")
                        if (sb.length > 2000) break
                    }

                    val response = sb.toString().lowercase()
                    when {
                        response.contains("server: hikvision") || response.contains("hikvision") -> "Hikvision"
                        response.contains("server: dahua") || response.contains("dahua") -> "Dahua"
                        response.contains("server: axis") || response.contains("axis") -> "Axis"
                        response.contains("server: d-link") || response.contains("dlink") -> "D-Link"
                        response.contains("server: reolink") || response.contains("reolink") -> "Reolink"
                        response.contains("server: foscam") || response.contains("foscam") -> "Foscam"
                        response.contains("live555") -> "Common IP Camera"
                        else -> null
                    }
                }
            } catch (_: Exception) { null }
        }
    }

    private suspend fun httpFingerprint(ip: String, port: Int): String? {
        return try {
            withContext(Dispatchers.IO) {
                withTimeout(1500) {
                    val url = java.net.URL("http://$ip:$port")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 1000
                    conn.readTimeout = 1000
                    conn.instanceFollowRedirects = true

                    val server = conn.getHeaderField("Server")?.lowercase()
                    val title = try {
                        conn.inputStream.bufferedReader().use { it.readText() }
                            .let { html ->
                                Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
                                    .find(html)?.groupValues?.get(1)
                            }
                    } catch (_: Exception) {
                        null
                    }

                    conn.disconnect()

                    when {
                        server?.contains("hikvision") == true -> "Hikvision"
                        server?.contains("dahua") == true -> "Dahua"
                        server?.contains("netgear") == true -> "NETGEAR"
                        server?.contains("router") == true -> "Router"
                        title?.contains("camera") == true -> "IP Camera"
                        title?.contains("d-link") == true -> "D-Link"
                        else -> null
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun guessVendorFromHostname(hostname: String?): String? {
        if (hostname.isNullOrBlank()) return null
        val h = hostname.lowercase()
        return when {
            h.contains("hikvision") || h.contains("ds-") -> "Hikvision"
            h.contains("dahua") || h.contains("ipc-") -> "Dahua"
            h.contains("axis") -> "Axis Communications"
            h.contains("foscam") -> "Foscam"
            h.contains("tp-link") || h.contains("tplink") -> "TP-Link"
            h.contains("netgear") -> "NETGEAR"
            h.contains("vodafone") -> "Vodafone"
            h.contains("samsung") -> "Samsung"
            h.contains("xiaomi") || h.contains("mi-") -> "Xiaomi"
            h.contains("google") || h.contains("nest") -> "Google"
            h.contains("amazon") || h.contains("echo") || h.contains("fire") -> "Amazon"
            h.contains("apple") || h.contains("iphone") || h.contains("ipad") -> "Apple"
            h.contains("dlink") || h.contains("d-link") || h.contains("dcs-") -> "D-Link"
            h.contains("ubiquiti") || h.contains("unifi") -> "Ubiquiti"
            h.contains("reolink") -> "Reolink"
            h.contains("amcrest") -> "Amcrest"
            h.contains("wyze") -> "Wyze"
            h.contains("ring") -> "Ring"
            h.contains("arlo") -> "Arlo"
            h.contains("ezviz") -> "EZVIZ"
            h.contains(" lorex") -> "Lorex"
            h.contains("swann") -> "Swann"
            h.contains("annke") -> "Annke"
            h.contains("zosi") -> "ZOSI"
            h.contains("icsee") || h.contains("xmeye") || h.contains("xmei") -> "XMEye / iCSee"
            h.contains("mysimplelink") -> "Texas Instruments (IoT)"
            h.contains("int6400") -> "Atheros Powerline"
            h.contains("raspberry") -> "Raspberry Pi"
            h.contains("synology") -> "Synology NAS"
            h.contains("qnap") -> "QNAP NAS"
            h.contains("western-digital") || h.contains("wd-") -> "Western Digital"
            h.contains("sonos") -> "Sonos"
            h.contains("roku") -> "Roku"
            h.contains("chromecast") -> "Chromecast"
            h.contains("philips-hue") || h.contains("hue-bridge") -> "Philips Hue"
            h.contains("esp32") || h.contains("espressif") -> "Espressif (IoT)"
            h.contains("shelly") -> "Shelly IoT"
            h.contains("wemo") -> "Belkin Wemo"
            else -> null
        }
    }

    private fun guessVendorFromMac(mac: String): String? {
        val clean = mac.replace(":", "").replace("-", "").uppercase()
        return when {
            // D-Link
            clean.startsWith("6C198F") || clean.startsWith("000F3D") ||
            clean.startsWith("C0A0BB") || clean.startsWith("F07D68") ||
            clean.startsWith("001195") || clean.startsWith("B8A386") -> "D-Link"
            // Cisco / Linksys
            clean.startsWith("000C41") || clean.startsWith("0016B6") ||
            clean.startsWith("001C10") || clean.startsWith("001839") -> "Cisco-Linksys"
            clean.startsWith("00000C") || clean.startsWith("001517") ||
            clean.startsWith("0017C5") || clean.startsWith("002213") -> "Cisco"
            // Hikvision
            clean.startsWith("00E0FC") || clean.startsWith("00C0CA") ||
            clean.startsWith("4447CC") || clean.startsWith("C05627") ||
            clean.startsWith("2857BE") || clean.startsWith("3CE9F7") -> "Hikvision"
            // Dahua
            clean.startsWith("FC4D96") || clean.startsWith("E0508B") ||
            clean.startsWith("A0BD1D") || clean.startsWith("5C04A0") ||
            clean.startsWith("38AF46") || clean.startsWith("4C11BF") -> "Dahua"
            // Samsung
            clean.startsWith("BC1485") || clean.startsWith("BC7ABF") ||
            clean.startsWith("B0C4E7") || clean.startsWith("500BC6") ||
            clean.startsWith("8C7712") || clean.startsWith("883660") ||
            clean.startsWith("784040") || clean.startsWith("A02195") -> "Samsung"
            // Apple
            clean.startsWith("AC87A3") || clean.startsWith("1C1A68") ||
            clean.startsWith("2CBE08") || clean.startsWith("38C986") ||
            clean.startsWith("4C7C5F") || clean.startsWith("5C97F3") ||
            clean.startsWith("A4D18C") || clean.startsWith("F81ED3") -> "Apple"
            // Google / Nest
            clean.startsWith("18B430") || clean.startsWith("3C5AB4") ||
            clean.startsWith("64167F") || clean.startsWith("7C2CE4") -> "Google"
            // Xiaomi
            clean.startsWith("009EC1") || clean.startsWith("286C07") ||
            clean.startsWith("7C1DD9") || clean.startsWith("FC7C02") -> "Xiaomi"
            // TP-Link
            clean.startsWith("50C7BF") || clean.startsWith("C0C9E3") ||
            clean.startsWith("14CC20") || clean.startsWith("60E327") ||
            clean.startsWith("A0F3C1") || clean.startsWith("98DAC4") -> "TP-Link"
            // Netgear
            clean.startsWith("28C68E") || clean.startsWith("001E2A") ||
            clean.startsWith("94163E") || clean.startsWith("841B5E") -> "Netgear"
            // ASUS
            clean.startsWith("04D4C4") || clean.startsWith("2C4D54") ||
            clean.startsWith("FC3497") || clean.startsWith("F832E4") -> "ASUS"
            // Synology / QNAP
            clean.startsWith("001132") -> "Synology"
            clean.startsWith("001D63") -> "QNAP"
            // Raspberry Pi
            clean.startsWith("B827EB") || clean.startsWith("E45F01") ||
            clean.startsWith("DCA632") -> "Raspberry Pi"
            // Amazon
            clean.startsWith("B0C554") || clean.startsWith("747548") ||
            clean.startsWith("F0D2F1") || clean.startsWith("68A40E") -> "Amazon"
            // Espressif (ESP32/ESP8266)
            clean.startsWith("240AC4") || clean.startsWith("30AEA4") ||
            clean.startsWith("84F3EB") || clean.startsWith("A4CF12") ||
            clean.startsWith("7C9EBD") || clean.startsWith("CC50E3") -> "Espressif"
            // Tuya
            clean.startsWith("84CCAD") || clean.startsWith("D4A651") ||
            clean.startsWith("CC8CBF") || clean.startsWith("C44EAC") -> "Tuya"
            // Reolink
            clean.startsWith("EC71DB") || clean.startsWith("9C8E99") -> "Reolink"
            // Ubiquiti
            clean.startsWith("24A43C") || clean.startsWith("FCEC38") ||
            clean.startsWith("802AA8") || clean.startsWith("B4FBE4") -> "Ubiquiti"
            // Roku
            clean.startsWith("D8DCE9") || clean.startsWith("CC6EA4") ||
            clean.startsWith("B0A73B") || clean.startsWith("AC3A7A") -> "Roku"
            // Sonos
            clean.startsWith("000E58") || clean.startsWith("B8E937") ||
            clean.startsWith("F40343") -> "Sonos"
            // Philips Hue
            clean.startsWith("001788") || clean.startsWith("ECB5FA") ||
            clean.startsWith("EC1BBD") -> "Philips Hue"
            // Foscam
            clean.startsWith("001CFA") -> "Foscam"
            // Wyze
            clean.startsWith("2CFAA2") -> "Wyze"
            // Arlo
            clean.startsWith("DC447D") -> "Arlo"
            // Amcrest
            clean.startsWith("A0CC2B") || clean.startsWith("C4AD34") -> "Amcrest"
            // Nintendo
            clean.startsWith("0009BF") -> "Nintendo"
            // NVIDIA
            clean.startsWith("00044B") -> "NVIDIA"
            // Intel
            clean.startsWith("0007CB") -> "Intel"
            // MikroTik
            clean.startsWith("4C5E0C") -> "MikroTik"
            // SpaceX Starlink
            clean.startsWith("00A0C6") -> "SpaceX Starlink"
            else -> null
        }
    }

    private fun tcpConnect(ip: String, port: Int, timeout: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), timeout)
                true
            }
        } catch (_: Exception) { false }
    }

    private fun enumerateTargets(): List<String> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        if (network != null) {
            try {
                val linkProps = cm.getLinkProperties(network)
                val ipv4 = linkProps?.linkAddresses?.firstOrNull { it.address is Inet4Address }
                if (ipv4 != null) {
                    val ipStr = ipv4.address.hostAddress ?: ""
                    if (ipStr.isNotEmpty()) {
                        val originalPrefix = ipv4.prefixLength
                        // Cap at /24 to prevent 65k-host scans
                        val prefix = if (originalPrefix < 24) 24 else originalPrefix
                        
                        val ipInt = ipToInt(ipv4.address as Inet4Address)
                        val mask = if (prefix == 32) -1 else (0xFFFFFFFF.toInt() shl (32 - prefix))
                        val networkAddr = ipInt and mask
                        val broadcastAddr = networkAddr or mask.inv()
                        
                        val targets = mutableListOf<String>()
                        for (addr in (networkAddr + 1) until broadcastAddr) {
                            if (addr != ipInt) {
                                targets.add(intToIp(addr))
                            }
                        }
                        return targets
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to read link properties", e)
            }
        }
        return listOf("192.168.1", "192.168.0", "10.0.0").flatMap { base ->
            (1..254).map { "$base.$it" }
        }
    }

    /**
     * Computes the directed broadcast address for the current subnet.
     * E.g. for 192.168.1.42/24 this returns 192.168.1.255.
     * Falls back to 255.255.255.255 if the subnet can't be determined.
     */
    fun getBroadcastAddress(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        if (network != null) {
            try {
                val linkProps = cm.getLinkProperties(network)
                val ipv4 = linkProps?.linkAddresses?.firstOrNull { it.address is Inet4Address }
                if (ipv4 != null) {
                    val ip = ipv4.address.hostAddress ?: return "255.255.255.255"
                    val prefixLength = ipv4.prefixLength
                    if (prefixLength > 0 && prefixLength <= 32) {
                        val ipInt = ipToInt(ipv4.address as Inet4Address)
                        val mask = if (prefixLength == 32) -1 else (0xFFFFFFFF.toInt() shl (32 - prefixLength))
                        val networkAddr = ipInt and mask
                        val broadcast = networkAddr or mask.inv()
                        return intToIp(broadcast)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to compute broadcast address", e)
            }
        }
        return "255.255.255.255"
    }

    private fun ipToInt(addr: Inet4Address): Int {
        val bytes = addr.address
        return ((bytes[0].toInt() and 0xFF) shl 24) or
               ((bytes[1].toInt() and 0xFF) shl 16) or
               ((bytes[2].toInt() and 0xFF) shl 8) or
               (bytes[3].toInt() and 0xFF)
    }

    private fun intToIp(value: Int): String {
        return "${(value shr 24) and 0xFF}.${(value shr 16) and 0xFF}.${(value shr 8) and 0xFF}.${value and 0xFF}"
    }

    private fun readArp(ip: String): String? {
        return ArpCacheReader.resolveMacWithProbe(ip)?.let { ArpCacheReader.normalizeMac(it) }
    }

    private fun resolveHostname(ip: String): String? {
        return try {
            val name = InetAddress.getByName(ip).canonicalHostName
            if (name != ip) name else null
        } catch (_: Exception) { null }
    }

    /**
     * Cancel any in-flight scan without destroying the permanent scope.
     * Called by [DiscoveryCoordinator.stop()].
     */
    fun cancelScan() {
        scope.coroutineContext[Job]?.cancelChildren()
    }
}
