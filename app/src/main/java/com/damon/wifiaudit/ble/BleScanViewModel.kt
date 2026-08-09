package com.damon.wifiaudit.ble

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class BleScanViewModel(application: Application) : AndroidViewModel(application) {

    private val scanManager = BleScanManager(application)

    val isScanning: StateFlow<Boolean> = scanManager.isScanning

    val deviceList: StateFlow<List<BleDeviceInfo>> = scanManager.devices
        .map { map -> map.values.sortedByDescending { it.rssi } }
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
