package com.damon.wifiaudit.map

import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun SightingMapScreen(viewModel: MapViewModel = viewModel()) {
    val context = LocalContext.current
    val wifiPoints by viewModel.wifiLocations.collectAsState()
    val blePoints by viewModel.bleLocations.collectAsState()

    // Load data once when the screen appears
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    // Proper osmdroid lifecycle
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // Rebuild overlays whenever the data changes
    LaunchedEffect(wifiPoints, blePoints) {
        mapView.overlays.clear()

        // Create clean circular markers to avoid the "purple blob" glitch
        val wifiIcon = createCircleMarker(Color.BLUE)
        val bleIcon = createCircleMarker(Color.MAGENTA)

        val wifiClusterer = RadiusMarkerClusterer(context).apply {
            setRadius(80)
            textPaint.textSize = 32f
            textPaint.color = Color.WHITE
        }
        val bleClusterer = RadiusMarkerClusterer(context).apply {
            setRadius(80)
            textPaint.textSize = 32f
            textPaint.color = Color.WHITE
        }

        val allPoints = mutableListOf<GeoPoint>()

        wifiPoints.forEach { record ->
            val geo = GeoPoint(record.latitude, record.longitude)
            allPoints.add(geo)
            val marker = Marker(mapView).apply {
                position = geo
                title = "WiFi: ${record.ssid.ifBlank { "<hidden>" }}"
                snippet = "${record.bssid} • ${record.encryption} • ${record.rssi} dBm"
                icon = android.graphics.drawable.BitmapDrawable(context.resources, wifiIcon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            wifiClusterer.add(marker)
        }

        blePoints.forEach { record ->
            val geo = GeoPoint(record.latitude, record.longitude)
            allPoints.add(geo)
            val marker = Marker(mapView).apply {
                position = geo
                title = "BLE: ${record.deviceName ?: record.macAddress}"
                snippet = "${record.macAddress} • ${record.rssi} dBm"
                icon = android.graphics.drawable.BitmapDrawable(context.resources, bleIcon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            bleClusterer.add(marker)
        }

        if (wifiPoints.isNotEmpty()) mapView.overlays.add(wifiClusterer)
        if (blePoints.isNotEmpty()) mapView.overlays.add(bleClusterer)

        // Center on the data
        if (allPoints.isNotEmpty()) {
            if (allPoints.size == 1) {
                mapView.controller.setZoom(17.0)
                mapView.controller.animateTo(allPoints.first())
            } else {
                val box = BoundingBox.fromGeoPoints(allPoints)
                mapView.post {
                    mapView.zoomToBoundingBox(box, true, 100)
                }
            }
        }

        mapView.invalidate()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Helpful empty-state so you know whether the problem is data or rendering
        if (wifiPoints.isEmpty() && blePoints.isEmpty()) {
            Text(
                text = "No sightings yet.\nRun a wardriving scan first.",
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private fun createCircleMarker(color: Int): android.graphics.Bitmap {
    val size = 40
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        this.color = color
        isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    
    // Add a small white border
    paint.color = Color.WHITE
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 4f
    canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 2f, paint)
    
    return bitmap
}
