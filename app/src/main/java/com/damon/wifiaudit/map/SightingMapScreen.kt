package com.damon.wifiaudit.map

import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.damon.wifiaudit.data.SessionSummary
import com.damon.wifiaudit.vendor.OuiVendorLookup
import com.damon.wifiaudit.watchdog.SurveillanceDeviceWatchdog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun SightingMapScreen(viewModel: MapViewModel = viewModel()) {
    val context = LocalContext.current
    val wifiPoints by viewModel.wifiLocations.collectAsState()
    val blePoints by viewModel.bleLocations.collectAsState()
    val trackPoints by viewModel.trackPoints.collectAsState()
    val showTrack by viewModel.showTrack.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val selectedSessionId by viewModel.selectedSessionId.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    val locationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), MapView(context)).apply {
            enableMyLocation()
        }
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
            controller.setZoom(16.0)
            
            // Set a default center if no data yet (e.g. London)
            controller.setCenter(GeoPoint(51.5074, -0.1278))
            
            overlays.add(locationOverlay)
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        locationOverlay.enableMyLocation()
        onDispose {
            locationOverlay.disableMyLocation()
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // Long-lived route overlay — avoid recreating Polyline on every marker refresh
    val routePolyline = remember {
        Polyline().apply {
            outlinePaint.color = Color.CYAN
            outlinePaint.strokeWidth = 8f
            outlinePaint.isAntiAlias = true
        }
    }

    // --- ROUTE only (trackPoints / showTrack) ---
    LaunchedEffect(trackPoints, showTrack) {
        mapView.overlays.remove(routePolyline)

        if (showTrack && trackPoints.size >= 2) {
            val simplified = simplifyTrack(
                trackPoints.map { GeoPoint(it.latitude, it.longitude) },
                toleranceMeters = 10.0
            )
            routePolyline.setPoints(simplified)
            // Index 0 = under markers so pins stay clickable
            mapView.overlays.add(0, routePolyline)
        }
        mapView.invalidate()
    }

    // --- MARKERS only (wifi / ble) ---
    LaunchedEffect(wifiPoints, blePoints) {
        android.util.Log.d(
            "MapScreen",
            "Refreshing markers: ${wifiPoints.size} WiFi, ${blePoints.size} BLE"
        )

        // Clear everything except my-location and the route polyline
        mapView.overlays.removeAll {
            it !is MyLocationNewOverlay && it !== routePolyline
        }

        val wifiIcon = createCircleMarker(Color.BLUE, 50)
        val bleIcon = createCircleMarker(Color.MAGENTA, 50)
        val warningIcon = createCircleMarker(Color.RED, 60)

        // Clusterers for different types with increased radius for dense areas
        val wifiClusterer = RadiusMarkerClusterer(context).apply {
            setIcon(createCircleMarker(Color.BLUE, 80, true))
            setRadius(120) // Increased from 80
            textPaint.textSize = 32f
            textPaint.color = Color.WHITE
        }
        val bleClusterer = RadiusMarkerClusterer(context).apply {
            setIcon(createCircleMarker(Color.MAGENTA, 80, true))
            setRadius(120) // Increased from 80
            textPaint.textSize = 32f
            textPaint.color = Color.WHITE
        }
        val watchdogClusterer = RadiusMarkerClusterer(context).apply {
            setIcon(createCircleMarker(Color.RED, 90, true))
            setRadius(120) // Increased from 80
            textPaint.textSize = 32f
            textPaint.color = Color.WHITE
        }

        val allPoints = mutableListOf<GeoPoint>()
        val random = java.util.Random()

        wifiPoints.forEach { record ->
            val vendor = OuiVendorLookup.lookup(record.bssid)
            val match = SurveillanceDeviceWatchdog.classifyWifi(record.ssid, vendor)

            // Add a tiny bit of jitter (approx 1-2 meters) to prevent perfect stacking
            val latJitter = (random.nextDouble() - 0.5) * 0.00002
            val lonJitter = (random.nextDouble() - 0.5) * 0.00002
            val geo = GeoPoint(record.latitude + latJitter, record.longitude + lonJitter)

            allPoints.add(geo)

            val marker = Marker(mapView).apply {
                position = geo
                title = if (match != null) "⚠ ${match.category.label}: ${record.ssid}" else "WiFi: ${record.ssid}"
                snippet = "${record.bssid} • ${record.encryption} • ${record.rssi} dBm"
                icon = android.graphics.drawable.BitmapDrawable(
                    context.resources,
                    if (match != null) warningIcon else wifiIcon
                )
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            if (match != null) watchdogClusterer.add(marker) else wifiClusterer.add(marker)
        }

        blePoints.forEach { record ->
            val vendor = OuiVendorLookup.lookup(record.macAddress)
            val match = SurveillanceDeviceWatchdog.classifyBle(record.deviceName, vendor)

            // Tiny jitter for BLE as well
            val latJitter = (random.nextDouble() - 0.5) * 0.00002
            val lonJitter = (random.nextDouble() - 0.5) * 0.00002
            val geo = GeoPoint(record.latitude + latJitter, record.longitude + lonJitter)

            allPoints.add(geo)

            val marker = Marker(mapView).apply {
                position = geo
                title = if (match != null) "⚠ ${match.category.label}: ${record.deviceName ?: record.macAddress}" else "BLE: ${record.deviceName ?: record.macAddress}"
                snippet = "${record.macAddress} • ${record.rssi} dBm"
                icon = android.graphics.drawable.BitmapDrawable(
                    context.resources,
                    if (match != null) warningIcon else bleIcon
                )
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            if (match != null) watchdogClusterer.add(marker) else bleClusterer.add(marker)
        }

        if (!wifiClusterer.items.isEmpty()) mapView.overlays.add(wifiClusterer)
        if (!bleClusterer.items.isEmpty()) mapView.overlays.add(bleClusterer)
        if (!watchdogClusterer.items.isEmpty()) mapView.overlays.add(watchdogClusterer)

        // Fit camera to markers (and route if visible)
        val fitPoints = allPoints.toMutableList()
        if (showTrack && trackPoints.size >= 2) {
            simplifyTrack(
                trackPoints.map { GeoPoint(it.latitude, it.longitude) },
                toleranceMeters = 10.0
            ).forEach { fitPoints.add(it) }
        }

        if (fitPoints.isNotEmpty()) {
            if (fitPoints.size == 1) {
                mapView.controller.setZoom(17.0)
                mapView.controller.animateTo(fitPoints.first())
            } else {
                val box = BoundingBox.fromGeoPoints(fitPoints)
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
            modifier = Modifier.fillMaxSize(),
            update = { 
                // MapView state is mostly handled by LaunchedEffect(wifiPoints...)
                // but we could trigger invalidation here if needed
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            var expanded by remember { mutableStateOf(false) }
            val selectedSession = sessions.find { it.id == selectedSessionId }
            val label = selectedSession?.let { formatSessionLabel(it) } ?: "Select Session"

            FilterChip(
                selected = false,
                onClick = { expanded = true },
                label = { Text(label) }
            )

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                sessions.forEach { session ->
                    DropdownMenuItem(
                        text = { Text(formatSessionLabel(session)) },
                        onClick = {
                            viewModel.selectSession(session.id)
                            expanded = false
                        }
                    )
                }
            }

            if (trackPoints.size >= 2) {
                Spacer(modifier = Modifier.height(8.dp))
                FilterChip(
                    selected = showTrack,
                    onClick = { viewModel.toggleTrack() },
                    label = { Text("Route") },
                    leadingIcon = { Icon(Icons.Default.Timeline, contentDescription = null) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            IconButton(
                onClick = {
                    val myLoc = locationOverlay.myLocation
                    if (myLoc != null) {
                        mapView.controller.animateTo(myLoc)
                        mapView.controller.setZoom(18.0)
                    } else {
                        // Fallback to scan snapshot if overlay hasn't fixed yet
                        val currentSnapshot = viewModel.currentSnapshot.value
                        if (currentSnapshot.latitude != null && currentSnapshot.longitude != null) {
                            val geo = GeoPoint(currentSnapshot.latitude, currentSnapshot.longitude)
                            mapView.controller.animateTo(geo)
                            mapView.controller.setZoom(18.0)
                        }
                    }
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Center on my location")
            }
        }

        if (wifiPoints.isEmpty() && blePoints.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(16.dp))
                Text("No sightings yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Start a wardriving scan to populate the map",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Drops intermediate points closer than [toleranceMeters] to the last kept point.
 * Always keeps first and last. Good enough for driving tracks; cheap O(n).
 */
private fun simplifyTrack(
    points: List<GeoPoint>,
    toleranceMeters: Double = 10.0
): List<GeoPoint> {
    if (points.size < 3) return points
    val out = ArrayList<GeoPoint>(points.size / 4 + 2)
    out.add(points.first())
    var last = points.first()
    for (i in 1 until points.lastIndex) {
        val p = points[i]
        if (last.distanceToAsDouble(p) >= toleranceMeters) {
            out.add(p)
            last = p
        }
    }
    out.add(points.last())
    return out
}

private fun formatSessionLabel(s: com.damon.wifiaudit.data.SessionSummary): String {
    val sdf = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
    return "${sdf.format(java.util.Date(s.startTime))} • ${s.wifiCount + s.bleCount} sightings"
}

private fun formatTime(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}

private fun createCircleMarker(color: Int, size: Int = 40, isCluster: Boolean = false): android.graphics.Bitmap {
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        this.color = color
        isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    
    // Add a border
    paint.color = Color.WHITE
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = if (isCluster) 6f else 4f
    canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - (paint.strokeWidth / 2f), paint)
    
    if (isCluster) {
        // Draw a shadow/outer ring for clusters to make them pop
        paint.color = color
        paint.alpha = 100
        paint.strokeWidth = 4f
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 1f, paint)
    }
    
    return bitmap
}
