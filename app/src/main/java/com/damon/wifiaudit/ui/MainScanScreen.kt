package com.damon.wifiaudit.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.damon.wifiaudit.scan.WardrivingService
import kotlinx.coroutines.delay

@Composable
fun MainScanScreen() {
    val context = LocalContext.current
    val statusViewModel: WardrivingStatusViewModel = viewModel()
    val snapshot by statusViewModel.snapshot.collectAsStateWithLifecycle()
    var serviceRunning by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf(0L) }
    var elapsedSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(serviceRunning) {
        while (serviceRunning) {
            elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
            delay(1000)
            // Note: In a real app, you'd want to handle activity manager state to persist startTime
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Big status indicator
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = if (serviceRunning) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    if (serviceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (serviceRunning) "Scanning" else "Idle",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (serviceRunning) {
                    Text(
                        formatElapsed(elapsedSeconds),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val intent = Intent(context, WardrivingService::class.java)
                        if (serviceRunning) {
                            context.stopService(intent)
                        } else {
                            ContextCompat.startForegroundService(context, intent)
                            startTime = System.currentTimeMillis()
                        }
                        serviceRunning = !serviceRunning
                    }
                ) {
                    Text(if (serviceRunning) "Stop Scanning" else "Start Scanning")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick-glance counter row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CounterCard("Wi-Fi", snapshot.wifiResults.size.toString(), Modifier.weight(1f))
            CounterCard("BLE", snapshot.bleDevices.size.toString(), Modifier.weight(1f))
            CounterCard("Saved", snapshot.cyclesWritten.toString(), Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Current fix", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (snapshot.latitude != null)
                        "${"%.5f".format(snapshot.latitude)}, ${"%.5f".format(snapshot.longitude)}"
                    else "Waiting for GPS…",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (snapshot.lastCommitMillis > 0)
                        "Last write: ${(System.currentTimeMillis() - snapshot.lastCommitMillis) / 1000}s ago"
                    else "No writes yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CounterCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun formatElapsed(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
