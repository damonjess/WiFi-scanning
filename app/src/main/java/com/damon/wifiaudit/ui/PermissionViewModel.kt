package com.damon.wifiaudit.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.damon.wifiaudit.util.PermissionRequirements
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PermissionViewModel(application: Application) : AndroidViewModel(application) {

    private val _allGranted = MutableStateFlow(false)
    val allGranted: StateFlow<Boolean> = _allGranted.asStateFlow()

    private val _backgroundLocationGranted = MutableStateFlow(false)
    val backgroundLocationGranted: StateFlow<Boolean> = _backgroundLocationGranted.asStateFlow()

    private val _batteryOptimizationBypassed = MutableStateFlow(false)
    val batteryOptimizationBypassed: StateFlow<Boolean> = _batteryOptimizationBypassed.asStateFlow()

    fun refreshStatus() {
        val context = getApplication<Application>()
        _allGranted.value = PermissionRequirements.requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }

        val bgPerm = PermissionRequirements.backgroundLocationPermission()
        _backgroundLocationGranted.value = bgPerm == null ||
            ContextCompat.checkSelfPermission(context, bgPerm) == PackageManager.PERMISSION_GRANTED

        val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        _batteryOptimizationBypassed.value = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
