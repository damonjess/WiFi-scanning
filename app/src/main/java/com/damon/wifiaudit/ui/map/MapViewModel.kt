package com.damon.wifiaudit.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.data.WifiSightingRecord
import com.damon.wifiaudit.data.BleSightingRecord
import com.damon.wifiaudit.data.RingCamera
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MapViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)

    // All GPS-tagged sightings
    private val _wifiPoints = MutableStateFlow<List<MapPoint>>(emptyList())
    val wifiPoints: StateFlow<List<MapPoint>> = _wifiPoints.asStateFlow()

    private val _blePoints = MutableStateFlow<List<MapPoint>>(emptyList())
    val blePoints: StateFlow<List<MapPoint>> = _blePoints.asStateFlow()

    private val _ringPoints = MutableStateFlow<List<MapPoint>>(emptyList())
    val ringPoints: StateFlow<List<MapPoint>> = _ringPoints.asStateFlow()

    // Combined for total count
    val totalPoints: StateFlow<Int> = combine(_wifiPoints, _blePoints, _ringPoints) { w, b, r -> w.size + b.size + r.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Layer toggles
    private val _showWifi = MutableStateFlow(true)
    val showWifi: StateFlow<Boolean> = _showWifi.asStateFlow()
    
    private val _showBle = MutableStateFlow(true)
    val showBle: StateFlow<Boolean> = _showBle.asStateFlow()

    private val _showRing = MutableStateFlow(true)
    val showRing: StateFlow<Boolean> = _showRing.asStateFlow()

    // Selected point for bottom sheet
    private val _selectedPoint = MutableStateFlow<MapPoint?>(null)
    val selectedPoint: StateFlow<MapPoint?> = _selectedPoint.asStateFlow()

    init {
        loadAllSightings()
        // Bridge marker taps from osmdroid callbacks back to Compose state
        MapMarkerBridge.onSelect = { pt ->
            _selectedPoint.value = pt
        }
    }

    private fun loadAllSightings() {
        viewModelScope.launch {
            // WiFi sightings with GPS
            db.wifiSightingDao().getAllSightings().collect { sightings ->
                _wifiPoints.value = sightings.map { it.toMapPoint() }
            }
        }
        viewModelScope.launch {
            // BLE sightings with GPS
            db.bleSightingDao().getAllSightings().collect { sightings ->
                _blePoints.value = sightings.map { it.toMapPoint() }
            }
        }
        viewModelScope.launch {
            // Ring cameras with GPS
            db.ringCameraDao().getAllRingCameras().collect { cameras ->
                _ringPoints.value = cameras
                    .filter { it.latitude != null && it.longitude != null }
                    .map { it.toMapPoint() }
            }
        }
    }

    private fun RingCamera.toMapPoint(): MapPoint {
        return MapPoint(
            id = macAddress.hashCode().toLong(),
            locationId = -1L, // Ring cameras aren't tied to a specific scan location ID in the same way
            macAddress = macAddress,
            name = deviceName,
            rssi = signalStrength,
            latitude = latitude ?: 0.0,
            longitude = longitude ?: 0.0,
            timestamp = lastSeen,
            type = PointType.RING
        )
    }

    private fun WifiSightingRecord.toMapPoint(): MapPoint {
        return MapPoint(
            id = id,
            locationId = locationId,
            macAddress = bssid,
            name = if (ssid.isBlank()) "Hidden WiFi" else ssid,
            rssi = rssi,
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp,
            type = PointType.WIFI
        )
    }

    private fun BleSightingRecord.toMapPoint(): MapPoint {
        return MapPoint(
            id = id,
            locationId = locationId,
            macAddress = macAddress,
            name = deviceName ?: "Unknown BLE",
            rssi = rssi,
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp,
            type = PointType.BLE
        )
    }

    fun toggleWifi() { _showWifi.value = !_showWifi.value }
    fun toggleBle() { _showBle.value = !_showBle.value }
    fun toggleRing() { _showRing.value = !_showRing.value }
    fun selectPoint(point: MapPoint?) { _selectedPoint.value = point }

    data class MapPoint(
        val id: Long,
        val locationId: Long,
        val macAddress: String,
        val name: String,
        val rssi: Int,
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long,
        val type: PointType
    )

    enum class PointType { WIFI, BLE, RING }
}
