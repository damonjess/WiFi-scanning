package com.damon.wifiaudit.ui

import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.damon.wifiaudit.scan.ScanCycleSnapshot
import com.damon.wifiaudit.scan.WardrivingService
import com.damon.wifiaudit.ui.theme.CyanAccent
import com.damon.wifiaudit.ui.theme.LimeAccent
import com.damon.wifiaudit.ui.theme.MagentaAccent
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
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // SCAN CONTROL CARD
        ScanControlCard(
            isRunning = serviceRunning,
            elapsedSeconds = elapsedSeconds,
            onToggle = {
                val intent = Intent(context, WardrivingService::class.java)
                if (serviceRunning) {
                    context.stopService(intent)
                } else {
                    ContextCompat.startForegroundService(context, intent)
                    startTime = System.currentTimeMillis()
                }
                serviceRunning = !serviceRunning
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // STATS ROW
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GradientStatCard(
                label = "Wi-Fi",
                value = snapshot.wifiResults.size.toString(),
                icon = Icons.Default.Wifi,
                gradientColors = listOf(CyanAccent.copy(alpha = 0.15f), CyanAccent.copy(alpha = 0.03f)),
                accentColor = CyanAccent,
                modifier = Modifier.weight(1f)
            )
            GradientStatCard(
                label = "BLE",
                value = snapshot.bleDevices.size.toString(),
                icon = Icons.Default.Bluetooth,
                gradientColors = listOf(MagentaAccent.copy(alpha = 0.15f), MagentaAccent.copy(alpha = 0.03f)),
                accentColor = MagentaAccent,
                modifier = Modifier.weight(1f)
            )
            GradientStatCard(
                label = "Saved",
                value = snapshot.cyclesWritten.toString(),
                icon = Icons.Default.CloudUpload,
                gradientColors = listOf(LimeAccent.copy(alpha = 0.15f), LimeAccent.copy(alpha = 0.03f)),
                accentColor = LimeAccent,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // GPS CARD
        GpsCard(snapshot)
    }
}

@Composable
private fun ScanControlCard(
    isRunning: Boolean,
    elapsedSeconds: Long,
    onToggle: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Restart),
        label = "fade"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            // Pulse ring behind
            if (isRunning) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(scale)
                        .alpha(alpha)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = if (isRunning) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    if (isRunning) "SCANNING" else "IDLE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isRunning) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
                if (isRunning) {
                    Text(
                        formatElapsed(elapsedSeconds),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        if (isRunning) "STOP SCANNING" else "START SCANNING",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun GradientStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(gradientColors))
                .padding(16.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun GpsCard(snapshot: ScanCycleSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CURRENT FIX",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                if (snapshot.latitude != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(LimeAccent, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = LimeAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (snapshot.latitude != null)
                    "${"%.5f".format(snapshot.latitude)}, ${"%.5f".format(snapshot.longitude)}"
                else "Waiting for GPS…",
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold
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

private fun formatElapsed(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
