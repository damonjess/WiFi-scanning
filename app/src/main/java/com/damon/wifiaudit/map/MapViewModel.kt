package com.damon.wifiaudit.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.data.BleSightingRecord
import com.damon.wifiaudit.data.WifiSightingRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).sightingHistoryDao()

    private val _wifiLocations = MutableStateFlow<List<WifiSightingRecord>>(emptyList())
    val wifiLocations: StateFlow<List<WifiSightingRecord>> = _wifiLocations.asStateFlow()

    private val _bleLocations = MutableStateFlow<List<BleSightingRecord>>(emptyList())
    val bleLocations: StateFlow<List<BleSightingRecord>> = _bleLocations.asStateFlow()

    fun refresh() {
        android.util.Log.i("MapVM", "Refresh requested")
        viewModelScope.launch {
            try {
                val wifi = dao.getWifiHistory()
                val ble = dao.getBleHistory()
                android.util.Log.i("MapVM", "DATA_LOADED: WiFi=${wifi.size}, BLE=${ble.size}")
                _wifiLocations.value = wifi
                _bleLocations.value = ble
            } catch (e: Exception) {
                android.util.Log.e("MapVM", "Failed to load history", e)
            }
        }
    }
}
