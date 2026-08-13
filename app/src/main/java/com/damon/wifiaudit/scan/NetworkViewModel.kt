package com.damon.wifiaudit.scan

import android.app.Application
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.damon.wifiaudit.vendor.OuiVendorLookup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NetworkViewModel(application: Application) : AndroidViewModel(application) {
    private val wifiManager = application.getSystemService(Application.WIFI_SERVICE) as WifiManager
    private val scanner = LocalNetworkScanner()

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
        val openPorts: List<Int>,
        val responseTime: Long
    )

    fun startScan() {
        if (_isScanning.value) return

        viewModelScope.launch {
            _isScanning.value = true
            _discoveredDevices.value = emptyList()
            
            val info = wifiManager.connectionInfo
            val ipAddress = info.ipAddress
            if (ipAddress == 0) {
                _isScanning.value = false
                return@launch
            }

            val ipString = Formatter.formatIpAddress(ipAddress)
            val baseIp = ipString.substringBeforeLast(".")

            scanner.scanSubnet(
                baseIp = baseIp,
                onHostFound = { result ->
                    val vendor = result.macAddress?.let { OuiVendorLookup.lookup(it) }
                    val device = NetworkDevice(
                        ip = result.ipAddress,
                        mac = result.macAddress,
                        vendor = vendor,
                        openPorts = result.openPorts,
                        responseTime = result.responseTimeMs
                    )
                    _discoveredDevices.value = (_discoveredDevices.value + device).sortedBy { it.ip.substringAfterLast(".").toIntOrNull() ?: 0 }
                }
            )
            _isScanning.value = false
        }
    }
}
