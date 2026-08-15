package com.damon.wifiaudit.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.damon.wifiaudit.data.*
import com.damon.wifiaudit.scan.ScanStatusRepository
import com.damon.wifiaudit.data.entity.RssiHeatmapPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val historyDao = db.sightingHistoryDao()
    private val locationDao = db.locationFixDao()
    private val sessionDao = db.scanSessionDao()

    val currentSnapshot = ScanStatusRepository.snapshot

    init {
        refresh()
    }

    private val _sessions = MutableStateFlow<List<SessionSummary>>(emptyList())
    val sessions: StateFlow<List<SessionSummary>> = _sessions.asStateFlow()

    private val _selectedSessionId = MutableStateFlow<Long?>(null)
    val selectedSessionId: StateFlow<Long?> = _selectedSessionId.asStateFlow()

    private val _wifiLocations = MutableStateFlow<List<WifiSightingRecord>>(emptyList())
    val wifiLocations: StateFlow<List<WifiSightingRecord>> = _wifiLocations.asStateFlow()

    private val _bleLocations = MutableStateFlow<List<BleSightingRecord>>(emptyList())
    val bleLocations: StateFlow<List<BleSightingRecord>> = _bleLocations.asStateFlow()

    private val _trackPoints = MutableStateFlow<List<LocationFix>>(emptyList())
    val trackPoints: StateFlow<List<LocationFix>> = _trackPoints.asStateFlow()

    private val _showTrack = MutableStateFlow(true)
    val showTrack: StateFlow<Boolean> = _showTrack.asStateFlow()

    private val _showHeatmap = MutableStateFlow(false)
    val showHeatmap: StateFlow<Boolean> = _showHeatmap.asStateFlow()

    // --- NEW IMPROVEMENTS ---

    // All points for this session
    private val _allPoints = MutableStateFlow<List<HeatmapPoint>>(emptyList())
    val allPoints: StateFlow<List<HeatmapPoint>> = _allPoints.asStateFlow()

    // --- OSM Integration ---
    private val _osmPoints = MutableStateFlow<List<RssiHeatmapPoint>>(emptyList())
    val osmPoints: StateFlow<List<RssiHeatmapPoint>> = _osmPoints.asStateFlow()

    val allRssiPoints: StateFlow<List<RssiHeatmapPoint>> = db.rssiHeatmapDao().getAllPoints()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var playJob: Job? = null

    fun loadPointsForMac(mac: String) {
        viewModelScope.launch {
            db.rssiHeatmapDao().getPointsForMac(mac).collect { pts ->
                _osmPoints.value = pts
            }
        }
    }

    fun togglePlayback() {
        _isPlaying.value = !_isPlaying.value
        if (_isPlaying.value) startPlayback() else stopPlayback()
    }

    private fun startPlayback() {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            val total = _osmPoints.value.size
            if (total == 0) return@launch
            while (isActive && _isPlaying.value) {
                val current = _playbackIndex.value ?: -1
                val next = if (current >= total - 1) 0 else current + 1
                _playbackIndex.value = next
                delay(400)
            }
        }
    }

    private fun stopPlayback() {
        playJob?.cancel()
        _playbackIndex.value = null
    }
    // --- END OSM Integration ---

    // Playback state
    private val _playbackIndex = MutableStateFlow<Int?>(null) // null = show all
    val playbackIndex: StateFlow<Int?> = _playbackIndex.asStateFlow()

    // Selected point for bottom sheet
    private val _selectedPoint = MutableStateFlow<HeatmapPoint?>(null)
    val selectedPoint: StateFlow<HeatmapPoint?> = _selectedPoint.asStateFlow()

    // Layer toggles
    private val _showWifi = MutableStateFlow(true)
    val showWifi: StateFlow<Boolean> = _showWifi.asStateFlow()
    private val _showBle = MutableStateFlow(true)
    val showBle: StateFlow<Boolean> = _showBle.asStateFlow()
    private val _showGrid = MutableStateFlow(false)
    val showGrid: StateFlow<Boolean> = _showGrid.asStateFlow()

    fun setPlaybackIndex(index: Int?) { _playbackIndex.value = index }
    fun selectPoint(point: HeatmapPoint?) { _selectedPoint.value = point }
    fun toggleWifi() { _showWifi.value = !_showWifi.value }
    fun toggleBle() { _showBle.value = !_showBle.value }
    fun toggleGrid() { _showGrid.value = !_showGrid.value }

    data class HeatmapPoint(
        val x: Float,      // normalized 0..1 on floor plan
        val y: Float,
        val rssi: Int,
        val type: String,  // "WIFI" | "BLE"
        val mac: String,
        val deviceName: String?,
        val timestamp: Long,
        val ssid: String? = null,  // WiFi only
        val latitude: Double? = null,
        val longitude: Double? = null
    )

    // --- END NEW IMPROVEMENTS ---

    fun selectSession(sessionId: Long?) {
        _selectedSessionId.value = sessionId
        loadSessionData(sessionId)
    }

    fun toggleTrack() {
        _showTrack.value = !_showTrack.value
    }

    fun setHeatmap(enabled: Boolean) {
        _showHeatmap.value = enabled
    }

    fun refresh() {
        viewModelScope.launch {
            val summaries = sessionDao.getAllSessionSummaries()
            _sessions.value = summaries
            val targetId = _selectedSessionId.value ?: summaries.firstOrNull()?.id
            _selectedSessionId.value = targetId
            loadSessionData(targetId)
        }
    }

    private fun loadSessionData(sessionId: Long?) {
        viewModelScope.launch {
            if (sessionId == null) {
                _wifiLocations.value = emptyList()
                _bleLocations.value = emptyList()
                _trackPoints.value = emptyList()
                return@launch
            }
            try {
                val sessionFixes = locationDao.getFixesForSession(sessionId)
                _trackPoints.value = sessionFixes
                val sessionFixIds = sessionFixes.map { it.id }.toSet()

                // Load all history and filter for this session
                val allWifi = historyDao.getWifiHistory()
                val allBle = historyDao.getBleHistory()

                // De-duplicate: Keep only the strongest sighting for each unique BSSID/MAC in this session
                _wifiLocations.value = allWifi
                    .filter { it.locationId in sessionFixIds }
                    .groupBy { it.bssid }
                    .map { (_, sightings) -> sightings.maxBy { it.rssi } }

                _bleLocations.value = allBle
                    .filter { it.locationId in sessionFixIds }
                    .groupBy { it.macAddress }
                    .map { (_, sightings) -> sightings.maxBy { it.rssi } }

                updateHeatmapPoints(_wifiLocations.value, _bleLocations.value, sessionFixes)

            } catch (e: Exception) {
                android.util.Log.e("MapVM", "Failed to load session data", e)
            }
        }
    }

    private fun updateHeatmapPoints(
        wifi: List<WifiSightingRecord>,
        ble: List<BleSightingRecord>,
        fixes: List<LocationFix>
    ) {
        if (fixes.isEmpty()) {
            _allPoints.value = emptyList()
            return
        }

        val minLat = fixes.minOf { it.latitude }
        val maxLat = fixes.maxOf { it.latitude }
        val minLon = fixes.minOf { it.longitude }
        val maxLon = fixes.maxOf { it.longitude }

        val latRange = (maxLat - minLat).coerceAtLeast(0.00001)
        val lonRange = (maxLon - minLon).coerceAtLeast(0.00001)

        val wifiPts = wifi.map {
            HeatmapPoint(
                x = ((it.longitude - minLon) / lonRange).toFloat(),
                y = (1.0 - (it.latitude - minLat) / latRange).toFloat(), // Flip Y for screen coords
                rssi = it.rssi,
                type = "WIFI",
                mac = it.bssid,
                deviceName = it.ssid,
                timestamp = it.timestamp,
                ssid = it.ssid,
                latitude = it.latitude,
                longitude = it.longitude
            )
        }

        val blePts = ble.map {
            HeatmapPoint(
                x = ((it.longitude - minLon) / lonRange).toFloat(),
                y = (1.0 - (it.latitude - minLat) / latRange).toFloat(),
                rssi = it.rssi,
                type = "BLE",
                mac = it.macAddress,
                deviceName = it.deviceName,
                timestamp = it.timestamp,
                latitude = it.latitude,
                longitude = it.longitude
            )
        }

        _allPoints.value = (wifiPts + blePts).sortedBy { it.timestamp }
    }
}
