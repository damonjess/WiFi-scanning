package com.damon.wifiaudit.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.damon.wifiaudit.ble.BleDeviceInfo
import com.damon.wifiaudit.ble.GattConnectionManager
import com.damon.wifiaudit.ble.toHex
import com.damon.wifiaudit.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class HistoryStyle(val label: String) {
    MARKERS("Markers"),
    HEATMAP("Heatmap"),
    TRACK("Track")
}

class DeviceDetailViewModel(
    application: Application,
    private val macAddress: String
) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val gattDao = db.bleGattDao()
    private val trackedDao = db.trackedDeviceDao()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _showHeatmap = MutableStateFlow(false)
    val showHeatmap: StateFlow<Boolean> = _showHeatmap.asStateFlow()

    private val _historyStyle = MutableStateFlow(HistoryStyle.MARKERS)
    val historyStyle: StateFlow<HistoryStyle> = _historyStyle.asStateFlow()

    private val _connectionState = MutableStateFlow<GattConnectionManager.ConnectionState>(
        GattConnectionManager.ConnectionState.Disconnected
    )
    val connectionState: StateFlow<GattConnectionManager.ConnectionState> = _connectionState.asStateFlow()

    private val _services = MutableStateFlow<List<GattConnectionManager.DiscoveredService>>(emptyList())
    val services: StateFlow<List<GattConnectionManager.DiscoveredService>> = _services.asStateFlow()

    private val _rawFragments = MutableStateFlow<List<BleRawFragment>>(emptyList())
    val rawFragments: StateFlow<List<BleRawFragment>> = _rawFragments.asStateFlow()

    private var gattManager: GattConnectionManager? = null

    private val _deviceInfo = MutableStateFlow<BleDeviceInfo?>(null)
    val deviceInfo: StateFlow<BleDeviceInfo?> = _deviceInfo.asStateFlow()

    init {
        viewModelScope.launch {
            _rawFragments.value = gattDao.getRawFragmentsForDevice(macAddress)
        }
        viewModelScope.launch {
            trackedDao.get(macAddress)?.let {
                _isFavorite.value = it.isFavorite
            }
        }
        viewModelScope.launch {
            val sightings = db.bleSightingDao().getHistoryForMac(macAddress)
            sightings.firstOrNull()?.let { s ->
                _deviceInfo.value = BleDeviceInfo(
                    macAddress = s.macAddress,
                    deviceName = s.deviceName,
                    rssi = s.rssi,
                    txPowerLevel = s.txPower,
                    serviceUuids = emptyList(),
                    iBeaconMajor = null,
                    iBeaconMinor = null,
                    iBeaconUuid = null,
                    lastSeenMillis = System.currentTimeMillis()
                )
            }
        }
    }

    fun toggleFavorite() {
        _isFavorite.value = !_isFavorite.value
        viewModelScope.launch {
            trackedDao.setFavorite(macAddress, _isFavorite.value)
        }
    }

    fun setHeatmap(enabled: Boolean) {
        _showHeatmap.value = enabled
    }

    fun setHistoryStyle(style: HistoryStyle) {
        _historyStyle.value = style
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice() {
        val bluetoothManager = getApplication<Application>()
            .getSystemService(BluetoothManager::class.java)
        val adapter = bluetoothManager.adapter ?: return
        val device = try { adapter.getRemoteDevice(macAddress) } catch (e: Exception) { null } ?: return

        gattManager?.release()
        gattManager = GattConnectionManager(getApplication(), device).apply {
            viewModelScope.launch {
                connectionState.collect { _connectionState.value = it }
            }
            viewModelScope.launch {
                services.collect { discovered ->
                    _services.value = discovered
                    // Persist to DB
                    gattDao.clearServicesForDevice(macAddress)
                    discovered.forEach { svc ->
                        val serviceId = gattDao.insertService(
                            BleGattService(
                                deviceMac = macAddress,
                                uuid = svc.uuid.toString(),
                                serviceType = svc.type,
                                instanceId = svc.instanceId
                            )
                        )
                        gattDao.insertCharacteristics(
                            svc.characteristics.map { char ->
                                BleGattCharacteristic(
                                    serviceId = serviceId,
                                    uuid = char.uuid.toString(),
                                    properties = char.properties,
                                    permissions = char.permissions,
                                    instanceId = char.instanceId,
                                    descriptorsJson = char.descriptors.joinToString(",") { it.toString() }
                                )
                            }
                        )
                    }
                }
            }
            connect()
        }
    }

    fun disconnect() {
        gattManager?.disconnect()
    }

    fun analyseDevice() {
        val mgr = gattManager ?: return
        val state = _connectionState.value
        if (state !is GattConnectionManager.ConnectionState.Ready) {
            connectToDevice()
            return
        }

        // Read device info service if available
        _services.value.find { it.uuid == UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb") }
            ?.let { service ->
                service.characteristics.find {
                    it.uuid == UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
                }?.let { char ->
                    mgr.readCharacteristic(service.uuid, char.uuid)
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        gattManager?.release()
    }
}
