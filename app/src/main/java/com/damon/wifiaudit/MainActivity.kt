package com.damon.wifiaudit

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
import androidx.lifecycle.lifecycleScope
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.data.oui.OuiCsvImporter
import com.damon.wifiaudit.data.oui.StandardGattSeeder
import com.damon.wifiaudit.map.OsmConfig
import com.damon.wifiaudit.ui.AppRoot
import com.damon.wifiaudit.ui.PermissionGateScreen
import com.damon.wifiaudit.ui.theme.WiFiAuditTheme
import com.damon.wifiaudit.vendor.OuiVendorLookup
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OsmConfig.initialize(applicationContext)
        lifecycleScope.launch {
            OuiVendorLookup.initialize(applicationContext)
            OuiCsvImporter.importIfNeeded(applicationContext)
            val db = AppDatabase.getInstance(applicationContext)
            StandardGattSeeder.seedIfNeeded(db)
        }
        setContent {
            WiFiAuditTheme {
                PermissionGateScreen { AppRoot() }
            }
        }
    }
}
