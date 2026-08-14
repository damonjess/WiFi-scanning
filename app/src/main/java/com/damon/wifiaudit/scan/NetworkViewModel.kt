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
                    val vendorInfo = dev.mac?.let { OuiVendorLookup.lookupInfo(it) }
                    NetworkDevice(
                        ip = dev.ip,
                        mac = dev.mac,
                        vendor = dev.vendor ?: vendorInfo?.name,
                        vendorInfo = vendorInfo,
                        hostname = dev.hostname,
                        source = dev.source,
                        openPorts = dev.openPorts,
                        securityMatches = dev.securityMatches
                    )
                }.sortedBy { it.ip.substringAfterLast(".").toIntOrNull() ?: 0 }
            }
        }
    }

    fun startScan() {
        coordinator.start()
    }
}
