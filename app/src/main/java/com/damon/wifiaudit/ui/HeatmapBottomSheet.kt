package com.damon.wifiaudit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damon.wifiaudit.data.entity.RssiHeatmapPoint
import com.damon.wifiaudit.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapBottomSheet(
    points: List<RssiHeatmapPoint>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A23)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Signal Heatmap",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "${points.size} samples",
                fontSize = 12.sp,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (points.size < 3) {
                Text(
                    text = "Walk around to collect more data points.",
                    fontSize = 14.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                HeatmapCanvas(points = points, modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F0F15))
                )

                // Stats
                val avgRssi = points.map { it.rssi }.average().toInt()
                val minRssi = points.minOf { it.rssi }
                val maxRssi = points.maxOf { it.rssi }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatChip("Average", "$avgRssi dBm")
                    StatChip("Best", "$maxRssi dBm", Color(0xFF81C784))
                    StatChip("Worst", "$minRssi dBm", Color(0xFFE57373))
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color = Color(0xFF8C9EFF)) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = TextMuted
        )
    }
}

@Composable
private fun HeatmapCanvas(points: List<RssiHeatmapPoint>, modifier: Modifier = Modifier) {
    val lats = points.mapNotNull { it.latitude }
    val lngs = points.mapNotNull { it.longitude }
    if (lats.isEmpty() || lngs.isEmpty()) return

    val minLat = lats.minOrNull() ?: return
    val maxLat = lats.maxOrNull() ?: return
    val minLng = lngs.minOrNull() ?: return
    val maxLng = lngs.maxOrNull() ?: return

    val latRange = (maxLat - minLat).coerceAtLeast(0.0001)
    val lngRange = (maxLng - minLng).coerceAtLeast(0.0001)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        points.forEach { pt ->
            if (pt.latitude == null || pt.longitude == null) return@forEach
            val x = ((pt.longitude - minLng) / lngRange * width).toFloat()
            val y = ((maxLat - pt.latitude) / latRange * height).toFloat()
            val radius = 24.dp.toPx()

            val color = when {
                pt.rssi > -50 -> Color(0xFF00E676)
                pt.rssi > -65 -> Color(0xFF76FF03)
                pt.rssi > -80 -> Color(0xFFFFEA00)
                else -> Color(0xFFFF3D00)
            }

            drawCircle(
                color = color.copy(alpha = 0.4f),
                radius = radius,
                center = Offset(x, y)
            )
            drawCircle(
                color = color.copy(alpha = 0.8f),
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}
