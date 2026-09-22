package com.lesovod.mobile.ui.map

import android.Manifest
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lesovod.mobile.data.local.CompletedWorkStore
import com.lesovod.mobile.data.location.hasLocationPermission
import com.lesovod.mobile.ui.theme.ForestError
import com.lesovod.mobile.ui.theme.ForestOutline
import com.lesovod.mobile.ui.theme.ForestPrimary
import com.lesovod.mobile.ui.theme.ForestSurface
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Выбранный объект — подсвечивается на карте, пока открыта карточка. */
data class MapSelection(val kvartal: String, val vydel: String?, val kind: ShapeKind)

private enum class LocateMode { Off, Follow, Heading }

private data class MapHud(val bearing: Float = 0f, val zoom: Double = 13.0, val latitude: Double = 54.5)

private const val VIEWPORT_DEBOUNCE_MS = 600L
private const val HUD_POLL_MS = 250L

@Composable
fun ForestMapView(
    kvartaly: List<MapShape>,
    vydela: List<MapShape>,
    lesoseki: List<MapShape>,
    layers: MapLayers,
    selection: MapSelection?,
    completed: Set<String>,
    fitToken: Int,
    lesosekiTappable: Boolean,
    geoNotes: List<GeoNoteMarker>,
    onShapeTap: (MapShape) -> Unit,
    onEmptyTap: () -> Unit,
    onGeoNoteTap: (GeoNoteMarker) -> Unit,
    onMapLongPress: (lat: Double, lon: Double) -> Unit,
    onViewportChanged: (bbox: String, zoom: Double, latitude: Double, longitude: Double) -> Unit,
    initialCamera: MapCamera?,
    controlsTopPadding: Dp,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val mapViewState = remember { mutableStateOf<MapView?>(null) }
    val myLocationOverlayState = remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    val labelsOverlayState = remember { mutableStateOf<TilesOverlay?>(null) }
    val scaleBarState = remember { mutableStateOf<ScaleBarOverlay?>(null) }
    val featuresOverlay = remember { ForestFeaturesOverlay(density) }
    val geoNotesOverlay = remember { GeoNotesOverlay(density) }
    // вернулись на вкладку — остаёмся там, где были, а не прыгаем на всё лесничество заново
    val lastFitToken = remember { mutableStateOf(if (initialCamera != null) fitToken else 0) }
    val onViewportChangedState = remember { mutableStateOf(onViewportChanged) }
    onViewportChangedState.value = onViewportChanged
    val onEmptyTapState = remember { mutableStateOf(onEmptyTap) }
    onEmptyTapState.value = onEmptyTap
    val onMapLongPressState = remember { mutableStateOf(onMapLongPress) }
    onMapLongPressState.value = onMapLongPress
    val pendingViewportRunnable = remember { mutableStateOf<Runnable?>(null) }
    var hud by remember { mutableStateOf(MapHud()) }
    var locateMode by remember { mutableStateOf(LocateMode.Off) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    fun reportViewport(mapView: MapView) {
        pendingViewportRunnable.value?.let { mapView.removeCallbacks(it) }
        val runnable = Runnable {
            val box = mapView.boundingBox
            val bboxString = "${box.lonWest},${box.latSouth},${box.lonEast},${box.latNorth}"
            val center = mapView.mapCenter
            onViewportChangedState.value(bboxString, mapView.zoomLevelDouble, center.latitude, center.longitude)
        }
        pendingViewportRunnable.value = runnable
        mapView.postDelayed(runnable, VIEWPORT_DEBOUNCE_MS)
    }

    // Компас, масштаб и режим "следовать за мной" — опрашиваем 4 раза в секунду:
    // у osmdroid нет слушателя поворота, а лишних перерисовок Compose при равных значениях не будет.
    LaunchedEffect(mapViewState.value) {
        val mapView = mapViewState.value ?: return@LaunchedEffect
        while (true) {
            delay(HUD_POLL_MS)
            val overlay = myLocationOverlayState.value
            if (locateMode != LocateMode.Off && overlay != null) {
                if (!overlay.isFollowLocationEnabled) {
                    locateMode = LocateMode.Off // пользователь сам сдвинул карту
                } else if (locateMode == LocateMode.Heading) {
                    val fix = overlay.lastFix
                    if (fix != null && fix.hasBearing() && fix.speed > 0.5f) {
                        mapView.mapOrientation = -fix.bearing
                    }
                }
            }
            val next = MapHud(
                bearing = (mapView.mapOrientation % 360f).roundToInt().toFloat(),
                zoom = (mapView.zoomLevelDouble * 10).roundToLong() / 10.0,
                latitude = (mapView.mapCenter.latitude * 10).roundToLong() / 10.0, // масштаб от широты почти не зависит — не будим Compose на каждый сдвиг
            )
            if (next != hud) hud = next
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                Configuration.getInstance().apply {
                    userAgentValue = ctx.packageName
                    // тайлы лежат в filesDir: скачанные участки остаются на устройстве и работают без интернета
                    osmdroidBasePath = File(ctx.filesDir, "osmdroid")
                    osmdroidTileCache = File(osmdroidBasePath, "tiles")
                    tileFileSystemCacheMaxBytes = 600L * 1024 * 1024
                    tileFileSystemCacheTrimBytes = 500L * 1024 * 1024
                    tileDownloadThreads = 4
                    tileFileSystemThreads = 4
                    cacheMapTileCount = 12
                }
                MapView(ctx).apply {
                    setTileSource(EsriSatelliteTileSource)
                    setMultiTouchControls(true)
                    // штатные кнопки +/- заменены своими, компактными
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    isHorizontalMapRepetitionEnabled = false
                    isVerticalMapRepetitionEnabled = false
                    minZoomLevel = 5.0
                    maxZoomLevel = 19.0

                    overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            onEmptyTapState.value()
                            return false
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            p?.let { onMapLongPressState.value(it.latitude, it.longitude) }
                            return true
                        }
                    }))

                    val labels = TilesOverlay(MapTileProviderBasic(ctx, EsriLabelsTileSource), ctx, true, true)
                    overlays.add(labels)
                    labelsOverlayState.value = labels

                    overlays.add(RotationGestureOverlay(this).apply { isEnabled = true })

                    overlays.add(featuresOverlay)
                    overlays.add(geoNotesOverlay)

                    val scaleBar = ScaleBarOverlay(this).apply {
                        setAlignBottom(true)
                        setCentred(false)
                        setBarPaint(Paint().apply { color = AndroidColor.WHITE; strokeWidth = 3f * ctx.resources.displayMetrics.density; style = Paint.Style.STROKE; isAntiAlias = true })
                        setTextPaint(Paint().apply {
                            color = AndroidColor.WHITE
                            textSize = 11f * ctx.resources.displayMetrics.scaledDensity
                            isAntiAlias = true
                            setShadowLayer(4f, 0f, 0f, AndroidColor.BLACK)
                        })
                    }
                    overlays.add(scaleBar)
                    scaleBarState.value = scaleBar

                    val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                    if (hasLocationPermission(ctx)) myLocationOverlay.enableMyLocation()
                    overlays.add(MyLocationAccuracyOverlay(myLocationOverlay))
                    overlays.add(myLocationOverlay)
                    myLocationOverlayState.value = myLocationOverlay

                    if (initialCamera != null) {
                        controller.setZoom(initialCamera.zoom)
                        controller.setCenter(GeoPoint(initialCamera.latitude, initialCamera.longitude))
                    } else {
                        controller.setZoom(13.0)
                        controller.setCenter(GeoPoint(54.51, 30.36)) // временный центр — сменится, как только подгрузятся кварталы
                    }

                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            reportViewport(this@apply)
                            return false
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            reportViewport(this@apply)
                            return false
                        }
                    })

                    mapViewState.value = this
                }
            },
            update = { mapView ->
                if (hasLocationPermission(context)) {
                    myLocationOverlayState.value?.let { if (!it.isMyLocationEnabled) it.enableMyLocation() }
                }

                // линейка масштаба живёт над нижней карточкой и над плашкой "1:N"
                scaleBarState.value?.setScaleBarOffset((16 * density).toInt(), ((bottomInset.value + 52) * density).toInt())

                if (layers.satellite) {
                    if (mapView.tileProvider.tileSource != EsriSatelliteTileSource) mapView.setTileSource(EsriSatelliteTileSource)
                } else if (mapView.tileProvider.tileSource != TileSourceFactory.MAPNIK) {
                    mapView.setTileSource(TileSourceFactory.MAPNIK)
                }
                labelsOverlayState.value?.isEnabled = layers.satellite

                // Данные и стиль просто передаём слою — он сам перерисуется; ничего не пересоздаётся.
                var dirty = false
                if (featuresOverlay.kvartaly !== kvartaly) { featuresOverlay.kvartaly = kvartaly; dirty = true }
                if (featuresOverlay.vydela !== vydela) { featuresOverlay.vydela = vydela; dirty = true }
                if (featuresOverlay.lesoseki !== lesoseki) { featuresOverlay.lesoseki = lesoseki; dirty = true }
                if (featuresOverlay.layers != layers) { featuresOverlay.layers = layers; dirty = true }
                if (featuresOverlay.selection != selection) { featuresOverlay.selection = selection; dirty = true }
                if (featuresOverlay.completed != completed) { featuresOverlay.completed = completed; dirty = true }
                featuresOverlay.onShapeTap = onShapeTap
                featuresOverlay.lesosekiTappable = lesosekiTappable
                if (geoNotesOverlay.notes !== geoNotes) { geoNotesOverlay.notes = geoNotes; dirty = true }
                geoNotesOverlay.onNoteTap = onGeoNoteTap

                if (fitToken != lastFitToken.value && kvartaly.isNotEmpty()) {
                    lastFitToken.value = fitToken
                    val box = BoundingBox(
                        kvartaly.maxOf { it.maxLat }, kvartaly.maxOf { it.maxLon },
                        kvartaly.minOf { it.minLat }, kvartaly.minOf { it.minLon },
                    )
                    mapView.zoomToBoundingBox(box, true, 50)
                    reportViewport(mapView)
                }

                if (dirty) mapView.invalidate()
            },
        )

        // Компактная колонка кнопок справа — полупрозрачная, поверх карты, а не отдельная панель.
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = controlsTopPadding, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlassButton(
                onClick = { mapViewState.value?.let { it.mapOrientation = 0f } },
                description = "Север сверху",
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Navigation,
                        contentDescription = null,
                        tint = ForestError,
                        modifier = Modifier.size(22.dp).rotate(hud.bearing),
                    )
                }
            }
            Text(
                "С",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )

            GlassButton(
                onClick = {
                    val overlay = myLocationOverlayState.value
                    val mapView = mapViewState.value
                    if (overlay == null || mapView == null) return@GlassButton
                    if (!hasLocationPermission(context)) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        return@GlassButton
                    }
                    if (!overlay.isMyLocationEnabled) overlay.enableMyLocation()
                    when (locateMode) {
                        LocateMode.Off -> {
                            locateMode = LocateMode.Follow
                            overlay.enableFollowLocation()
                            overlay.myLocation?.let {
                                if (mapView.zoomLevelDouble < 16.0) mapView.controller.setZoom(16.0)
                                mapView.controller.animateTo(it)
                            }
                        }
                        LocateMode.Follow -> locateMode = LocateMode.Heading
                        LocateMode.Heading -> {
                            locateMode = LocateMode.Off
                            overlay.disableFollowLocation()
                            mapView.mapOrientation = 0f
                        }
                    }
                },
                description = "Моё местоположение",
                active = locateMode != LocateMode.Off,
            ) {
                Icon(
                    if (locateMode == LocateMode.Heading) Icons.Filled.NearMe else Icons.Filled.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }

            GlassButton(onClick = { mapViewState.value?.controller?.zoomIn() }, description = "Приблизить") {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            GlassButton(onClick = { mapViewState.value?.controller?.zoomOut() }, description = "Отдалить") {
                Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }

        // Всегда видимый масштаб: линейка рисуется самой картой, а здесь — числом.
        Surface(
            color = ForestPrimary.copy(alpha = 0.78f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = bottomInset + 12.dp),
        ) {
            Text(
                "1:${formatScale(scaleDenominator(hud.zoom, hud.latitude, context.resources.displayMetrics.xdpi))}  ·  z${"%.1f".format(hud.zoom)}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            myLocationOverlayState.value?.disableMyLocation()
            mapViewState.value?.onDetach()
        }
    }
}

@Composable
private fun GlassButton(
    onClick: () -> Unit,
    description: String,
    active: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) ForestPrimary.copy(alpha = 0.92f) else ForestSurface.copy(alpha = 0.88f),
        contentColor = if (active) Color.White else ForestPrimary,
        border = BorderStroke(1.dp, ForestOutline.copy(alpha = 0.7f)),
        shadowElevation = 3.dp,
        modifier = Modifier
            .size(44.dp)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { content() }
    }
}

private fun scaleDenominator(zoom: Double, latitude: Double, xdpi: Float): Long {
    val metersPerPixel = 156543.03392 * cos(Math.toRadians(latitude)) / 2.0.pow(zoom)
    return (metersPerPixel * xdpi / 0.0254).roundToLong()
}

private fun formatScale(denominator: Long): String {
    val rounded = if (denominator >= 1000) (denominator / 100.0).roundToLong() * 100 else denominator
    return "%,d".format(rounded).replace(',', ' ')
}
