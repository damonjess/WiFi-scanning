package com.damon.wifiaudit.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.data.BleSightingRecord
import com.damon.wifiaudit.data.LocationFix
import com.damon.wifiaudit.data.WifiSightingRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val historyDao = db.sightingHistoryDao()
    private val locationDao = db.locationFixDao()

    private val _wifiLocations = MutableStateFlow<List<WifiSightingRecord>>(emptyList())
    val wifiLocations: StateFlow<List<WifiSightingRecord>> = _wifiLocations.asStateFlow()

    private val _bleLocations = MutableStateFlow<List<BleSightingRecord>>(emptyList())
    val bleLocations: StateFlow<List<BleSightingRecord>> = _bleLocations.asStateFlow()

    private val _trackPoints = MutableStateFlow<List<LocationFix>>(emptyList())
    val trackPoints: StateFlow<List<LocationFix>> = _trackPoints.asStateFlow()

    private val _showTrack = MutableStateFlow(true)
    val showTrack: StateFlow<Boolean> = _showTrack.asStateFlow()

    fun toggleTrack() {
        _showTrack.value = !_showTrack.value
    }

    fun refresh() {
        viewModelScope.launch {
            android.util.Log.i("MapVM", "Refresh requested")
            try {
                val wifi = historyDao.getWifiHistory()
                val ble = historyDao.getBleHistory()
                val track = locationDao.getAll() // already ordered by timestamp ASC
                android.util.Log.i("MapVM", "DATA_LOADED: WiFi=${wifi.size}, BLE=${ble.size}, track points=${track.size}")
                _wifiLocations.value = wifi
                _bleLocations.value = ble
                _trackPoints.value = track
            } catch (e: Exception) {
                android.util.Log.e("MapVM", "Failed to load history", e)
            }
        }
    }
}
