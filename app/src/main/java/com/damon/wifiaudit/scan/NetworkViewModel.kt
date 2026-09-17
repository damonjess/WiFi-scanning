package com.damon.wifiaudit.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.damon.wifiaudit.vendor.OuiVendorLookup
import com.damon.wifiaudit.watchdog.SurveillanceDeviceWatchdog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NetworkViewModel(application: Application) : AndroidViewModel(application) {
    private val coordinator = DiscoveryCoordinator.getInstance(application)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<NetworkDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<NetworkDevice>> = _discoveredDevices.asStateFlow()

    private val _currentHost = MutableStateFlow<String?>(null)
    val currentHost: StateFlow<String?> = _currentHost.asStateFlow()

    data class NetworkDevice(
        val ip: String,
        val mac: String?,
        val vendor: String?,
        val vendorInfo: OuiVendorLookup.VendorInfo? = null,
        val deviceName: String = "",
        val hostname: String?,
        val source: String?,
        val openPorts: List<Int>,
        val responseTime: Long = 0,
        val securityMatches: List<SurveillanceDeviceWatchdog.Match> = emptyList()
    )

    init {
        viewModelScope.launch {
            coordinator.scanning.collect {
                _isScanning.value = it
            }
        }
        viewModelScope.launch {
            coordinator.devices.collect { devices ->
                _discoveredDevices.value = devices.map { dev ->
                    val rawMac = dev.mac ?: ArpCacheReader.macForIp(dev.ip)
                    val mac = rawMac?.let { ArpCacheReader.normalizeMac(it) }
                    val vendorInfo = mac?.let { OuiVendorLookup.lookupInfo(it) }
                    val vendor = dev.vendor ?: vendorInfo?.name ?: (mac?.let { OuiVendorLookup.lookup(it) })
                    val name = resolveDeviceName(dev.hostname, vendor, dev.ip, dev.openPorts)
                    NetworkDevice(
                        ip = dev.ip,
                        mac = mac,
                        vendor = vendor,
                        vendorInfo = vendorInfo,
                        deviceName = name,
                        hostname = dev.hostname,
                        source = dev.source,
                        openPorts = dev.openPorts,
                        securityMatches = dev.securityMatches
                    )
                }.sortedBy { it.ip.substringAfterLast(".").toIntOrNull() ?: 0 }
            }
        }
    }

    private fun resolveDeviceName(
        hostname: String?,
        vendor: String?,
        ip: String,
        openPorts: List<Int>
    ): String {
        if (!hostname.isNullOrBlank() && hostname != ip && hostname != "Unknown") {
            val cleanHost = hostname
                .split("|")
                .map { it.trim() }
                .distinct()
                .joinToString(" • ")
                .removeSuffix(".local")
                .removeSuffix(".lan")
                .replace("-", " ")
            if (cleanHost.isNotBlank()) return cleanHost
        }

        val v = vendor?.lowercase() ?: ""
        return when {
            v.contains("apple") -> "Apple iPhone / Mac"
            v.contains("samsung") -> "Samsung Galaxy Device"
            v.contains("google") -> "Google Nest / Chromecast"
            v.contains("amazon") -> "Amazon Echo / Fire TV"
            v.contains("raspberry") -> "Raspberry Pi"
            v.contains("espressif") -> "ESP32 / ESP8266 IoT Node"
            v.contains("tuya") -> "Tuya Smart Home Controller"
            v.contains("tp-link") -> "TP-Link Smart Device"
            v.contains("netgear") -> "Netgear Router / Access Point"
            v.contains("asus") -> "ASUS Router / Computer"
            v.contains("synology") || v.contains("qnap") -> "Network Attached Storage (NAS)"
            v.contains("hikvision") || v.contains("dahua") || v.contains("reolink") || v.contains("amcrest") || v.contains("wyze") || v.contains("ring") || v.contains("arlo") || v.contains("foscam") -> "IP Security Camera"
            v.contains("sonos") -> "Sonos Audio Speaker"
            v.contains("roku") -> "Roku Streaming Player"
            v.contains("hue") || v.contains("philips") -> "Philips Hue Smart Hub"
            v.contains("tesla") -> "Tesla Vehicle / Key System"
            v.contains("hp") || v.contains("canon") || v.contains("epson") || v.contains("brother") -> "Network Printer"
            v.contains("dell") || v.contains("lenovo") || v.contains("intel") || v.contains("realtek") -> "PC / Workstation"
            v.contains("nintendo") || v.contains("nvidia") -> "Gaming / Media Console"
            v.contains("sony") -> "Sony Entertainment / Audio"
            v.contains("cisco") || v.contains("ubiquiti") || v.contains("mikrotik") || v.contains("d-link") || v.contains("linksys") -> "Network Switch / Router"
            v.contains("starlink") -> "SpaceX Starlink Terminal"
            openPorts.contains(554) || openPorts.contains(8554) || openPorts.contains(37777) || openPorts.contains(34567) -> "IP Camera / Video Stream"
            openPorts.contains(631) || openPorts.contains(9100) -> "Network Printer"
            openPorts.contains(5353) -> vendor?.let { "$it Smart Device" } ?: "mDNS Network Host"
            openPorts.contains(80) || openPorts.contains(443) || openPorts.contains(8080) || openPorts.contains(8443) -> vendor?.let { "$it Web Endpoint" } ?: "Web Router / Gateway"
            !vendor.isNullOrBlank() -> "$vendor Hardware"
            else -> "Discovered Host ($ip)"
        }
    }

    fun startScan() {
        coordinator.start()
    }
}
