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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.damon.wifiaudit.util.PermissionRequirements

@Composable
fun PermissionGateScreen(
    onAllGranted: @Composable () -> Unit
) {
    val context = LocalContext.current
    val viewModel: PermissionViewModel = viewModel()
    val allGranted by viewModel.allGranted.collectAsState()
    val backgroundGranted by viewModel.backgroundLocationGranted.collectAsState()

    val batteryBypassed by viewModel.batteryOptimizationBypassed.collectAsState()
    var honorBypassDismissed by remember { mutableStateOf(false) }

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

    if (allGranted && (batteryBypassed || Build.MANUFACTURER.uppercase() != "HONOR" || honorBypassDismissed)) {
        // Foreground perms are in. 
        // For Honor, we strongly suggest battery bypass before proceeding.
        onAllGranted()
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Setup Required", style = MaterialTheme.typography.headlineSmall)
        
        if (!allGranted) {
            PermissionSection(onClick = {
                multiPermissionLauncher.launch(PermissionRequirements.requiredPermissions().toTypedArray())
            })
        }

        if (allGranted && !batteryBypassed) {
            BatteryOptimizationSection(
                context = context,
                onDismiss = { honorBypassDismissed = true }
            )
        }

        if (allGranted && !backgroundGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            BackgroundLocationSection {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
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
private fun BatteryOptimizationSection(context: android.content.Context, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Honor/MagicOS Power Fix", style = MaterialTheme.typography.titleMedium)
            Text(
                "Your device will kill this app in the background. " +
                "Please set Battery Optimization to 'Don't Optimize'.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = {
                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }) {
                Text("Request Bypass")
            }
            
            if (android.os.Build.MANUFACTURER.equals("HONOR", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    try {
                        val intent = android.content.Intent().apply {
                            setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
                    }
                }) {
                    Text("Honor 'App Launch' Settings")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Continue anyway")
            }
        }
    }
}

@Composable
private fun BackgroundLocationSection(onClick: () -> Unit) {
    Spacer(modifier = Modifier.height(16.dp))
    Text("Background scanning is recommended for wardriving.", style = MaterialTheme.typography.bodySmall)
    Button(onClick = onClick) { Text("Allow background scanning") }
}
