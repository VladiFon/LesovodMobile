package com.lesovod.mobile.ui.map

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Point
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Полупрозрачный круг точности вокруг синей точки "моё местоположение" — радиус из
 * Location.accuracy (стандартная практика в картах, ничего заново не изобретаем).
 */
class MyLocationAccuracyOverlay(private val source: MyLocationNewOverlay) : Overlay() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(40, 33, 150, 243)
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(120, 33, 150, 243)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val point = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        if (!source.isMyLocationEnabled) return
        val location = source.myLocation ?: return
        val accuracy = source.lastFix?.accuracy ?: return
        if (accuracy <= 0f) return
        projection.toPixels(location, point)
        val radiusPx = projection.metersToEquatorPixels(accuracy)
        if (radiusPx <= 0f) return
        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radiusPx, fillPaint)
        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radiusPx, strokePaint)
    }
}
