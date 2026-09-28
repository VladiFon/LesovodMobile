package com.lesovod.mobile.ui.map

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.view.MotionEvent
import com.lesovod.mobile.data.network.dto.SkladDto
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

private val RULER_COLOR = AndroidColor.parseColor("#FFEB3B")
private val WALK_COLOR = AndroidColor.parseColor("#00E676")
private val NAV_COLOR = AndroidColor.parseColor("#FF4081")
private val SKLAD_COLOR = AndroidColor.parseColor("#1E88E5")

/**
 * Всё "рабочее" поверх леса: линейка (точки по тапу), трек обмера обходом, линия
 * "веди до делянки" от меня до цели и склады. Один слой — одна проверка тапа.
 */
class MapToolsOverlay(private val density: Float) : Overlay() {
    var rulerActive: Boolean = false
    var rulerPoints: List<LatLon> = emptyList()
    var walkPoints: List<LatLon> = emptyList()
    var myLocation: LatLon? = null
    var navTarget: LatLon? = null
    var sklady: List<SkladDto> = emptyList()
    var showSklady: Boolean = true

    var onRulerTap: (LatLon) -> Unit = {}
    var onSkladTap: (SkladDto) -> Unit = {}

    private val path = Path()
    private val point = Point()
    private val geo = GeoPoint(0.0, 0.0)

    private val rulerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = RULER_COLOR
        strokeWidth = 3f * density
    }
    private val rulerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = AndroidColor.argb(60, 255, 235, 59)
    }
    private val walkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = WALK_COLOR
        strokeWidth = 4f * density
        strokeJoin = Paint.Join.ROUND
    }
    private val navPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = NAV_COLOR
        strokeWidth = 4f * density
        pathEffect = DashPathEffect(floatArrayOf(14f * density, 8f * density), 0f)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = AndroidColor.BLACK
        strokeWidth = 1.5f * density
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 10f * density
    }
    private val skladSize = 10f * density

    override fun draw(canvas: Canvas, projection: Projection) {
        if (showSklady) sklady.forEach { drawSklad(canvas, projection, it) }

        if (walkPoints.size >= 2) {
            buildPath(walkPoints, projection, close = false)
            canvas.drawPath(path, walkPaint)
        }
        walkPoints.lastOrNull()?.let { drawDot(canvas, projection, it, WALK_COLOR, 5f) }

        if (rulerPoints.isNotEmpty()) {
            if (rulerPoints.size >= 3) {
                buildPath(rulerPoints, projection, close = true)
                canvas.drawPath(path, rulerFillPaint)
            }
            if (rulerPoints.size >= 2) {
                buildPath(rulerPoints, projection, close = false)
                canvas.drawPath(path, rulerPaint)
            }
            rulerPoints.forEach { drawDot(canvas, projection, it, RULER_COLOR, 6f) }
        }

        val me = myLocation
        val target = navTarget
        if (target != null) {
            if (me != null) {
                buildPath(listOf(me, target), projection, close = false)
                canvas.drawPath(path, navPaint)
            }
            drawDot(canvas, projection, target, NAV_COLOR, 9f)
        }
    }

    private fun drawSklad(canvas: Canvas, projection: Projection, sklad: SkladDto) {
        geo.setCoords(sklad.lat, sklad.lon)
        projection.toPixels(geo, point)
        val x = point.x.toFloat()
        val y = point.y.toFloat()
        dotPaint.color = SKLAD_COLOR
        canvas.drawRect(x - skladSize, y - skladSize, x + skladSize, y + skladSize, dotPaint)
        canvas.drawRect(x - skladSize, y - skladSize, x + skladSize, y + skladSize, dotStrokePaint)
        canvas.save()
        canvas.rotate(-projection.orientation, x, y)
        canvas.drawText("Ск", x, y + glyphPaint.textSize / 3, glyphPaint)
        canvas.restore()
    }

    private fun drawDot(canvas: Canvas, projection: Projection, p: LatLon, color: Int, radiusDp: Float) {
        geo.setCoords(p.lat, p.lon)
        projection.toPixels(geo, point)
        dotPaint.color = color
        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radiusDp * density, dotPaint)
        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radiusDp * density, dotStrokePaint)
    }

    private fun buildPath(points: List<LatLon>, projection: Projection, close: Boolean) {
        path.rewind()
        points.forEachIndexed { i, p ->
            geo.setCoords(p.lat, p.lon)
            projection.toPixels(geo, point)
            if (i == 0) path.moveTo(point.x.toFloat(), point.y.toFloat()) else path.lineTo(point.x.toFloat(), point.y.toFloat())
        }
        if (close) path.close()
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        // линейка перехватывает все тапы, пока включена: иначе каждый тап выделял бы выдел
        if (rulerActive) {
            val p = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt())
            onRulerTap(LatLon(p.latitude, p.longitude))
            return true
        }
        if (showSklady) {
            val projection = mapView.projection
            val slop = skladSize * 1.8f
            for (sklad in sklady.asReversed()) {
                geo.setCoords(sklad.lat, sklad.lon)
                projection.toPixels(geo, point)
                if (kotlin.math.abs(e.x - point.x) <= slop && kotlin.math.abs(e.y - point.y) <= slop) {
                    onSkladTap(sklad)
                    return true
                }
            }
        }
        return false
    }

    override fun onLongPress(e: MotionEvent, mapView: MapView): Boolean = rulerActive
}
