package com.damon.wifiaudit.util

import android.content.Context
import android.content.Intent
import android.provider.Settings

object DeveloperOptionsHelper {

    fun isDeveloperOptionsEnabled(context: Context): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) != 0
    }

    /** Opens Developer Options directly if enabled, otherwise the main Settings screen. */
    fun openDeveloperOptionsOrSettings(context: Context) {
        val intent = if (isDeveloperOptionsEnabled(context)) {
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        } else {
            // Can't jump straight to Developer Options if it's not unlocked yet —
            // send the user to About Phone so they can tap Build Number themselves
            Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
