package com.damon.wifiaudit.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun MapControls(
    modifier: Modifier = Modifier,
    onRecenter: () -> Unit,
    showWifi: Boolean,
    showBle: Boolean,
    showGrid: Boolean,
    onToggleWifi: () -> Unit,
    onToggleBle: () -> Unit,
    onToggleGrid: () -> Unit
) {
    Column(
        modifier = modifier.padding(end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MapControlButton(
            icon = Icons.Default.MyLocation,
            onClick = onRecenter
        )
        MapControlButton(
            icon = Icons.Default.Wifi,
            tint = if (showWifi) Color(0xFF00BCD4) else Color.Gray,
            onClick = onToggleWifi
        )
        MapControlButton(
            icon = Icons.Default.Bluetooth,
            tint = if (showBle) Color(0xFFE040FB) else Color.Gray,
            onClick = onToggleBle
        )
        MapControlButton(
            icon = Icons.Default.GridOn,
            tint = if (showGrid) Color.White else Color.Gray,
            onClick = onToggleGrid
        )
    }
}

@Composable
private fun MapControlButton(
    icon: ImageVector,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color(0xFF1A1A23).copy(alpha = 0.9f),
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}
