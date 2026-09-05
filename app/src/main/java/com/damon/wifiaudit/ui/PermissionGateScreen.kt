package com.damon.wifiaudit.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.damon.wifiaudit.util.PermissionRequirements

@Composable
fun PermissionGateScreen(
    onAllGranted: @Composable () -> Unit
) {
    val viewModel: PermissionViewModel = viewModel()
    val allGranted by viewModel.allGranted.collectAsState()
    val backgroundGranted by viewModel.backgroundLocationGranted.collectAsState()

    val multiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshStatus()
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshStatus()
    }

    LaunchedEffect(Unit) {
        viewModel.refreshStatus()
    }

    if (allGranted) {
        if (!backgroundGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Setup Required", style = MaterialTheme.typography.headlineSmall)
                BackgroundLocationSection {
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
        } else {
            onAllGranted()
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Setup Required", style = MaterialTheme.typography.headlineSmall)
        PermissionSection(onClick = {
            multiPermissionLauncher.launch(PermissionRequirements.requiredPermissions().toTypedArray())
        })
    }
}

@Composable
private fun PermissionSection(onClick: () -> Unit) {
    Text(
        "This app needs location, Bluetooth, and notification access to scan " +
        "for Wi-Fi networks and BLE devices.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
    Spacer(modifier = Modifier.height(24.dp))
    Button(onClick = onClick) { Text("Grant Permissions") }
}

@Composable
private fun BackgroundLocationSection(onClick: () -> Unit) {
    Spacer(modifier = Modifier.height(16.dp))
    Text("Background scanning is recommended for wardriving.", style = MaterialTheme.typography.bodySmall)
    Button(onClick = onClick) { Text("Allow background scanning") }
}

