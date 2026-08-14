package com.damon.wifiaudit.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.damon.wifiaudit.data.*
import com.damon.wifiaudit.scan.ScanStatusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val historyDao = db.sightingHistoryDao()
    private val locationDao = db.locationFixDao()
    private val sessionDao = db.scanSessionDao()

    val currentSnapshot = ScanStatusRepository.snapshot

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

            } catch (e: Exception) {
                android.util.Log.e("MapVM", "Failed to load session data", e)
            }
        }
    }
}
