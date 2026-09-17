package com.damon.wifiaudit.scan

import android.content.Context
import android.net.wifi.WifiManager
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
    private val onvifHelper = OnvifDiscoveryHelper()
    private val nbnsHelper = NbnsDiscoveryHelper()

    private val _devices = MutableStateFlow<List<RobustLanScanner.Device>>(emptyList())
    val devices: StateFlow<List<RobustLanScanner.Device>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _progress = MutableStateFlow(0 to 0) // current, total
    val progress: StateFlow<Pair<Int, Int>> = _progress.asStateFlow()

    private val _discoveryFlow = MutableSharedFlow<DiscoveryResult>()
    val discoveryFlow: SharedFlow<DiscoveryResult> = _discoveryFlow.asSharedFlow()

    private val deviceMap = ConcurrentHashMap<String, RobustLanScanner.Device>()
    private var multicastLock: WifiManager.MulticastLock? = null

    private fun acquireMulticastLock() {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        multicastLock = wifiManager?.createMulticastLock("WiFiAuditDiscovery")?.apply {
            acquire()
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
    }

    fun start() {
        if (_scanning.value) return
        _scanning.value = true
        deviceMap.clear()
        _devices.value = emptyList()
        _progress.value = 0 to 0

        // Cache the ARP table once per scan to avoid re-reading /proc/net/arp
        // on every single discovery result.
        val arpCache = ArpCacheReader.readArpTable()

        // Immediately seed the device map with all hosts found in the ARP cache.
        // This ensures devices that don't respond to TCP probes or ping but are
        // present in the kernel ARP table are still shown to the user.
        arpCache.forEach { (ip, mac) ->
            val normalizedMac = ArpCacheReader.normalizeMac(mac)
            val vendor = OuiVendorLookup.lookup(normalizedMac)
                ?: guessVendorFromHostname(ip)
            deviceMap[ip] = RobustLanScanner.Device(
                ip = ip,
                mac = normalizedMac,
                hostname = null,
                openPorts = emptyList(),
                source = "arp",
                vendor = vendor
            )
        }
        if (deviceMap.isNotEmpty()) {
            _devices.value = deviceMap.values.sortedBy { it.ip }
        }

        scanJob = scope.launch {
            var ssdpJob: Job? = null
            var mdnsJob: Job? = null
            var p2pJob: Job? = null
            var onvifJob: Job? = null
            var nbnsJob: Job? = null
            var tcpScanJob: Job? = null

            // Acquire multicast lock so the WiFi radio doesn't filter out
            // multicast packets (SSDP, ONVIF WS-Discovery, mDNS).
            acquireMulticastLock()

            try {
                // Run SSDP in parallel
                ssdpJob = launch {
                    try {
                        ssdpHelper.discover(onResult = { ip, info ->
                            launch {
                                val desc = if (info.startsWith("http")) ssdpHelper.fetchDeviceDescription(info) else null
                                val name = desc?.friendlyName ?: desc?.modelName ?: info
                                val vendor = desc?.manufacturer ?: scanner.guessVendorFromHostname(name)
                                addOrMerge(
                                    RobustLanScanner.Device(
                                        ip = ip,
                                        mac = null,
                                        hostname = name,
                                        openPorts = emptyList(),
                                        source = "ssdp",
                                        vendor = vendor
                                    ),
                                    arpCache = arpCache
                                )
                            }
                        })
                    } catch (e: Exception) {
                        Log.e(tag, "SSDP discovery error", e)
                    }
                }

                // Run ONVIF WS-Discovery in parallel — many IP cameras respond
                // to this but NOT to SSDP.
                onvifJob = launch {
                    try {
                        onvifHelper.discover(onResult = { device ->
                            val vendor = device.types?.let { scanner.guessVendorFromHostname(it) }
                            addOrMerge(
                                RobustLanScanner.Device(
                                    ip = device.ip,
                                    mac = null,
                                    hostname = device.types,
                                    openPorts = emptyList(),
                                    source = "onvif",
                                    vendor = vendor
                                ),
                                arpCache = arpCache
                            )
                        })
                    } catch (e: Exception) {
                        Log.e(tag, "ONVIF discovery error", e)
                    }
                }

                // Run NBNS (NetBIOS) discovery in parallel — picks up Windows
                // machines, NAS, printers invisible to TCP port scanning.
                nbnsJob = launch {
                    try {
                        val broadcastAddr = scanner.getBroadcastAddress()
                        nbnsHelper.discover(broadcastAddress = broadcastAddr, onResult = { device ->
                            addOrMerge(
                                RobustLanScanner.Device(
                                    ip = device.ip,
                                    mac = device.macAddress,
                                    hostname = device.netbiosName,
                                    openPorts = emptyList(),
                                    source = "nbns",
                                    vendor = device.macAddress?.let { OuiVendorLookup.lookup(it) }
                                        ?: device.netbiosName?.let { scanner.guessVendorFromHostname(it) }
                                ),
                                arpCache = arpCache
                            )
                        })
                    } catch (e: Exception) {
                        Log.e(tag, "NBNS discovery error", e)
                    }
                }

                // Run mDNS in parallel with expanded service type coverage
                mdnsJob = launch {
                    try {
                        val serviceTypes = listOf(
                            "_http._tcp.", "_rtsp._tcp.", "_axis-video._tcp.",
                            "_onvif._tcp.", "_workstation._tcp.",
                            "_printer._tcp.", "_ipp._tcp.", "_airplay._tcp.",
                            "_googlecast._tcp.", "_smb._tcp.", "_ssh._tcp.",
                            "_airprint._tcp.", "_esphomelib._tcp.", "_homekit._tcp.",
                            "_matter._tcp.", "_hap._tcp.", "_raop._tcp."
                        )
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
                onvifJob.cancelAndJoin()
                nbnsJob.cancelAndJoin()
                mdnsJob.cancelAndJoin()
                p2pJob.cancelAndJoin()

                // Refresh the ARP cache after TCP probes have populated it,
                // then enrich any devices still missing MAC addresses and add
                // any new ARP-only devices that weren't found by other methods.
                val refreshedArp = ArpCacheReader.readArpTable()
                enrichMacsFromArp(refreshedArp)
                addNewArpDevices(refreshedArp)

                // Run security assessment on devices with open ports
                runSecurityAssessment()
            } finally {
                // Ensure cleanup happens even if an exception or cancellation occurs.
                ssdpJob?.cancel()
                onvifJob?.cancel()
                nbnsJob?.cancel()
                mdnsJob?.cancel()
                p2pJob?.cancel()
                tcpScanJob?.cancel()
                _scanning.value = false
                scanJob = null
                releaseMulticastLock()
            }
        }
    }

    private fun enrichMacsFromArp(arpCache: Map<String, String>) {
        if (arpCache.isEmpty()) return

        var changed = false
        deviceMap.forEach { (ip, dev) ->
            if (dev.mac == null) {
                arpCache[ip]?.let { mac ->
                    val normalizedMac = ArpCacheReader.normalizeMac(mac)
                    val vendor = OuiVendorLookup.lookup(normalizedMac) ?: dev.vendor
                    val wifiMatch = SurveillanceDeviceWatchdog.classifyWifi(dev.hostname ?: "", vendor)
                    val vulnMatches = SurveillanceDeviceWatchdog.analyzeVulnerabilities(vendor, dev.openPorts)
                    val allMatches = (dev.securityMatches + listOfNotNull(wifiMatch) + vulnMatches).distinctBy { it.category to it.matchedOn }
                    
                    deviceMap[ip] = dev.copy(mac = normalizedMac, vendor = vendor, securityMatches = allMatches)
                    changed = true

                    val hostname = dev.hostname ?: ip
                    if (SurveillanceDeviceWatchdog.isRingDevice(hostname, vendor, hostname)) {
                        scope.launch {
                            try {
                                val db = AppDatabase.getInstance(context)
                                val repository = WardrivingRepository(db)
                                repository.processAndSaveTargetDevice(
                                    macAddress = normalizedMac.uppercase(),
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

    /**
     * Adds any ARP cache entries that don't already exist in the device map.
     * This catches devices that responded to TCP probes (triggering ARP) but
     * weren't detected by any of the discovery methods.
     */
    private fun addNewArpDevices(arpCache: Map<String, String>) {
        if (arpCache.isEmpty()) return

        var added = false
        arpCache.forEach { (ip, mac) ->
            if (deviceMap[ip] == null) {
                val normalizedMac = ArpCacheReader.normalizeMac(mac)
                val vendor = OuiVendorLookup.lookup(normalizedMac) ?: guessVendorFromHostname(ip)
                val wifiMatch = SurveillanceDeviceWatchdog.classifyWifi("", vendor)
                val allMatches = listOfNotNull(wifiMatch)

                deviceMap[ip] = RobustLanScanner.Device(
                    ip = ip,
                    mac = normalizedMac,
                    hostname = null,
                    openPorts = emptyList(),
                    source = "arp",
                    vendor = vendor,
                    securityMatches = allMatches
                )
                added = true

                scope.launch {
                    val hostname = ip
                    if (SurveillanceDeviceWatchdog.isRingDevice(hostname, vendor, hostname)) {
                        try {
                            val db = AppDatabase.getInstance(context)
                            val repository = WardrivingRepository(db)
                            repository.processAndSaveTargetDevice(
                                macAddress = normalizedMac.uppercase(),
                                deviceName = vendor?.let { "$it Ring Device" } ?: "Ring Network Camera",
                                category = "CAMERA",
                                rssi = -50,
                                latitude = null,
                                longitude = null
                            )
                        } catch (e: Exception) {
                            Log.e(tag, "Failed to save Ring camera from ARP discovery", e)
                        }
                    }
                }
            }
        }
        if (added) {
            _devices.value = deviceMap.values.sortedBy { it.ip }
        }
    }

    private fun guessVendorFromHostname(hostname: String?): String? {
        return scanner.guessVendorFromHostname(hostname)
    }

    private fun addOrMerge(dev: RobustLanScanner.Device, arpCache: Map<String, String> = emptyMap()) {
        val rawMac = dev.mac ?: arpCache[dev.ip] ?: ArpCacheReader.macForIp(dev.ip)
        val arpMac = rawMac?.let { ArpCacheReader.normalizeMac(it) }
        val existing = deviceMap[dev.ip]
        
        val currentVendor = dev.vendor ?: (arpMac?.let { OuiVendorLookup.lookup(it) })
            ?: scanner.guessVendorFromHostname(dev.hostname)

        val merged = if (existing != null) {
            val finalMac = arpMac ?: existing.mac ?: ArpCacheReader.macForIp(dev.ip)?.let { ArpCacheReader.normalizeMac(it) }
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
            var activeMac = merged.mac
            var activeName = merged.hostname

            if (activeMac == null) {
                // Try NBNS direct NetBIOS unicast query — returns hardware MAC address directly in payload
                val nbnsRes = nbnsHelper.queryHost(merged.ip)
                if (nbnsRes?.macAddress != null) {
                    activeMac = nbnsRes.macAddress
                    if (!nbnsRes.netbiosName.isNullOrBlank()) activeName = nbnsRes.netbiosName
                }
            }

            if (activeMac == null && ArpCacheReader.isArpSupported()) {
                activeMac = withContext(Dispatchers.IO) {
                    ArpCacheReader.resolveMacWithProbe(merged.ip)
                }
            }

            if (activeMac != merged.mac || activeName != merged.hostname) {
                val reVendor = activeMac?.let { OuiVendorLookup.lookup(it) } ?: merged.vendor
                val updated = merged.copy(
                    mac = activeMac ?: merged.mac,
                    hostname = activeName ?: merged.hostname,
                    vendor = reVendor
                )
                deviceMap[merged.ip] = updated
                _devices.value = deviceMap.values.sortedBy { it.ip }
            }

            val mac = activeMac ?: "Unknown"
            val vendor = merged.vendor ?: OuiVendorLookup.lookup(mac)
            val hostname = activeName ?: merged.ip

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

    // ============ SECURITY ASSESSMENT ============

    private val securityAssessor = SecurityAssessmentHelper()
    private val _securityFindings = MutableStateFlow<List<SecurityAssessmentHelper.SecurityFinding>>(emptyList())
    val securityFindings: StateFlow<List<SecurityAssessmentHelper.SecurityFinding>> = _securityFindings.asStateFlow()

    /**
     * Run security assessment on all discovered devices that have open ports.
     * This performs passive HTTP checks and port-based vulnerability matching.
     */
    private suspend fun runSecurityAssessment() {
        val devicesToAssess = deviceMap.values
            .filter { it.openPorts.isNotEmpty() }
            .map { it.ip to it.openPorts }
            .take(20) // Limit to 20 devices to avoid excessive network traffic

        if (devicesToAssess.isEmpty()) return

        val findings = securityAssessor.assessDevices(devicesToAssess)
        val allFindings = findings.flatMap { it.findings }

        if (allFindings.isNotEmpty()) {
            _securityFindings.value = allFindings
            Log.i(tag, "Security assessment found ${allFindings.size} findings across ${devicesToAssess.size} devices")
        }
    }

    /**
     * Manually check default credentials on a specific device.
     * User-triggered only — not part of automatic scanning.
     */
    suspend fun checkDefaultCredentials(ip: String, port: Int, isHttps: Boolean = false): Pair<String, String>? {
        return securityAssessor.checkDefaultCredentials(ip, port, isHttps)
    }

    // ============ VULNERABILITY MATCHING ============

    /**
     * Get port-based vulnerability risks for a device.
     */
    fun getPortRisks(openPorts: List<Int>): List<VulnerabilityDatabase.PortRisk> {
        return VulnerabilityDatabase.assessPorts(openPorts)
    }

    fun stop() {
        scanJob?.cancel()
        scanJob = null
        scanner.cancelScan()
        _scanning.value = false
        _progress.value = 0 to 0
        releaseMulticastLock()
    }
}
