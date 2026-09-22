package com.lesovod.mobile.ui.map

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Point
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

private val LOCATION_BLUE = AndroidColor.parseColor("#2196F3")

/**
 * Рисует "моё местоположение" сама, вместо штатных иконок человечка/стрелки osmdroid
 * (их рисование отключено в ForestMapView через прозрачные setPersonIcon/setDirectionArrowIcon):
 * полупрозрачный круг точности (радиус из Location.accuracy), синяя точка и луч направления
 * движения (Location.bearing), когда он известен.
 */
class MyLocationIndicatorOverlay(private val source: MyLocationNewOverlay, density: Float) : Overlay() {
    private val accuracyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(40, 33, 150, 243)
        style = Paint.Style.FILL
    }
    private val accuracyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(120, 33, 150, 243)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val dotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LOCATION_BLUE
        style = Paint.Style.FILL
    }
    private val dotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LOCATION_BLUE
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val dotRadiusPx = 7f * density
    private val rayLengthPx = 28f * density
    private val point = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        if (!source.isMyLocationEnabled) return
        val location = source.myLocation ?: return
        val fix = source.lastFix

        projection.toPixels(location, point)
        val x = point.x.toFloat()
        val y = point.y.toFloat()

        val accuracy = fix?.accuracy ?: 0f
        if (accuracy > 0f) {
            val radiusPx = projection.metersToEquatorPixels(accuracy)
            if (radiusPx > 0f) {
                canvas.drawCircle(x, y, radiusPx, accuracyFillPaint)
                canvas.drawCircle(x, y, radiusPx, accuracyStrokePaint)
            }
        }

        // Луч в сторону движения — угол считаем от текущего поворота карты (см. подпись в
        // ForestFeaturesOverlay.drawLabel: та же формула bearing - projection.orientation).
        if (fix != null && fix.hasBearing()) {
            canvas.save()
            canvas.rotate(fix.bearing - projection.orientation, x, y)
            canvas.drawLine(x, y, x, y - rayLengthPx, rayPaint)
            canvas.restore()
        }

        canvas.drawCircle(x, y, dotRadiusPx, dotFillPaint)
        canvas.drawCircle(x, y, dotRadiusPx, dotStrokePaint)
    }
}
