package com.damon.wifiaudit.scan

import android.content.Context
import android.util.Log
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.data.WardrivingRepository
import com.damon.wifiaudit.vendor.OuiVendorLookup
import com.damon.wifiaudit.watchdog.SurveillanceDeviceWatchdog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class DiscoveryCoordinator(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: DiscoveryCoordinator? = null

        fun getInstance(context: Context): DiscoveryCoordinator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DiscoveryCoordinator(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val tag = "DiscoveryCoordinator"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scanJob: Job? = null

    private val scanner = RobustLanScanner(context)
    private val ssdpHelper = SsdpDiscoveryHelper()
    private val mdnsHelper = MdnsDiscoveryHelper(context)
    private val p2pHelper = P2pDiscoveryHelper()

    private val _devices = MutableStateFlow<List<RobustLanScanner.Device>>(emptyList())
    val devices: StateFlow<List<RobustLanScanner.Device>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _progress = MutableStateFlow(0 to 0) // current, total
    val progress: StateFlow<Pair<Int, Int>> = _progress.asStateFlow()

    private val _discoveryFlow = MutableSharedFlow<DiscoveryResult>()
    val discoveryFlow: SharedFlow<DiscoveryResult> = _discoveryFlow.asSharedFlow()

    private val deviceMap = ConcurrentHashMap<String, RobustLanScanner.Device>()

    fun start() {
        if (_scanning.value) return
        _scanning.value = true
        deviceMap.clear()
        _devices.value = emptyList()
        _progress.value = 0 to 0

        // Cache the ARP table once per scan to avoid re-reading /proc/net/arp
        // on every single discovery result.
        val arpCache = ArpCacheReader.readArpTable()

        scanJob = scope.launch {
            var ssdpJob: Job? = null
            var mdnsJob: Job? = null
            var p2pJob: Job? = null
            var tcpScanJob: Job? = null

            try {
                // Run SSDP in parallel
                ssdpJob = launch {
                    try {
                        ssdpHelper.discover(onResult = { ip, info ->
                            addOrMerge(
                                RobustLanScanner.Device(
                                    ip = ip,
                                    mac = null,
                                    hostname = info,
                                    openPorts = emptyList(),
                                    source = "ssdp",
                                    vendor = scanner.guessVendorFromHostname(info)
                                ),
                                arpCache = arpCache
                            )
                        })
                    } catch (e: Exception) {
                        Log.e(tag, "SSDP discovery error", e)
                    }
                }

                // Run mDNS in parallel
                mdnsJob = launch {
                    try {
                        val serviceTypes = listOf("_http._tcp.", "_rtsp._tcp.", "_axis-video._tcp.", "_onvif._tcp.", "_workstation._tcp.")
                        serviceTypes.forEach { type ->
                            launch {
                                mdnsHelper.discoverServices(type).collect { (ip, info) ->
                                    if (ip == null) return@collect
                                    addOrMerge(
                                        RobustLanScanner.Device(
                                            ip = ip,
                                            mac = null,
                                            hostname = info,
                                            openPorts = emptyList(),
                                            source = "mdns",
                                            vendor = scanner.guessVendorFromHostname(info)
                                        ),
                                        arpCache = arpCache
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "mDNS discovery error", e)
                    }
                }

                // Run P2P Discovery in parallel
                p2pJob = launch {
                    try {
                        p2pHelper.discover().collect { (ip, info) ->
                            addOrMerge(
                                RobustLanScanner.Device(
                                    ip = ip,
                                    mac = null,
                                    hostname = info,
                                    openPorts = emptyList(),
                                    source = "p2p",
                                    vendor = null
                                ),
                                arpCache = arpCache
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "P2P discovery error", e)
                    }
                }

                // Run active TCP/ICMP scan
                tcpScanJob = scanner.scan(
                    timeoutMs = 400,
                    onResult = { addOrMerge(it, arpCache = arpCache) },
                    onProgress = { cur, tot -> _progress.value = cur to tot },
                    onFinished = { }
                )

                tcpScanJob.join()
                delay(1500)
                ssdpJob.cancelAndJoin()
                mdnsJob.cancelAndJoin()
                p2pJob.cancelAndJoin()

                // Refresh the ARP cache after TCP probes have populated it,
                // then enrich any devices still missing MAC addresses.
                val refreshedArp = ArpCacheReader.readArpTable()
                enrichMacsFromArp(refreshedArp)
            } finally {
                // Ensure cleanup happens even if an exception or cancellation occurs.
                ssdpJob?.cancel()
                mdnsJob?.cancel()
                p2pJob?.cancel()
                tcpScanJob?.cancel()
                _scanning.value = false
                scanJob = null
            }
        }
    }

    private fun enrichMacsFromArp(arpCache: Map<String, String>) {
        if (arpCache.isEmpty()) return

        var changed = false
        deviceMap.forEach { (ip, dev) ->
            if (dev.mac == null) {
                arpCache[ip]?.let { mac ->
                    val vendor = OuiVendorLookup.lookup(mac) ?: dev.vendor
                    val wifiMatch = SurveillanceDeviceWatchdog.classifyWifi(dev.hostname ?: "", vendor)
                    val vulnMatches = SurveillanceDeviceWatchdog.analyzeVulnerabilities(vendor, dev.openPorts)
                    val allMatches = (dev.securityMatches + listOfNotNull(wifiMatch) + vulnMatches).distinctBy { it.category to it.matchedOn }
                    
                    deviceMap[ip] = dev.copy(mac = mac, vendor = vendor, securityMatches = allMatches)
                    changed = true

                    val hostname = dev.hostname ?: ip
                    if (SurveillanceDeviceWatchdog.isRingDevice(hostname, vendor, hostname)) {
                        scope.launch {
                            try {
                                val db = AppDatabase.getInstance(context)
                                val repository = WardrivingRepository(db)
                                repository.processAndSaveTargetDevice(
                                    macAddress = mac.uppercase(),
                                    deviceName = hostname.takeIf { it != ip } ?: vendor?.let { "$it Ring Device" } ?: "Ring Network Camera",
                                    category = "CAMERA",
                                    rssi = -50,
                                    latitude = null,
                                    longitude = null
                                )
                            } catch (e: Exception) {
                                Log.e(tag, "Failed to save Ring camera from ARP enrichment", e)
                            }
                        }
                    }
                }
            }
        }
        if (changed) {
            _devices.value = deviceMap.values.sortedBy { it.ip }
        }
    }

    private fun addOrMerge(dev: RobustLanScanner.Device, arpCache: Map<String, String> = emptyMap()) {
        val arpMac = dev.mac ?: arpCache[dev.ip]
        val existing = deviceMap[dev.ip]
        
        val currentVendor = dev.vendor ?: (arpMac?.let { OuiVendorLookup.lookup(it) })
            ?: scanner.guessVendorFromHostname(dev.hostname)

        val merged = if (existing != null) {
            val finalMac = arpMac ?: existing.mac
            val finalVendor = currentVendor ?: existing.vendor ?: (finalMac?.let { OuiVendorLookup.lookup(it) })
            val finalPorts = (existing.openPorts + dev.openPorts).distinct().sorted()
            val finalMatches = (existing.securityMatches + dev.securityMatches).distinctBy { it.category to it.matchedOn }

            existing.copy(
                mac = finalMac,
                hostname = dev.hostname ?: existing.hostname,
                openPorts = finalPorts,
                source = if (existing.source.contains(dev.source)) existing.source
                         else "${existing.source},${dev.source}",
                vendor = finalVendor,
                securityMatches = finalMatches
            )
        } else dev.copy(mac = arpMac, vendor = currentVendor)

        deviceMap[dev.ip] = merged
        _devices.value = deviceMap.values.sortedBy { it.ip }

        scope.launch {
            val mac = merged.mac ?: "Unknown"
            val vendor = merged.vendor ?: OuiVendorLookup.lookup(mac)
            val hostname = merged.hostname ?: merged.ip

            if (mac != "Unknown" && SurveillanceDeviceWatchdog.isRingDevice(hostname, vendor, hostname)) {
                try {
                    val db = AppDatabase.getInstance(context)
                    val repository = WardrivingRepository(db)
                    repository.processAndSaveTargetDevice(
                        macAddress = mac.uppercase(),
                        deviceName = hostname.takeIf { it != merged.ip } ?: vendor?.let { "$it Ring Device" } ?: "Ring Network Camera",
                        category = "CAMERA",
                        rssi = -50,
                        latitude = null,
                        longitude = null
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Failed to save Ring camera from LAN scan", e)
                }
            }
            
            // Re-classify security matches with full context
            val wifiMatch = SurveillanceDeviceWatchdog.classifyWifi(merged.hostname ?: "", vendor)
            val vulnMatches = SurveillanceDeviceWatchdog.analyzeVulnerabilities(vendor, merged.openPorts)
            val allMatches = (listOfNotNull(wifiMatch) + vulnMatches).distinctBy { it.category to it.matchedOn }

            val networkDevice = NetworkDevice(
                ip = merged.ip,
                hostname = merged.hostname ?: "Unknown",
                mac = mac,
                vendor = vendor,
                openPorts = merged.openPorts,
                securityMatches = allMatches
            )
            _discoveryFlow.emit(DiscoveryResult(
                ip = merged.ip,
                source = merged.source,
                device = networkDevice
            ))
        }
    }

    fun stop() {
        scanJob?.cancel()
        scanJob = null
        scanner.cancelScan()
        _scanning.value = false
        _progress.value = 0 to 0
    }
}
