package com.damon.wifiaudit.ble

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.data.TargetDevice
import com.damon.wifiaudit.data.WardrivingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import kotlinx.coroutines.Dispatchers

class BleScanViewModel(application: Application) : AndroidViewModel(application) {

    private val scanManager = BleScanManager(application)
    private val targetDao = AppDatabase.getInstance(application).targetDeviceDao()

    val isScanning: StateFlow<Boolean> = scanManager.isScanning

    val deviceList: StateFlow<List<BleDeviceInfo>> = scanManager.devices
        .map { map -> map.values.sortedByDescending { it.rssi } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Asynchronously classify and save targets to DB on Dispatchers.IO with throttling
        viewModelScope.launch(Dispatchers.IO) {
            val processedMacs = mutableMapOf<String, Long>()
            scanManager.devices.collect { map ->
                val now = System.currentTimeMillis()
                map.values.forEach { device ->
                    val mac = device.macAddress.uppercase()
                    val lastSaved = processedMacs[mac] ?: 0L
                    if (now - lastSaved > 10_000L) {
                        val classification = TargetClassifier.classify(device)
                        if (classification != null) {
                            processedMacs[mac] = now
                            try {
                                val existing = targetDao.getDevice(mac)
                                if (existing == null) {
                                    targetDao.upsert(
                                        TargetDevice(
                                            macAddress = mac,
                                            deviceName = classification.label,
                                            category = classification.category,
                                            signalStrength = device.rssi,
                                            firstSeen = now,
                                            lastSeen = now,
                                            latitude = null,
                                            longitude = null
                                        )
                                    )
                                } else {
                                    targetDao.upsert(
                                        existing.copy(
                                            lastSeen = now,
                                            signalStrength = device.rssi
                                        )
                                    )
                                }
                            } catch (_: Exception) { }
                        }
                    }
                }
            }
        }
    }

    fun startScanning() {
        if (!scanManager.isBluetoothEnabled()) return
        scanManager.startScan()
    }

    fun stopScanning() {
        scanManager.stopScan()
    }

    fun clearResults() {
        scanManager.clearResults()
    }

    override fun onCleared() {
        super.onCleared()
        scanManager.stopScan()
    }
}
