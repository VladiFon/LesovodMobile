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

/**
 * Метки рабочего (POST/GET geo-notes) — кружки цвета их типа с буквой типа внутри
 * (ветровал — "В", пожар — "П", …), поверх слоёв кварталов/выделов/делянок.
 */
class GeoNotesOverlay(density: Float) : Overlay() {
    var notes: List<GeoNoteMarker> = emptyList()
    var onNoteTap: (GeoNoteMarker) -> Unit = {}

    private val radiusPx = 11f * density
    private val touchSlopPx = radiusPx * 2f
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 11f * density
    }
    private val point = Point()
    private val geo = GeoPoint(0.0, 0.0)

    override fun draw(canvas: Canvas, projection: Projection) {
        for (note in notes) {
            geo.setCoords(note.lat, note.lon)
            projection.toPixels(geo, point)
            val x = point.x.toFloat()
            val y = point.y.toFloat()
            val category = note.category
            fillPaint.color = category.color
            canvas.drawCircle(x, y, radiusPx, fillPaint)
            canvas.drawCircle(x, y, radiusPx, strokePaint)
            canvas.save()
            canvas.rotate(-projection.orientation, x, y)
            canvas.drawText(category.glyph, x, y + glyphPaint.textSize / 3, glyphPaint)
            canvas.restore()
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
