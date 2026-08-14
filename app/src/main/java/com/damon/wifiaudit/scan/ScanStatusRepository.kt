package com.damon.wifiaudit.scan

import android.net.wifi.ScanResult
import com.damon.wifiaudit.ble.BleDeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScanCycleSnapshot(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val wifiResults: List<ScanResult> = emptyList(),
    val bleDevices: List<BleDeviceInfo> = emptyList(),
    val lastCommitMillis: Long = 0L,
    val cyclesWritten: Int = 0,
)

/**
 * Singleton bridge between WardrivingService (writer) and any UI ViewModel
 * (reader). The service updates this on every radio event regardless of
 * whether anything is observing it. The UI only pays collection cost while
 * a screen is actually subscribed (via repeatOnLifecycle upstream).
 */
object ScanStatusRepository {
    private val _snapshot = MutableStateFlow(ScanCycleSnapshot())
    val snapshot: StateFlow<ScanCycleSnapshot> = _snapshot.asStateFlow()

    fun updateLocation(lat: Double, lon: Double) {
        _snapshot.value = _snapshot.value.copy(latitude = lat, longitude = lon)
    }

    fun updateWifiResults(results: List<ScanResult>) {
        val current = _snapshot.value.wifiResults.associateBy { it.BSSID }.toMutableMap()
        results.forEach { newResult ->
            val existing = current[newResult.BSSID]
            if (existing == null || newResult.level > existing.level) {
                current[newResult.BSSID] = newResult
            }
        }
        _snapshot.value = _snapshot.value.copy(wifiResults = current.values.toList())
    }

    fun updateBleDevices(devices: List<BleDeviceInfo>) {
        _snapshot.value = _snapshot.value.copy(bleDevices = devices)
    }

    fun markCommitted() {
        _snapshot.value = _snapshot.value.copy(
            lastCommitMillis = System.currentTimeMillis(),
            cyclesWritten = _snapshot.value.cyclesWritten + 1,
        )
    }

    fun clearWifiResults() {
        _snapshot.value = _snapshot.value.copy(wifiResults = emptyList())
    }

    fun clearBleDevices() {
        _snapshot.value = _snapshot.value.copy(bleDevices = emptyList())
    }
}
