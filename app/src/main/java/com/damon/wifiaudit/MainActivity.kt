package com.damon.wifiaudit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.damon.wifiaudit.ble.ProximityMonitorService
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.data.oui.OuiCsvImporter
import com.damon.wifiaudit.data.oui.StandardGattSeeder
import com.damon.wifiaudit.map.OsmConfig
import com.damon.wifiaudit.ui.AppRoot
import com.damon.wifiaudit.ui.PermissionGateScreen
import com.damon.wifiaudit.ui.theme.WiFiAuditTheme
import com.damon.wifiaudit.vendor.OuiVendorLookup
import android.util.Log
import com.damon.wifiaudit.util.LockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val lockdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ProximityMonitorService.ACTION_LOCKDOWN) {
                LockManager.lock()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OsmConfig.initialize(applicationContext)

        ContextCompat.registerReceiver(
            this,
            lockdownReceiver,
            IntentFilter(ProximityMonitorService.ACTION_LOCKDOWN),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    OuiVendorLookup.initialize(applicationContext)
                    OuiCsvImporter.importIfNeeded(applicationContext)
                    val db = AppDatabase.getInstance(applicationContext)
                    StandardGattSeeder.seedIfNeeded(db)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to initialize dependencies", e)
            }
        }
        setContent {
            WiFiAuditTheme {
                PermissionGateScreen { AppRoot() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(lockdownReceiver)
    }
}
