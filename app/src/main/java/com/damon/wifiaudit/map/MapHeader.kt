package com.damon.wifiaudit.map

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    focusedScanLabel: String? = null,
    modifier: Modifier = Modifier,
    filterContent: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1A1A23).copy(alpha = 0.95f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top row: Playback controls + Point counter (prevents vertical letter wrapping)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (focusedScanLabel == null) {
                        IconButton(onClick = onPlayPause, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }

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
                    }

                    // Constrained text block that will never wrap letter-by-letter
                    Text(
                        text = focusedScanLabel ?: playback?.let { "${it + 1}/$pointCount" } ?: "$pointCount pts",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.wrapContentWidth(Alignment.End)
                    )
                }

                // Scrollable chips row for filters so they never overflow off-screen
                if (filterContent != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        filterContent()
                    }
                }

                if (focusedScanLabel == null) {
                    playback?.let { idx ->
                        Text(
                            text = "Showing scan ${idx + 1}",
                            fontSize = 11.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
