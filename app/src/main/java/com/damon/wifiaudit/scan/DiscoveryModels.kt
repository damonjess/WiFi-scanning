package com.damon.wifiaudit.scan

data class OnvifDeviceInfo(
    val ip: String,
    val epAddress: String?,
    val types: String?,
    val xAddrs: String?
)

data class SsdpDeviceInfo(
    val ip: String,
    val location: String?,
    val server: String?,
    val friendlyName: String? = null,
    val modelName: String? = null,
    val manufacturer: String? = null
)

data class DiscoveryResult(
    val ip: String,
    val source: String, // "ssdp", "tcp", "icmp", "mdns", "p2p"
    val rawData: String? = null,
    val onvifInfo: OnvifDeviceInfo? = null,
    val ssdpInfo: SsdpDeviceInfo? = null,
    val device: NetworkDevice? = null,
    val playableUrl: String? = null,
    val streamUrls: List<String> = emptyList()
)

data class NetworkDevice(
    val ip: String,
    val hostname: String,
    val mac: String,
    val vendor: String?,
    val openPorts: List<Int>
)
