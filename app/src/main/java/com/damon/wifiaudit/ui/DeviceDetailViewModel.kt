package com.damon.wifiaudit.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.damon.wifiaudit.ble.GattSnapshotSerializer
import com.damon.wifiaudit.ble.LightGattManager
import com.damon.wifiaudit.ble.AdvertisementParser
import com.damon.wifiaudit.ble.ParsedAdvertisement
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.data.WifiSightingRecord
import com.damon.wifiaudit.data.entity.BleGattSnapshot
import com.damon.wifiaudit.data.entity.RssiHeatmapPoint
import com.damon.wifiaudit.util.MacOuiExtractor
import com.damon.wifiaudit.vendor.OuiVendorLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DeviceDetailViewModel(
    app: Application,
    private val mac: String,
    private val type: String
) : AndroidViewModel(app) {

    val db = AppDatabase.getInstance(app)

    // Classification (placeholder for future implementation)
    private val _classification = MutableStateFlow<String?>(null)
    val classification: StateFlow<String?> = _classification.asStateFlow()

    // Heatmap collection
    private val _heatmapEnabled = MutableStateFlow(false)
    val heatmapEnabled: StateFlow<Boolean> = _heatmapEnabled.asStateFlow()

    private val _heatmapPoints = MutableStateFlow<List<RssiHeatmapPoint>>(emptyList())
    val heatmapPoints: StateFlow<List<RssiHeatmapPoint>> = _heatmapPoints.asStateFlow()

    // Parsed advertisement from the latest sighting
    private val _advertisement = MutableStateFlow<ParsedAdvertisement?>(null)
    val advertisement: StateFlow<ParsedAdvertisement?> = _advertisement.asStateFlow()

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
        val hasHistoricGatt: Boolean = false,
        val isConnectable: Boolean = false,
        val txPower: Int? = null
    )

    private val _state = MutableStateFlow(UiState(macAddress = mac))
    val state: StateFlow<UiState> = _state.asStateFlow()

    var gattManager: LightGattManager? = null
        private set

    init {
        viewModelScope.launch {
            load()
            if (type == "BLE") {
                checkHistoricGatt()
                loadAdvertisementData()
                observeHeatmapPoints()
            }
        }
    }

    private suspend fun loadAdvertisementData() {
        val sighting = db.bleSightingDao().getLatestForMac(mac) ?: return
        val parsed = AdvertisementParser.parse(sighting.scanRecord)
        _advertisement.value = parsed

        _state.value = _state.value.copy(
            isConnectable = parsed.isConnectable,
            txPower = parsed.txPowerLevel ?: _state.value.txPower
        )
    }

    private fun observeHeatmapPoints() {
        viewModelScope.launch {
            db.rssiHeatmapDao().getPointsForMac(mac).collect { points ->
                _heatmapPoints.value = points
            }
        }
    }

    fun toggleHeatmap() {
        val newState = !_heatmapEnabled.value
        _heatmapEnabled.value = newState

        if (newState) {
            startHeatmapCollection()
        } else {
            stopHeatmapCollection()
        }
    }

    private var heatmapJob: Job? = null

    private fun startHeatmapCollection() {
        heatmapJob?.cancel()
        heatmapJob = viewModelScope.launch {
            while (isActive && _heatmapEnabled.value) {
                val currentRssi = _state.value.rssi
                val lat = _state.value.latitude
                val lng = _state.value.longitude

                if (lat != null && lng != null) {
                    db.rssiHeatmapDao().insert(
                        RssiHeatmapPoint(
                            macAddress = mac,
                            rssi = currentRssi,
                            latitude = lat,
                            longitude = lng,
                            accuracy = null
                        )
                    )
                }
                delay(1000)
            }
        }
    }

    private fun stopHeatmapCollection() {
        heatmapJob?.cancel()
    }

    fun clearHeatmap() {
        viewModelScope.launch {
            db.rssiHeatmapDao().clearForMac(mac)
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
                val dbVendor = db.ouiVendorDao().lookupVendor(MacOuiExtractor.extractOui(mac))
                _state.value = _state.value.copy(
                    name = latest.deviceName ?: "Unknown",
                    macAddress = latest.macAddress,
                    vendor = dbVendor ?: OuiVendorLookup.lookup(latest.macAddress),
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
                val dbVendor = db.ouiVendorDao().lookupVendor(MacOuiExtractor.extractOui(mac))
                _state.value = _state.value.copy(
                    name = latest.ssid.ifBlank { "Hidden Network" },
                    macAddress = latest.bssid,
                    vendor = dbVendor ?: OuiVendorLookup.lookup(latest.bssid),
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
        gattManager = LightGattManager(getApplication(), remoteDevice).apply {
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
