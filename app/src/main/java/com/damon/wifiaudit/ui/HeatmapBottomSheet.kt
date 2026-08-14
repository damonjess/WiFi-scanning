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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.remember
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
    if (points.isEmpty()) return

    val coords = remember(points) {
        val lats = points.mapNotNull { it.latitude }
        val lngs = points.mapNotNull { it.longitude }
        val hasCoords = lats.isNotEmpty() && lngs.isNotEmpty()
        val latVar = if (hasCoords) lats.maxOrNull()!! - lats.minOrNull()!! else 0.0
        val lngVar = if (hasCoords) lngs.maxOrNull()!! - lngs.minOrNull()!! else 0.0
        val vary = latVar > 1e-5 && lngVar > 1e-5

        // Pre-calculate canvas coordinates
        if (vary) {
            val minLat = lats.minOrNull()!!; val maxLat = lats.maxOrNull()!!
            val minLng = lngs.minOrNull()!!; val maxLng = lngs.maxOrNull()!!
            val latR = (maxLat - minLat).coerceAtLeast(1e-6)
            val lngR = (maxLng - minLng).coerceAtLeast(1e-6)
            points.map { pt ->
                val x = ((pt.longitude!! - minLng) / lngR).toFloat()
                val y = ((maxLat - pt.latitude!!) / latR).toFloat() // invert Y
                Triple(x, y, pt.rssi)
            }
        } else {
            // No GPS variance: spiral layout by index
            points.mapIndexed { index, pt ->
                val angle = index * 0.85f
                val radius = (index + 1) * 0.06f // normalized 0..1
                val x = 0.5f + kotlin.math.cos(angle) * radius
                val y = 0.5f + kotlin.math.sin(angle) * radius
                Triple(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), pt.rssi)
            }
        }
    }

    val showGpsWarning = remember(points) {
        val lats = points.mapNotNull { it.latitude }
        val lngs = points.mapNotNull { it.longitude }
        lats.isEmpty() || (lats.maxOrNull()!! - lats.minOrNull()!! < 1e-5 && lngs.maxOrNull()!! - lngs.minOrNull()!! < 1e-5)
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Background
            drawRect(Color(0xFF0F0F15))

            // Grid
            val grid = Color.White.copy(alpha = 0.04f)
            repeat(6) { i ->
                val x = w * i / 5f
                val y = h * i / 5f
                drawLine(grid, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }

            // Center crosshair
            drawLine(Color.White.copy(alpha = 0.08f), Offset(w/2, 0f), Offset(w/2, h), strokeWidth = 1f)
            drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, h/2), Offset(w, h/2), strokeWidth = 1f)

            // Draw points with 10% padding inset
            val padX = w * 0.1f
            val padY = h * 0.1f
            val drawW = w * 0.8f
            val drawH = h * 0.8f

            coords.forEachIndexed { _, (nx, ny, rssi) ->
                val cx = padX + nx * drawW
                val cy = padY + ny * drawH

                val baseColor = when {
                    rssi > -50 -> Color(0xFF00E676)
                    rssi > -65 -> Color(0xFF76FF03)
                    rssi > -80 -> Color(0xFFFFEA00)
                    else -> Color(0xFFFF3D00)
                }

                // Outer glow
                drawCircle(
                    color = baseColor.copy(alpha = 0.12f),
                    radius = 32f,
                    center = Offset(cx, cy)
                )
                // Mid glow
                drawCircle(
                    color = baseColor.copy(alpha = 0.3f),
                    radius = 14f,
                    center = Offset(cx, cy)
                )
                // Core dot
                drawCircle(
                    color = baseColor.copy(alpha = 0.95f),
                    radius = 5.5f,
                    center = Offset(cx, cy)
                )
            }

            // Draw "start" (first sample) marker
            val (sx, sy, _) = coords.first()
            val scx = padX + sx * drawW
            val scy = padY + sy * drawH
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = 3f,
                center = Offset(scx, scy),
                style = Stroke(width = 2f)
            )
        }

        // GPS warning overlay
        if (showGpsWarning) {
            Surface(
                color = Color(0xFF1A1A23).copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Text(
                    text = "GPS not moving — using time layout",
                    fontSize = 11.sp,
                    color = Color(0xFFFFB74D),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
