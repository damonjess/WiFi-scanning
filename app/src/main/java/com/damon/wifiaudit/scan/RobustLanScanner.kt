package com.damon.wifiaudit.scan

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.damon.wifiaudit.vendor.OuiVendorLookup
import com.damon.wifiaudit.watchdog.SurveillanceDeviceWatchdog
import kotlinx.coroutines.*
import java.io.BufferedReader
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

    // Ports commonly used by CCTV / IP cameras and vulnerable services
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

        // Use a single global semaphore to bound total concurrent sockets.
        // Android typically has a ~1024 file descriptor limit; each TCP socket
        // consumes one. With 128 permits and 400ms timeout, we stay well within
        // limits while still achieving good parallelism.
        val socketSemaphore = Semaphore(128)
        // Bound the number of hosts being probed simultaneously to avoid
        // excessive coroutine creation (254–762 hosts × 42 port probes each).
        val hostSemaphore = Semaphore(32)

        targets.map { ip ->
            launch {
                hostSemaphore.withPermit {
                    val dev = probeHost(ip, timeoutMs, socketSemaphore)
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
     * Probes a single host: scans the full [probePorts] set in parallel (bounded
     * by the global [socketSemaphore]) and considers the host alive if ANY port
     * is open, it appears in the ARP cache, or [InetAddress.isReachable] succeeds.
     *
     * This replaces the previous two-phase approach (alive-check then full scan)
     * which doubled the work and missed hosts whose only open ports weren't in
     * the small alive-check set.
     */
    private suspend fun probeHost(ip: String, timeoutMs: Int, socketSemaphore: Semaphore): Device? = coroutineScope {
        val openPorts = mutableListOf<Int>()

        val portJobs = probePorts.map { port ->
            async(Dispatchers.IO) {
                socketSemaphore.withPermit {
                    if (tcpConnect(ip, port, timeoutMs)) {
                        synchronized(openPorts) { openPorts.add(port) }
                    }
                }
            }
        }
        portJobs.awaitAll()

        val mac = readArp(ip)
        val isReachable = if (openPorts.isEmpty() && mac == null) {
            try {
                InetAddress.getByName(ip).isReachable(timeoutMs)
            } catch (_: Exception) {
                false
            }
        } else false

        val isAlive = openPorts.isNotEmpty() || mac != null || isReachable
        if (!isAlive) return@coroutineScope null

        val hostname = resolveHostname(ip)

        val httpPort = when {
            80 in openPorts -> 80
            8080 in openPorts -> 8080
            else -> null
        }

        val httpVendor = httpPort?.let { httpFingerprint(ip, it) }
        val rtspVendor = if (554 in openPorts) rtspFingerprint(ip, 554) else null
        val ftpVendor = if (21 in openPorts) ftpFingerprint(ip, 21) else null

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
            clean.startsWith("6C198F") || clean.startsWith("000F3D") ||
            clean.startsWith("C0A0BB") || clean.startsWith("F07D68") -> "D-Link"
            clean.startsWith("000C41") -> "Cisco-Linksys"
            clean.startsWith("00E0FC") || clean.startsWith("00C0CA") -> "Hikvision"
            clean.startsWith("BC1485") || clean.startsWith("BC7ABF") -> "Samsung"
            clean.startsWith("00000C") -> "Cisco"
            clean.startsWith("B0C4E7") -> "Samsung"
            clean.startsWith("001132") -> "Synology"
            clean.startsWith("001D63") -> "QNAP"
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
                    val ip = ipv4.address.hostAddress ?: ""
                    if (ip.isNotEmpty()) {
                        val base = ip.substring(0, ip.lastIndexOf('.'))
                        return (1..254).map { "$base.$it" }
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
        return try {
            BufferedReader(FileReader("/proc/net/arp")).useLines { lines ->
                lines.map { it.split("\\s+".toRegex()) }
                    .firstOrNull { parts ->
                        parts.size >= 4 &&
                        parts[0] == ip &&
                        !parts[3].equals("00:00:00:00:00:00", ignoreCase = true) &&
                        !parts[3].contains("incomplete", ignoreCase = true)
                    }
                    ?.get(3)
                    ?.uppercase()
            }
        } catch (_: Exception) { null }
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
