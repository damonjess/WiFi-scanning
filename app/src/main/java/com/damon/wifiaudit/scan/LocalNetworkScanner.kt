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
    private val maxConcurrentHosts: Int = 64,
    private val portConnectTimeoutMs: Int = 300,
    private val discoveryPorts: List<Int> = listOf(80, 443, 22, 445, 139),
    private val fullPortSet: List<Int> = listOf(21, 22, 23, 25, 53, 80, 110, 139, 443, 445, 3389, 8080, 8443)
) {

    /**
     * Scans a /24 (or custom range) subnet given a base like "192.168.1".
     * Returns only hosts that responded on at least one port.
     */
    suspend fun scanSubnet(
        baseIp: String,
        hostRange: IntRange = 1..254,
        onHostFound: ((HostScanResult) -> Unit)? = null
    ): List<HostScanResult> = coroutineScope {
        val semaphore = Semaphore(maxConcurrentHosts)
        val results = mutableListOf<HostScanResult>()
        val mutex = Mutex()

        val jobs = hostRange.map { lastOctet ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val ip = "$baseIp.$lastOctet"
                    val result = scanHost(ip)
                    if (result.isAlive) {
                        mutex.withLock { results.add(result) }
                        onHostFound?.invoke(result)
                    }
                }
            }
        }
        jobs.awaitAll()
        results.sortedBy { it.ipAddress.substringAfterLast(".").toIntOrNull() ?: 0 }
    }

    /**
     * Probes a single host: discovery ports first (fast alive check), then
     * — only if alive — a full port sweep. ARP is read only after some
     * traffic has already been sent to the host, so the kernel table has
     * a chance to populate.
     */
    private suspend fun scanHost(ip: String): HostScanResult = coroutineScope {
        val startTime = System.currentTimeMillis()

        // Stage 1: quick discovery sweep to determine liveness
        val discoveryHits = discoveryPorts.map { port ->
            async(Dispatchers.IO) { port to isPortOpen(ip, port) }
        }.awaitAll().filter { it.second }.map { it.first }

        val isAlive = discoveryHits.isNotEmpty()
        if (!isAlive) {
            return@coroutineScope HostScanResult(
                ipAddress = ip, isAlive = false, openPorts = emptyList(),
                macAddress = null, responseTimeMs = System.currentTimeMillis() - startTime
            )
        }

        // Stage 2: full port sweep only for confirmed-alive hosts
        val remainingPorts = fullPortSet - discoveryHits.toSet()
        val extraHits = remainingPorts.map { port ->
            async(Dispatchers.IO) { port to isPortOpen(ip, port) }
        }.awaitAll().filter { it.second }.map { it.first }

        val allOpenPorts = (discoveryHits + extraHits).sorted()

        // ARP table now has an entry for this IP since we just connected to it
        val mac = ArpCacheReader.macForIp(ip)

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
