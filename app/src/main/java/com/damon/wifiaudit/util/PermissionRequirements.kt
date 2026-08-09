package com.damon.wifiaudit.util

import android.Manifest
import android.os.Build

object PermissionRequirements {

    /** Permissions requested together in one runtime prompt batch. */
    fun requiredPermissions(): List<String> {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            perms += Manifest.permission.POST_NOTIFICATIONS
        }

        return perms
    }

    /**
     * Background location is requested separately and only AFTER foreground
     * location is granted — the OS rejects a combined prompt on API 30+ and
     * will silently ignore the background permission if bundled together.
     */
    fun backgroundLocationPermission(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        } else null
    }
}
