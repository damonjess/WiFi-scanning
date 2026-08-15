package com.damon.wifiaudit.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damon.wifiaudit.ui.theme.TextMuted

@Composable
fun MapHeader(
    pointCount: Int,
    playback: Int?,
    onScrub: (Int?) -> Unit,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 16.dp, end = 16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1A1A23).copy(alpha = 0.92f),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Play/Pause
                    IconButton(onClick = onPlayPause, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }

                    // Scrubber
                    if (pointCount > 0) {
                        Slider(
                            value = (playback ?: (pointCount - 1)).toFloat(),
                            onValueChange = { onScrub(it.toInt()) },
                            onValueChangeFinished = { if (!isPlaying) onScrub(null) },
                            valueRange = 0f..((pointCount - 1).coerceAtLeast(0)).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF8C9EFF),
                                activeTrackColor = Color(0xFF8C9EFF),
                                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = playback?.let { "${it + 1}/$pointCount" } ?: "$pointCount pts",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                playback?.let { idx ->
                    Text(
                        text = "Showing scan ${idx + 1}",
                        fontSize = 11.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(start = 44.dp)
                    )
                }
            }
        }
    }
}
