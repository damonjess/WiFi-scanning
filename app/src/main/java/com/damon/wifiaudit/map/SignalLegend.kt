package com.damon.wifiaudit.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damon.wifiaudit.ui.theme.TextMuted

@Composable
fun SignalLegend(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1A1A23).copy(alpha = 0.9f),
        modifier = modifier.padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Signal", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Spacer(modifier = Modifier.height(6.dp))
            LegendItem(color = Color(0xFF00E676), label = "Strong (-50+ dBm)")
            LegendItem(color = Color(0xFFFFEA00), label = "Fair (-50 to -70)")
            LegendItem(color = Color(0xFFFF3D00), label = "Weak (< -70)")
            
            Spacer(modifier = Modifier.height(12.dp))
            Text("Type", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Spacer(modifier = Modifier.height(6.dp))
            LegendItem(color = Color(0xFF00BCD4), label = "WiFi Network")
            LegendItem(color = Color(0xFFE040FB), label = "Bluetooth Device")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
    }
}
