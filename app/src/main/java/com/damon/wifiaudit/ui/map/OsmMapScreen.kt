package com.damon.wifiaudit.ui.map

import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.damon.wifiaudit.data.entity.RssiHeatmapPoint
import com.damon.wifiaudit.map.*
import kotlinx.coroutines.flow.StateFlow
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline

@Composable
fun OsmMapScreen(
    points: StateFlow<List<RssiHeatmapPoint>>,
    playbackIndex: StateFlow<Int?>,
    showWifi: StateFlow<Boolean>,
    showBle: StateFlow<Boolean>,
    focusTarget: ScanLocationTarget? = null,
    onPointSelected: (RssiHeatmapPoint) -> Unit,
    onBack: () -> Unit,
    viewModel: com.damon.wifiaudit.map.MapViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val allPoints by points.collectAsState()
    val playback by playbackIndex.collectAsState()
    val wifiVisible by showWifi.collectAsState()
    val bleVisible by showBle.collectAsState()

    var mapView by remember { mutableStateOf<MapView?>(null) }
    val isFocusedScan = focusTarget != null

    // Init osmdroid once
    LaunchedEffect(Unit) {
        OsmConfig.initialize(context)
    }

    // Centre and highlight an individual history observation independently of
    // any wider device track drawn by the map data source.
    LaunchedEffect(mapView, focusTarget) {
        val map = mapView ?: return@LaunchedEffect
        map.overlays.removeAll(map.overlays.filterIsInstance<FocusedScanOverlay>())
        focusTarget?.let { target ->
            map.overlays.add(FocusedScanOverlay(target))
            map.controller.animateTo(GeoPoint(target.latitude, target.longitude), 19.0, 350L)
            map.invalidate()
        }
    }

    // Lifecycle handling for MapView
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // OSM Map
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(CartoDbDarkMatter) // Dark theme tiles
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true
                    minZoomLevel = 3.0
                    maxZoomLevel = 21.0

                    // Default zoom (will be overridden when points arrive)
                    controller.setZoom(19.0)

                    mapView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                updateOverlays(map, allPoints, playback, wifiVisible, bleVisible, onPointSelected)
            }
        )

        // Back Button
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = Color(0xFF1A1A23).copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 56.dp, start = 16.dp)
                .size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }

        // Top Timeline Header
        MapHeader(
            pointCount = allPoints.size,
            playback = playback,
            onScrub = { viewModel.setPlaybackIndex(it) },
            onPlayPause = { viewModel.togglePlayback() },
            isPlaying = viewModel.isPlaying.collectAsState().value,
            focusedScanLabel = if (isFocusedScan) "Selected scan location" else null,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Right-side Controls
        MapControls(
            onRecenter = {
                mapView?.let { mv ->
                    val target = focusTarget
                    if (target != null) {
                        mv.controller.animateTo(GeoPoint(target.latitude, target.longitude), 19.0, 350L)
                    } else {
                        allPoints.averageValidGeoPoint()?.let { centre ->
                            mv.controller.animateTo(centre, 19.0, 500L)
                        }
                    }
                }
            },
            showWifi = wifiVisible,
            showBle = bleVisible,
            showGrid = false,
            onToggleWifi = { viewModel.toggleWifi() },
            onToggleBle = { viewModel.toggleBle() },
            onToggleGrid = { },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 100.dp, end = 16.dp)
        )

        focusTarget?.let { target ->
            Surface(
                color = Color(0xFF1A1A23).copy(alpha = 0.94f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp, start = 16.dp, end = 16.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("Selected scan location", color = Color(0xFF00E5FF), style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${target.displayName}  •  ${target.rssi} dBm",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // Bottom-right Legend
        SignalLegend(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 80.dp, end = 16.dp)
        )

        // Empty state
        if (allPoints.isEmpty() && !isFocusedScan) {
            Surface(
                color = Color(0xFF0F0F15).copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    "No heatmap data.\nEnable Range Heatmap in device details.",
                    color = Color.White,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    }
}

// --- Dark Matter Tile Source ---
private val CartoDbDarkMatter = XYTileSource(
    "CartoDB_DarkMatter",
    0, 20, 256, ".png",
    arrayOf("https://a.basemaps.cartocdn.com/dark_all/", 
            "https://b.basemaps.cartocdn.com/dark_all/",
            "https://c.basemaps.cartocdn.com/dark_all/",
            "https://d.basemaps.cartocdn.com/dark_all/")
)

// --- Overlay Update Logic ---
private fun updateOverlays(
    map: MapView,
    allPoints: List<RssiHeatmapPoint>,
    playback: Int?,
    wifiVisible: Boolean,
    bleVisible: Boolean,
    onPointSelected: (RssiHeatmapPoint) -> Unit
) {
    map.overlays.removeAll(map.overlays.filterIsInstance<RssiOverlay>())
    map.overlays.removeAll(map.overlays.filterIsInstance<Polyline>())

    val visible = playback?.let { idx -> allPoints.take(idx + 1) } ?: allPoints
    val valid = visible.asSequence()
        .filter { it.latitude != null && it.longitude != null }
        .toList()

    if (valid.isEmpty()) {
        map.invalidate()
        return
    }

    // Center map on data if first load
    if (map.mapCenter.latitude == 0.0) {
        valid.averageValidGeoPoint()?.let(map.controller::setCenter)
    }

    // Group by MAC to draw lines and dots
    val byMac = valid.groupBy { it.macAddress }

    byMac.forEach { (mac, pts) ->
        val isActuallyWifi = mac.startsWith("WIFI_") 
        
        if (isActuallyWifi && !wifiVisible) return@forEach
        if (!isActuallyWifi && !bleVisible) return@forEach

        // Draw path line
        if (pts.size > 1) {
            val line = Polyline(map).apply {
                outlinePaint.color = if (isActuallyWifi) Color(0xFF00BCD4).toArgb() else Color(0xFFE040FB).toArgb()
                outlinePaint.strokeWidth = 3f
                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
                setPoints(pts.map { GeoPoint(it.latitude!!, it.longitude!!) })
            }
            map.overlays.add(line)
        }

        // Draw RSSI dots
        pts.forEach { pt ->
            val overlay = RssiOverlay(pt, map, onPointSelected)
            map.overlays.add(overlay)
        }
    }

    map.invalidate()
}

/** Calculates a valid map centre in one pass without allocating latitude/longitude lists. */
private fun List<RssiHeatmapPoint>.averageValidGeoPoint(): GeoPoint? {
    var latitudeTotal = 0.0
    var longitudeTotal = 0.0
    var count = 0
    for (point in this) {
        val latitude = point.latitude ?: continue
        val longitude = point.longitude ?: continue
        latitudeTotal += latitude
        longitudeTotal += longitude
        count++
    }
    return if (count == 0) null else GeoPoint(latitudeTotal / count, longitudeTotal / count)
}

/** Draws a persistent ring around the exact history item chosen by the user. */
private class FocusedScanOverlay(
    private val target: ScanLocationTarget,
) : Overlay() {
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(70, 255, 235, 59)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(255, 235, 59)
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (target.type == ScanLocationTarget.RadioType.WIFI) {
            android.graphics.Color.rgb(0, 188, 212)
        } else {
            android.graphics.Color.rgb(224, 64, 251)
        }
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val point = mapView.projection.toPixels(GeoPoint(target.latitude, target.longitude), null) ?: return
        val x = point.x.toFloat()
        val y = point.y.toFloat()
        canvas.drawCircle(x, y, 34f, glowPaint)
        canvas.drawCircle(x, y, 23f, ringPaint)
        canvas.drawCircle(x, y, 9f, corePaint)
    }
}

// --- Custom RSSI Dot Overlay ---
private class RssiOverlay(
    private val point: RssiHeatmapPoint,
    private val mapView: MapView,
    private val onTap: (RssiHeatmapPoint) -> Unit
) : Overlay() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow) return
        val geo = GeoPoint(point.latitude!!, point.longitude!!)
        val proj = osmv.projection
        val screen = proj.toPixels(geo, null) ?: return

        val baseColor = when {
            point.rssi >= -50 -> Color(0xFF00E676)
            point.rssi >= -70 -> Color(0xFFFFEA00)
            else -> Color(0xFFFF3D00)
        }

        // Glow
        paint.color = baseColor.copy(alpha = 0.25f).toArgb()
        c.drawCircle(screen.x.toFloat(), screen.y.toFloat(), 24f, paint)

        // Core
        paint.color = baseColor.copy(alpha = 0.9f).toArgb()
        c.drawCircle(screen.x.toFloat(), screen.y.toFloat(), 8f, paint)

        // White center
        paint.color = Color.White.copy(alpha = 0.8f).toArgb()
        c.drawCircle(screen.x.toFloat(), screen.y.toFloat(), 3f, paint)

        // Selection ring
        strokePaint.color = baseColor.toArgb()
        c.drawCircle(screen.x.toFloat(), screen.y.toFloat(), 12f, strokePaint)
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val geo = GeoPoint(point.latitude!!, point.longitude!!)
        val proj = mapView.projection
        val screen = proj.toPixels(geo, null) ?: return false
        val dx = e.x - screen.x
        val dy = e.y - screen.y
        if (dx * dx + dy * dy < 900) { // 30px hit radius
            onTap(point)
            return true
        }
        return false
    }
}
