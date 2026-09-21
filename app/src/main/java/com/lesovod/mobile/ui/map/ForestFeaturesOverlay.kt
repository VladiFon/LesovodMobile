package com.lesovod.mobile.ui.map

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.view.MotionEvent
import com.lesovod.mobile.data.local.CompletedWorkStore
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow

/** Цвет делянки, где работа отмечена выполненной (см. legendItems в MapScreen). */
val DONE_COLOR: Int = AndroidColor.parseColor("#2196F3")
private val HIGHLIGHT_COLOR = AndroidColor.parseColor("#DF964E")

private const val SELECTED_FILL_ALPHA = 130
private const val MIN_VISIBLE_PX = 2.0
private const val MIN_POINT_STEP_PX = 2

/**
 * Один слой вместо тысяч Polygon/Marker: рисует только фигуры, попавшие в экран и крупнее пары
 * пикселей, пропускает почти совпадающие точки контура, подписи рисует прямо на канве.
 * Выбранный объект и "выполнено" — просто другой стиль при отрисовке, слой не пересобирается.
 * Тап определяем сами (точка внутри контура), без обхода тысяч объектов osmdroid.
 */
class ForestFeaturesOverlay(density: Float) : Overlay() {
    var kvartaly: List<MapShape> = emptyList()
    var vydela: List<MapShape> = emptyList()
    var lesoseki: List<MapShape> = emptyList()
    var layers: MapLayers = MapLayers()
    var selection: MapSelection? = null
    var completed: Set<String> = emptySet()
    var onShapeTap: (MapShape) -> Unit = {}

    /** Приблизительные прямоугольники вместо настоящих контуров делянок не выбираем — тап уходит в выдел под ними. */
    var lesosekiTappable: Boolean = true

