package com.damon.wifiaudit.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.damon.wifiaudit.ble.GattSnapshotSerializer
import com.damon.wifiaudit.ble.LightGattManager
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.data.WifiSightingRecord
import com.damon.wifiaudit.data.entity.BleGattSnapshot
import com.damon.wifiaudit.vendor.OuiVendorLookup
import kotlinx.coroutines.Dispatchers
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
        val lastSeen: Long? = null,
        val gattState: LightGattManager.State = LightGattManager.State.Disconnected,
        val services: List<LightGattManager.BleService> = emptyList(),
        val gattError: String? = null,
        val hasHistoricGatt: Boolean = false
    )

    private val _state = MutableStateFlow(UiState(macAddress = mac))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var gattManager: LightGattManager? = null

    init {
        viewModelScope.launch {
            load()
            if (type == "BLE") checkHistoricGatt()
        }
    }

    private suspend fun checkHistoricGatt() {
        val snap = db.bleGattSnapshotDao().getLatestForMac(mac)
        _state.value = _state.value.copy(hasHistoricGatt = snap != null)
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

    @SuppressLint("MissingPermission")
    fun analyseDevice() {
        val current = _state.value.gattState
        if (current is LightGattManager.State.Ready) {
            // Already connected — disconnect
            gattManager?.disconnect()
            _state.value = _state.value.copy(gattState = LightGattManager.State.Disconnected, gattError = null)
            return
        }
        if (current is LightGattManager.State.Connecting || current is LightGattManager.State.Discovering) {
            return // already in progress
        }

        val bluetoothManager = getApplication<Application>()
            .getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val remoteDevice = bluetoothManager.adapter?.getRemoteDevice(mac) ?: return

        gattManager?.release()
        gattManager = LightGattManager(getApplication(), remoteDevice, viewModelScope).apply {
            // Collect state updates
            viewModelScope.launch {
                state.collect { s ->
                    val errorMsg = if (s is LightGattManager.State.Error) s.msg else null
                    val svcList = (s as? LightGattManager.State.Ready)?.services ?: _state.value.services

                    _state.value = _state.value.copy(
                        gattState = s,
                        gattError = errorMsg,
                        services = svcList
                    )

                    if (s is LightGattManager.State.Ready) {
                        saveGattSnapshot(svcList)
                    }
                }
            }
            connect()
        }
    }

    private fun saveGattSnapshot(services: List<LightGattManager.BleService>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = GattSnapshotSerializer.toJson(services)
                db.bleGattSnapshotDao().insert(
                    BleGattSnapshot(
                        macAddress = mac,
                        deviceName = _state.value.name,
                        servicesJson = json,
                        serviceCount = services.size,
                        characteristicCount = services.sumOf { it.characteristics.size }
                    )
                )
                _state.value = _state.value.copy(hasHistoricGatt = true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadHistoricGatt() {
        viewModelScope.launch(Dispatchers.IO) {
            val snap = db.bleGattSnapshotDao().getLatestForMac(mac) ?: return@launch
            val services = GattSnapshotSerializer.fromJson(snap.servicesJson)
            _state.value = _state.value.copy(
                services = services,
                gattState = LightGattManager.State.Ready(services),
                gattError = null
            )
        }
    }

    fun toggleFavorite() {
        _state.value = _state.value.copy(isFavorite = !_state.value.isFavorite)
    }

    override fun onCleared() {
        super.onCleared()
        gattManager?.release()
    }
}
