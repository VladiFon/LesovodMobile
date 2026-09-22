package com.lesovod.mobile.ui.map

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

private val NOTE_COLOR = AndroidColor.parseColor("#DF964E")

/** Метки рабочих (POST/GET geo-notes) — маленькие кружки поверх слоёв кварталов/выделов/делянок. */
class GeoNotesOverlay(density: Float) : Overlay() {
    var notes: List<GeoNoteMarker> = emptyList()
    var onNoteTap: (GeoNoteMarker) -> Unit = {}

    private val radiusPx = 8f * density
    private val touchSlopPx = radiusPx * 2.5f
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NOTE_COLOR; style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val point = Point()
    private val geo = GeoPoint(0.0, 0.0)

    override fun draw(canvas: Canvas, projection: Projection) {
        for (note in notes) {
            geo.setCoords(note.lat, note.lon)
            projection.toPixels(geo, point)
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radiusPx, fillPaint)
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radiusPx, strokePaint)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val projection = mapView.projection
        for (note in notes.asReversed()) {
            geo.setCoords(note.lat, note.lon)
            projection.toPixels(geo, point)
            val dx = e.x - point.x
            val dy = e.y - point.y
            if (dx * dx + dy * dy <= touchSlopPx * touchSlopPx) {
                onNoteTap(note)
                return true
            }
        }
        return false
    }
}