    private val path = Path()
    private val point = Point()
    private val geo = GeoPoint(0.0, 0.0)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 12f * density
    }
    private val labelHaloPaint = Paint(labelPaint).apply {
        color = AndroidColor.argb(220, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        val box = projection.boundingBox
        val marginLat = (box.latNorth - box.latSouth) * 0.15
        val marginLon = (box.lonEast - box.lonWest) * 0.15
        val south = box.latSouth - marginLat
        val north = box.latNorth + marginLat
        val west = box.lonWest - marginLon
        val east = box.lonEast + marginLon
        val zoom = projection.zoomLevel
        val pxPerDegree = 256.0 * 2.0.pow(zoom) / 360.0

        val view = View(canvas, projection, south, north, west, east, pxPerDegree, zoom)
        if (layers.kvartaly) kvartaly.forEach { drawShape(it, view) }
        if (layers.vydela) vydela.forEach { drawShape(it, view) }
        if (layers.lesoseki) lesoseki.forEach { drawShape(it, view) }
    }

    private class View(
        val canvas: Canvas,
        val projection: Projection,
        val south: Double,
        val north: Double,
        val west: Double,
        val east: Double,
        val pxPerDegree: Double,
        val zoom: Double,
    )

    private fun drawShape(shape: MapShape, v: View) {
        if (shape.maxLat < v.south || shape.minLat > v.north || shape.maxLon < v.west || shape.minLon > v.east) return

        val widthPx = (shape.maxLon - shape.minLon) * v.pxPerDegree
        val heightPx = (shape.maxLat - shape.minLat) * v.pxPerDegree / cos(Math.toRadians(shape.labelLat))
        if (widthPx < MIN_VISIBLE_PX && heightPx < MIN_VISIBLE_PX) return

        applyStyle(shape)
        buildPath(shape, v.projection)
        if (fillPaint.alpha > 0) v.canvas.drawPath(path, fillPaint)
        v.canvas.drawPath(path, strokePaint)

        val labelled = when (shape.kind) {
            ShapeKind.KVARTAL -> v.zoom >= 11.5 && minOf(widthPx, heightPx) > 60
            ShapeKind.VYDEL -> v.zoom >= 14.0 && minOf(widthPx, heightPx) > 34
            ShapeKind.LESOSEKA -> false
        }
        if (labelled) drawLabel(shape, v)
    }

    private fun applyStyle(shape: MapShape) {
        val sel = selection
        // выбранным считается только объект того же типа: делянка и выдел с одним номером — разные объекты
        val selected = sel != null && sel.kind == shape.kind && sel.kvartal == shape.kvartal && sel.vydel == shape.vydel
        val done = shape.vydel != null && CompletedWorkStore.key(shape.kvartal, shape.vydel) in completed
        fillPaint.color = AndroidColor.TRANSPARENT
        strokePaint.color = AndroidColor.WHITE

        when (shape.kind) {
            ShapeKind.KVARTAL -> {
                strokePaint.strokeWidth = if (selected) 7f else 4f
                if (selected) {
                    strokePaint.color = HIGHLIGHT_COLOR
                    fillPaint.color = withAlpha(HIGHLIGHT_COLOR, SELECTED_FILL_ALPHA)
                }
            }
            ShapeKind.VYDEL -> {
                val base = if (done) DONE_COLOR else shape.statusColor
                if (base != null) {
                    fillPaint.color = withAlpha(base, 110)
                    strokePaint.color = base
                    strokePaint.strokeWidth = 2f
                } else {
                    strokePaint.color = AndroidColor.argb(200, 255, 255, 255)
                    strokePaint.strokeWidth = 1.5f
                }
                if (selected) {
                    // выбранный участок заливаем поверх цвета работ — сразу видно, где человек обходит границы
                    fillPaint.color = withAlpha(HIGHLIGHT_COLOR, SELECTED_FILL_ALPHA)
                    strokePaint.color = HIGHLIGHT_COLOR
                    strokePaint.strokeWidth = 7f
                }
            }
            ShapeKind.LESOSEKA -> {
                strokePaint.strokeWidth = if (selected) 8f else 5f
                strokePaint.color = if (done && !selected) DONE_COLOR else HIGHLIGHT_COLOR
                fillPaint.color = when {
                    selected -> withAlpha(HIGHLIGHT_COLOR, SELECTED_FILL_ALPHA)
                    done -> withAlpha(DONE_COLOR, 140)
                    else -> AndroidColor.TRANSPARENT
                }
            }
        }
    }

    private fun withAlpha(color: Int, alpha: Int) =
        AndroidColor.argb(alpha, AndroidColor.red(color), AndroidColor.green(color), AndroidColor.blue(color))

    private fun buildPath(shape: MapShape, projection: Projection) {
        path.rewind()
        for (ring in shape.rings) {
            var lastX = 0
            var lastY = 0
            var i = 0
            while (i + 1 < ring.size) {
                geo.setCoords(ring[i], ring[i + 1])
                projection.toPixels(geo, point)
                val last = i + 3 >= ring.size
                if (i == 0) {
                    path.moveTo(point.x.toFloat(), point.y.toFloat())
                    lastX = point.x
                    lastY = point.y
                } else if (last || abs(point.x - lastX) + abs(point.y - lastY) >= MIN_POINT_STEP_PX) {
                    path.lineTo(point.x.toFloat(), point.y.toFloat())
                    lastX = point.x
                    lastY = point.y
                }
                i += 2
            }
            path.close()
        }
    }

    private fun drawLabel(shape: MapShape, v: View) {
        geo.setCoords(shape.labelLat, shape.labelLon)
        v.projection.toPixels(geo, point)
        val x = point.x.toFloat()
        val y = point.y + labelPaint.textSize / 3
        // подпись остаётся читаемой, даже когда карта повёрнута
        v.canvas.save()
        v.canvas.rotate(-v.projection.orientation, point.x.toFloat(), point.y.toFloat())
        v.canvas.drawText(shape.label, x, y, labelHaloPaint)
        v.canvas.drawText(shape.label, x, y, labelPaint)
        v.canvas.restore()
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val p = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt())
        val lat = p.latitude
        val lon = p.longitude

        // сверху вниз: делянки → выделы → кварталы
        if (layers.lesoseki && lesosekiTappable) lesoseki.lastOrNull { it.contains(lat, lon) }?.let { shape ->
            onShapeTap(shape)
            return true
        }
        if (layers.vydela) vydela.lastOrNull { it.contains(lat, lon) }?.let { shape ->
            onShapeTap(shape)
            return true
        }
        if (layers.kvartaly) kvartaly.lastOrNull { it.contains(lat, lon) }?.let { shape ->
            onShapeTap(shape)
            return true
        }
        return false
    }

    private fun MapShape.contains(lat: Double, lon: Double): Boolean {
        if (lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) return false
        return rings.any { pointInRing(it, lat, lon) }
    }

    private fun pointInRing(ring: DoubleArray, lat: Double, lon: Double): Boolean {
        var inside = false
        val n = ring.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val latI = ring[i * 2]
            val lonI = ring[i * 2 + 1]
            val latJ = ring[j * 2]
            val lonJ = ring[j * 2 + 1]
            if ((latI > lat) != (latJ > lat) && lon < (lonJ - lonI) * (lat - latI) / (latJ - latI) + lonI) inside = !inside
            j = i
        }
        return inside
    }
}
