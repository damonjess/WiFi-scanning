package com.damon.wifiaudit.scan

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class LocalNetworkScanner(
    private val maxConcurrentHosts: Int = 16,
    private val portConnectTimeoutMs: Int = 300,
    private val discoveryPorts: List<Int> = listOf(80, 443, 22, 445, 139, 62078, 8008, 8080, 9100, 5000, 5353),
    private val fullPortSet: List<Int> = listOf(21, 22, 23, 25, 53, 80, 110, 139, 443, 445, 3389, 5000, 8008, 8080, 8443, 9100)
) {

    /**
     * Scans a /24 (or custom range) subnet given a base like "192.168.1".
     * Returns only hosts that responded on at least one port or are in ARP.
     */
    suspend fun scanSubnet(
        baseIp: String,
        hostRange: IntRange = 1..254,
        onHostFound: ((HostScanResult) -> Unit)? = null
    ): List<HostScanResult> = coroutineScope {
        val semaphore = Semaphore(maxConcurrentHosts)
        val results = mutableMapOf<String, HostScanResult>()
        val mutex = Mutex()

        // 1. Check existing ARP cache for hosts that might already be known
        val initialArp = ArpCacheReader.readArpTable()
        initialArp.forEach { (ip, mac) ->
            if (ip.startsWith(baseIp)) {
                val r = HostScanResult(ip, true, emptyList(), mac, 0)
                results[ip] = r
                onHostFound?.invoke(r)
            }
        }

        // 2. Scan the range
        val jobs = hostRange.map { lastOctet ->
            async(Dispatchers.IO) {
                val ip = "$baseIp.$lastOctet"
                semaphore.withPermit {
                    val result = scanHost(ip)
                    if (result.isAlive) {
                        mutex.withLock { results[ip] = result }
                        onHostFound?.invoke(result)
                    }
                }
            }
        }
        jobs.awaitAll()
        results.values.sortedBy { it.ipAddress.substringAfterLast(".").toIntOrNull() ?: 0 }
    }

    /**
     * Probes a single host: discovery ports first (fast alive check), then
     * — only if alive — a full port sweep.
     */
    private suspend fun scanHost(ip: String): HostScanResult = coroutineScope {
        val startTime = System.currentTimeMillis()

        // Sequential, not parallel, per host to conserve file descriptors
        val discoveryHits = discoveryPorts.filter { port ->
            isPortOpen(ip, port)
        }

        val mac = ArpCacheReader.macForIp(ip)
        val isAlive = discoveryHits.isNotEmpty() || mac != null

        if (!isAlive) {
            return@coroutineScope HostScanResult(
                ipAddress = ip, isAlive = false, openPorts = emptyList(),
                macAddress = null, responseTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val remainingPorts = fullPortSet - discoveryHits.toSet()
        val extraHits = remainingPorts.filter { port -> isPortOpen(ip, port) }

        val allOpenPorts = (discoveryHits + extraHits).sorted()

        HostScanResult(
            ipAddress = ip,
            isAlive = true,
            openPorts = allOpenPorts,
            macAddress = mac,
            responseTimeMs = System.currentTimeMillis() - startTime
        )
    }

    private fun isPortOpen(ip: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), portConnectTimeoutMs)
                true
            }
        } catch (e: SocketTimeoutException) {
            false
        } catch (e: Exception) {
            false
        }
    }
}
