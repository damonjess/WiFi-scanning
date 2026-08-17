package com.damon.wifiaudit.scan

import android.location.Location
import android.net.wifi.ScanResult
import com.damon.wifiaudit.ble.BleDeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScanCycleSnapshot(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracyMeters: Float? = null,
    val locationTimeMillis: Long = 0L,
    val wifiResults: List<ScanResult> = emptyList(),
    val bleDevices: List<BleDeviceInfo> = emptyList(),
    val lastCommitMillis: Long = 0L,
    val cyclesWritten: Int = 0,
) {
    fun hasUsableLiveLocation(maxAgeMillis: Long = 15_000L): Boolean {
        val latitude = latitude ?: return false
        val longitude = longitude ?: return false
        val accuracy = locationAccuracyMeters ?: return false
        val ageMillis = System.currentTimeMillis() - locationTimeMillis
        return latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            accuracy <= MAXIMUM_USABLE_ACCURACY_METERS &&
            ageMillis in 0..maxAgeMillis
    }

    private companion object {
        const val MAXIMUM_USABLE_ACCURACY_METERS = 75f
    }
}

/**
 * Shared current scan state. Persisted sightings are deliberately not used as
 * live device location, preventing a stale home address from recentering the
 * map while a new scan is in progress.
 */
object ScanStatusRepository {
    private val _snapshot = MutableStateFlow(ScanCycleSnapshot())
    val snapshot: StateFlow<ScanCycleSnapshot> = _snapshot.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun updateLocation(location: Location) {
        if (!location.hasAccuracy() || location.accuracy > 75f) return
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return

        _snapshot.value = _snapshot.value.copy(
            latitude = location.latitude,
            longitude = location.longitude,
            locationAccuracyMeters = location.accuracy,
            locationTimeMillis = location.time,
        )
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
