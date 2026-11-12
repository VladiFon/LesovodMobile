package com.lesovod.mobile.ui.map

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

private val DRAFT_COLOR = AndroidColor.parseColor("#FF6D00")

/**
 * Лесные дороги: у каждого типа свой цвет (зимник и волок пунктиром), под линией тёмная
 * «обводка», чтобы читалось на спутнике. Плюс черновик новой дороги, пока мастер её рисует.
 */
class RoadsOverlay(private val density: Float) : Overlay() {
    var roads: List<Road> = emptyList()
    var selectedId: Int? = null
    var draft: List<LatLon> = emptyList()
    /** След смены — голубые «хлебные крошки». */
    var trail: List<LatLon> = emptyList()
    /** Пока рисуем дорогу, тапы по дорогам не перехватываем — их ловит инструмент. */
    var tapEnabled: Boolean = true
    var onRoadTap: (Road) -> Unit = {}

    private val path = Path()
    private val point = Point()
    private val geo = GeoPoint(0.0, 0.0)

    private val casing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = AndroidColor.argb(170, 20, 20, 20)
        strokeWidth = 6.5f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val dash = DashPathEffect(floatArrayOf(10f * density, 6f * density), 0f)
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = AndroidColor.argb(110, 255, 255, 255)
        strokeWidth = 12f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val draftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = DRAFT_COLOR
        strokeWidth = 4f * density
        strokeJoin = Paint.Join.ROUND
        pathEffect = DashPathEffect(floatArrayOf(12f * density, 6f * density), 0f)
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = AndroidColor.parseColor("#00B8D4")
        strokeWidth = 3f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(2f * density, 6f * density), 0f)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = AndroidColor.BLACK
        strokeWidth = 1.5f * density
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        val zoom = projection.zoomLevel
        val thin = zoom < 12.5
        for (road in roads) {
            if (road.points.size < 2) continue
            buildPath(road.points, projection)
            if (road.id == selectedId) canvas.drawPath(path, selectedPaint)
            if (!thin) canvas.drawPath(path, casing)
            line.color = road.tip.color
            line.pathEffect = if (road.tip.dashed) dash else null
            line.strokeWidth = (if (thin) 2.5f else 3.5f) * density
            canvas.drawPath(path, line)
        }
        if (trail.size >= 2) {
            buildPath(trail, projection)
            canvas.drawPath(path, trailPaint)
        }
        trail.firstOrNull()?.let { p ->
            geo.setCoords(p.lat, p.lon)
            projection.toPixels(geo, point)
            dotPaint.color = AndroidColor.parseColor("#00B8D4")
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 6f * density, dotPaint)
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 6f * density, dotStroke)
        }
        if (draft.size >= 2) {
            buildPath(draft, projection)
            canvas.drawPath(path, draftPaint)
        }
        draft.forEachIndexed { i, p ->
            geo.setCoords(p.lat, p.lon)
            projection.toPixels(geo, point)
            dotPaint.color = if (i == draft.lastIndex) DRAFT_COLOR else AndroidColor.WHITE
            val r = (if (i == draft.lastIndex) 7f else 4.5f) * density
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), r, dotPaint)
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), r, dotStroke)
        }
    }

    private fun buildPath(points: List<LatLon>, projection: Projection) {
        path.rewind()
        points.forEachIndexed { i, p ->
            geo.setCoords(p.lat, p.lon)
            projection.toPixels(geo, point)
            if (i == 0) path.moveTo(point.x.toFloat(), point.y.toFloat()) else path.lineTo(point.x.toFloat(), point.y.toFloat())
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        if (!tapEnabled || roads.isEmpty()) return false
        val projection = mapView.projection
        val slop = 14f * density
        var best: Road? = null
        var bestD = slop
        val a = Point()
        val b = Point()
        for (road in roads) {
            for (i in 0 until road.points.size - 1) {
                geo.setCoords(road.points[i].lat, road.points[i].lon)
                projection.toPixels(geo, a)
                geo.setCoords(road.points[i + 1].lat, road.points[i + 1].lon)
                projection.toPixels(geo, b)
                val d = segmentDistance(e.x, e.y, a, b)
                if (d < bestD) { bestD = d; best = road }
            }
        }
        best?.let { onRoadTap(it); return true }
        return false
    }

    private fun segmentDistance(x: Float, y: Float, a: Point, b: Point): Float {
        val dx = (b.x - a.x).toFloat()
        val dy = (b.y - a.y).toFloat()
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((x - a.x) * dx + (y - a.y) * dy) / len2).coerceIn(0f, 1f)
        return hypot(x - (a.x + dx * t), y - (a.y + dy * t))
    }
}
