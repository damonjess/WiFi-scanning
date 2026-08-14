package com.damon.wifiaudit.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class DeviceDetailViewModelFactory(
    private val application: Application,
    private val macAddress: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeviceDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DeviceDetailViewModel(application, macAddress) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
