package com.damon.wifiaudit.ble

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.data.WardrivingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BleScanViewModel(application: Application) : AndroidViewModel(application) {

    private val scanManager = BleScanManager(application)
    private val targetDao = AppDatabase.getInstance(application).targetDeviceDao()

    val isScanning: StateFlow<Boolean> = scanManager.isScanning

    val deviceList: StateFlow<List<BleDeviceInfo>> = scanManager.devices
        .map { map ->
            // Classify and save newly discovered devices to the targets database
            map.values.forEach { device ->
                val classification = TargetClassifier.classify(device)
                if (classification != null) {
                    viewModelScope.launch {
                        try {
                            val existing = targetDao.getDevice(device.macAddress.uppercase())
                            if (existing == null) {
                                targetDao.upsert(
                                    com.damon.wifiaudit.data.TargetDevice(
                                        macAddress = device.macAddress.uppercase(),
                                        deviceName = classification.label,
                                        category = classification.category,
                                        signalStrength = device.rssi,
                                        firstSeen = System.currentTimeMillis(),
                                        lastSeen = System.currentTimeMillis(),
                                        latitude = null,
                                        longitude = null
                                    )
                                )
                            } else {
                                targetDao.upsert(
                                    existing.copy(
                                        lastSeen = System.currentTimeMillis(),
                                        signalStrength = device.rssi
                                    )
                                )
                            }
                        } catch (_: Exception) { }
                    }
                }
            }
            map.values.sortedByDescending { it.rssi }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
