package com.damon.wifiaudit.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.data.BleSightingRecord
import com.damon.wifiaudit.data.WifiSightingRecord
import com.damon.wifiaudit.vendor.OuiVendorLookup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DeviceDetailViewModel(
    app: Application,
    private val mac: String,
    private val type: String
) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)

    data class UiState(
        val name: String = "",
        val macAddress: String = "",
        val vendor: String? = null,
        val rssi: Int = 0,
        val encryption: String = "",
        val latitude: Double? = null,
        val longitude: Double? = null,
        val isFavorite: Boolean = false,
        val detectCount: Int = 0,
        val firstSeen: Long? = null,
        val lastSeen: Long? = null
    )

    private val _state = MutableStateFlow(UiState(macAddress = mac))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            if (type == "BLE") {
                val list = db.bleSightingDao().getHistoryForMac(mac).first()
                val latest = list.firstOrNull() ?: return
                _state.value = _state.value.copy(
                    name = latest.deviceName ?: "Unknown",
                    macAddress = latest.macAddress,
                    vendor = OuiVendorLookup.lookup(latest.macAddress),
                    rssi = latest.rssi,
                    latitude = latest.latitude,
                    longitude = latest.longitude,
                    detectCount = list.size,
                    firstSeen = list.minOfOrNull { it.timestamp },
                    lastSeen = list.maxOfOrNull { it.timestamp }
                )
            } else {
                val list = db.wifiSightingDao().getHistoryForBssid(mac).first()
                val latest = list.firstOrNull() ?: return
                _state.value = _state.value.copy(
                    name = latest.ssid.ifBlank { "Hidden Network" },
                    macAddress = latest.bssid,
                    vendor = OuiVendorLookup.lookup(latest.bssid),
                    rssi = latest.rssi,
                    encryption = latest.encryption,
                    latitude = latest.latitude,
                    longitude = latest.longitude,
                    detectCount = list.size,
                    firstSeen = list.minOfOrNull { it.timestamp },
                    lastSeen = list.maxOfOrNull { it.timestamp }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleFavorite() {
        _state.value = _state.value.copy(isFavorite = !_state.value.isFavorite)
    }
}
