package com.damon.wifiaudit.util

import android.content.Context
import android.graphics.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

object MapUtils {
    fun createHeatmapBitmap(context: Context, radius: Int = 100): Bitmap {
        val bitmap = Bitmap.createBitmap(radius * 2, radius * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = true }
        
        // Radial gradient from center
        val colors = intArrayOf(
            Color(0xFF76FF03).copy(alpha = 0.6f).toArgb(),
            Color(0xFF76FF03).copy(alpha = 0.2f).toArgb(),
            Color.Transparent.toArgb()
        )
        val positions = floatArrayOf(0f, 0.5f, 1f)
        
        paint.shader = RadialGradient(
            radius.toFloat(), radius.toFloat(), radius.toFloat(),
            colors, positions, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(radius.toFloat(), radius.toFloat(), radius.toFloat(), paint)
        return bitmap
    }
}
