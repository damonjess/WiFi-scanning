package com.damon.wifiaudit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damon.wifiaudit.ui.theme.CyanAccent
import com.damon.wifiaudit.ui.theme.DarkSurface
import com.damon.wifiaudit.ui.theme.DarkSurfaceElevated
import com.damon.wifiaudit.ui.theme.TextMuted

@Composable
fun StateBadge(state: String) {
    val (bg, text) = when (state) {
        "STP" -> Color(0xFF1B5E20) to Color(0xFF81C784)
        "RST" -> Color(0xFF5D4037) to Color(0xFFFFB74D)
        "CON" -> Color(0xFF0D47A1) to Color(0xFF64B5F6)
        else -> DarkSurfaceElevated to TextMuted
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = text,
                letterSpacing = 1.sp
            )
            Text(
                text = "ⓘ",
                fontSize = 10.sp,
                color = text,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

@Composable
fun SignalBadge(rssi: Int) {
    val color = when {
        rssi > -60 -> CyanAccent
        rssi > -80 -> Color(0xFFFFB300)
        else -> Color(0xFFFF5252)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = "$rssi dBm",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}

@Composable
fun FilterChipStyled(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isActive) CyanAccent.copy(alpha = 0.15f) else DarkSurface,
        border = BorderStroke(
            1.dp,
            if (isActive) CyanAccent.copy(alpha = 0.4f) else DarkSurfaceElevated
        ),
        modifier = Modifier.height(32.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) CyanAccent else TextMuted
            )
        }
    }
}
