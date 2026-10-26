package com.lesovod.mobile.ui.map

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.net.Uri
import android.view.MotionEvent
import com.lesovod.mobile.data.network.dto.KartaProbaDto
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Пробные площади лесных культур на карте (сервер 26.10.2026+, /api/proby).
 *
 * По правилам тех. приёмка, инвентаризация 1-го и 3-го года идут на одной и той же пробе —
 * поэтому её ставят один раз (центр по GPS, усреднённый по нескольким замерам, + граница:
 * круг, прямоугольник или обход), она хранится на сервере и видна всем.
 *
 * На карте проба не должна спорить с делянками и культурами: издалека её не видно вовсе,
 * ближе — маленькая точка, вблизи — тонкий белый пунктир и подпись «П1».
 */

/** Формы — те же коды, что на сервере (app/proby_karta.py). */
enum class ProbaForma(val code: String, val label: String) {
    KRUG("krug", "Круг"),
    PRYAMOUGOLNIK("pryamougolnik", "Прямоугольник"),
    KONTUR("kontur", "Обход"),
    /** Углы по вешкам: стоишь на каждой, GPS усредняется. На сервер уходит как контур. */
    VESHKI("veshki", "По вешкам");

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: KRUG
    }
}

/** Готовые размеры круглых проб: площадь, м² → радиус, м. */
val KRUG_PRESETS: List<Pair<Int, Double>> = listOf(10 to 1.78, 20 to 2.52, 50 to 3.99, 100 to 5.64, 200 to 7.98)

private const val EARTH_R = 6_371_008.8

private fun offset(center: LatLon, eastM: Double, northM: Double): LatLon {
    val mLat = PI * EARTH_R / 180.0
    val mLon = mLat * cos(Math.toRadians(center.lat))
    return LatLon(center.lat + northM / mLat, center.lon + eastM / mLon)
}

/** Граница круга — так же, как строит сервер (36 вершин). */
fun krugRing(center: LatLon, radiusM: Double, n: Int = 36): List<LatLon> =
    (0 until n).map { i -> offset(center, radiusM * sin(2 * PI * i / n), radiusM * cos(2 * PI * i / n)) }

/** Прямоугольник: центр — середина, длина — вдоль азимута (0 — север, по часовой). */
fun pryamougolnikRing(center: LatLon, dlinaM: Double, shirinaM: Double, azimut: Double): List<LatLon> {
    val a = Math.toRadians(azimut)
    val ux = sin(a)
    val uy = cos(a)
    val vx = uy
    val vy = -ux
    val hl = dlinaM / 2
    val hw = shirinaM / 2
    return listOf(1 to 1, 1 to -1, -1 to -1, -1 to 1).map { (sx, sy) ->
        offset(center, sx * hl * ux + sy * hw * vx, sx * hl * uy + sy * hw * vy)
    }
}

/** Проба, готовая к рисованию: кольцо границы плоским массивом [lat0, lon0, …] и рамка. */
class ProbaShape(
    val id: Int,
    val nomer: String,
    val center: LatLon,
    val ring: DoubleArray,
    val dto: KartaProbaDto?,
    /** Ещё в офлайн-очереди — на сервер не ушла. */
    val pending: Boolean = false,
) {
    val minLat: Double
    val maxLat: Double
    val minLon: Double
    val maxLon: Double

    init {
        var a = center.lat
        var b = center.lat
        var c = center.lon
        var d = center.lon
        var i = 0
        while (i + 1 < ring.size) {
            a = minOf(a, ring[i]); b = maxOf(b, ring[i]); c = minOf(c, ring[i + 1]); d = maxOf(d, ring[i + 1])
            i += 2
        }
        minLat = a; maxLat = b; minLon = c; maxLon = d
    }

    fun contains(lat: Double, lon: Double): Boolean {
        if (lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) return false
        var inside = false
        val n = ring.size / 2
        var j = n - 1
        for (k in 0 until n) {
            val la = ring[k * 2]
            val lo = ring[k * 2 + 1]
            val lb = ring[j * 2]
            val lob = ring[j * 2 + 1]
            if ((la > lat) != (lb > lat) && lon < (lob - lo) * (lat - la) / (lb - la) + lo) inside = !inside
            j = k
        }
        return inside
    }
}

fun List<LatLon>.toFlatRing(): DoubleArray = DoubleArray(size * 2).also { arr ->
    forEachIndexed { i, p -> arr[2 * i] = p.lat; arr[2 * i + 1] = p.lon }
}

fun KartaProbaDto.toShape(): ProbaShape {
    val center = LatLon(lat, lon)
    val ring = geometry?.let { g -> runCatching { g.toOuterRings().firstOrNull() }.getOrNull() }
        ?.map { LatLon(it.latitude, it.longitude) }
        ?.takeIf { it.size >= 3 }
        ?: when (ProbaForma.fromCode(forma)) {
            ProbaForma.PRYAMOUGOLNIK -> pryamougolnikRing(center, dlinaM ?: 10.0, shirinaM ?: 2.0, azimut ?: 0.0)
            else -> krugRing(center, radiusM ?: 5.64)
        }
    return ProbaShape(id, nomer.orEmpty(), center, ring.toFlatRing(), this)
}

/** «П3 · круг R 5,64 м · 100 м²» — короткое описание размера. */
fun KartaProbaDto.razmerText(): String {
    val forma = ProbaForma.fromCode(forma)
    val size = when (forma) {
        ProbaForma.KRUG -> radiusM?.let { "круг R ${fmt(it)} м" } ?: "круг"
        ProbaForma.PRYAMOUGOLNIK -> if (dlinaM != null && shirinaM != null) "${fmt(dlinaM)}×${fmt(shirinaM)} м" else "прямоугольник"
        ProbaForma.KONTUR, ProbaForma.VESHKI -> "граница по GPS"
    }
    return listOfNotNull(size, ploshadM2?.let { "${fmt(it)} м²" }).joinToString(" · ")
}

private fun fmt(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString() else String.format(java.util.Locale("ru"), "%.2f", v).trimEnd('0').trimEnd(',')

/**
 * Черновик новой пробы. Центр — усреднение GPS-точек (fixes штук, точность ±accuracy),
 * пока averaging; тап по карте ставит центр вручную. Для обхода — точки контура из
 * «Обмера обходом».
 */
data class ProbaDraft(
    val forma: ProbaForma = ProbaForma.KRUG,
    val radius: String = "5.64",
    val dlina: String = "10",
    val shirina: String = "2",
    val azimut: String = "0",
    val center: LatLon? = null,
    val averaging: Boolean = true,
    val fixes: Int = 0,
    val accuracy: Float? = null,
    val manualCenter: Boolean = false,
    val kontur: List<LatLon> = emptyList(),
    /** Отмеченные вешки (углы) и точность каждой; center — вешка, у которой стоим сейчас. */
    val veshki: List<LatLon> = emptyList(),
    val veshkiTochnost: List<Float?> = emptyList(),
    val uchastokId: Int? = null,
    /** Участок выбран сам (по месту), а не человеком. */
    val uchastokAuto: Boolean = true,
    val nomer: String = "",
    val orientir: String = "",
    val photoUri: Uri? = null,
    val saving: Boolean = false,
    val error: String? = null,
) {
    fun number(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

    /** Граница для предпросмотра на карте; null — размеры ещё не заданы. */
    fun previewRing(): List<LatLon>? {
        if (forma == ProbaForma.KONTUR) return kontur.takeIf { it.size >= 3 }
        if (forma == ProbaForma.VESHKI) {
            // вместе с вешкой, у которой стоим: видно, как ляжет граница
            val tek = center?.takeIf { veshki.size < VESHEK }
            return uporyadochitUgly(veshki + listOfNotNull(tek)).takeIf { it.size >= 2 }
        }
        val c = center ?: return null
        return when (forma) {
            ProbaForma.KRUG -> number(radius)?.takeIf { it in 0.3..200.0 }?.let { krugRing(c, it) }
            else -> {
                val d = number(dlina)?.takeIf { it in 0.3..500.0 } ?: return null
                val w = number(shirina)?.takeIf { it in 0.3..500.0 } ?: return null
                pryamougolnikRing(c, d, w, number(azimut) ?: 0.0)
            }
        }
    }

    /** Площадь, м²: круг/прямоугольник — точно по размерам, обход — по контуру. */
    fun areaM2(): Double? = when (forma) {
        ProbaForma.KRUG -> number(radius)?.let { PI * it * it }
        ProbaForma.PRYAMOUGOLNIK -> number(dlina)?.let { d -> number(shirina)?.let { d * it } }
        ProbaForma.KONTUR -> polygonAreaSquareMeters(kontur).takeIf { kontur.size >= 3 }
        ProbaForma.VESHKI -> polygonAreaSquareMeters(uporyadochitUgly(veshki)).takeIf { veshki.size >= 3 }
    }

    /** Середина пробы по вешкам — для номера участка и метки «П1». */
    fun veshkiCenter(): LatLon? = veshki.takeIf { it.isNotEmpty() }
        ?.let { v -> LatLon(v.sumOf { it.lat } / v.size, v.sumOf { it.lon } / v.size) }

    companion object {
        /** Обычно у пробы 4 вешки; больше — можно, меньше 3 — нельзя. */
        const val VESHEK = 4
    }
}

/**
 * Углы по кругу вокруг середины: обошёл вешки «крест-накрест» — граница
 * всё равно не перекрутится восьмёркой.
 */
fun uporyadochitUgly(points: List<LatLon>): List<LatLon> {
    if (points.size < 3) return points
    val cLat = points.sumOf { it.lat } / points.size
    val cLon = points.sumOf { it.lon } / points.size
    val k = kotlin.math.cos(Math.toRadians(cLat))
    return points.sortedBy { kotlin.math.atan2(it.lat - cLat, (it.lon - cLon) * k) }
}

/**
 * Усреднение GPS для центра пробы: точки весом 1/точность², плохие (хуже 25 м) не берём,
 * если есть хорошие. Набрав 60 точек, перестаём — дальше точнее не станет.
 */
class GpsAverager {
    private var sumLat = 0.0
    private var sumLon = 0.0
    private var sumW = 0.0
    var count = 0
        private set
    private var hasGood = false

    fun reset() {
        sumLat = 0.0; sumLon = 0.0; sumW = 0.0; count = 0; hasGood = false
    }

    val full: Boolean get() = count >= MAX_FIXES

    fun add(lat: Double, lon: Double, accuracy: Float?): Boolean {
        if (full) return false
        val acc = (accuracy ?: 15f).coerceAtLeast(1f)
        val good = acc <= BAD_ACCURACY_M
        if (!good && hasGood) return false
        if (good && !hasGood && count > 0) reset() // пришёл нормальный GPS — грубые точки выкидываем
        val w = 1.0 / (acc * acc)
        sumLat += lat * w; sumLon += lon * w; sumW += w
        count += 1
        if (good) hasGood = true
        return true
    }

    val center: LatLon? get() = if (sumW > 0) LatLon(sumLat / sumW, sumLon / sumW) else null

    /** Оценка точности среднего: 1/√Σ(1/σ²) — с каждой точкой лучше, но не лучше 1 м. */
    val accuracy: Float? get() = if (sumW > 0) maxOf(1.0, sqrt(1.0 / sumW)).toFloat() else null

    companion object {
        const val MAX_FIXES = 60
        const val BAD_ACCURACY_M = 25f
    }
}

/**
 * Слой проб. Ненавязчиво: до z13.5 — ничего, до z16 — маленькая точка, ближе — тонкий белый
 * пунктир с едва заметной заливкой, с z17.5 — подпись «П1». Выбранная проба и черновик —
 * оранжевым, ждущая отправки — серым.
 */
class ProbyOverlay(density: Float) : Overlay() {
    var proby: List<ProbaShape> = emptyList()
    var selectedId: Int? = null
    var draftRing: List<LatLon>? = null
    var draftCenter: LatLon? = null
    var onProbaTap: (ProbaShape) -> Unit = {}

    private val path = Path()
    private val point = Point()
    private val geo = GeoPoint(0.0, 0.0)
    private val dotRadius = 3f * density
    private val touchPx = 16f * density

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
        pathEffect = DashPathEffect(floatArrayOf(5f * density, 4f * density), 0f)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = AndroidColor.argb(170, 0, 0, 0)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(235, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        textSize = 10f * density
        isFakeBoldText = true
    }
    private val labelHalo = Paint(labelPaint).apply {
        color = AndroidColor.argb(170, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        val zoom = projection.zoomLevel
        val box = projection.boundingBox
        if (zoom >= MIN_ZOOM) {
            for (p in proby) {
                if (p.maxLat < box.latSouth || p.minLat > box.latNorth || p.maxLon < box.lonWest || p.minLon > box.lonEast) continue
                val selected = p.id == selectedId
                val base = when {
                    selected -> SELECTED_COLOR
                    p.pending -> PENDING_COLOR
                    else -> AndroidColor.WHITE
                }
                if (zoom >= OUTLINE_ZOOM || selected) {
                    ringPath(p.ring, projection)
                    fillPaint.color = withAlpha(base, if (selected) 60 else 22)
                    linePaint.color = withAlpha(base, if (selected) 255 else 200)
                    canvas.drawPath(path, fillPaint)
                    canvas.drawPath(path, linePaint)
                }
                geo.setCoords(p.center.lat, p.center.lon)
                projection.toPixels(geo, point)
                dotPaint.color = withAlpha(base, 220)
                canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), dotRadius, dotPaint)
                canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), dotRadius, dotStroke)
                if (zoom >= LABEL_ZOOM && p.nomer.isNotEmpty()) {
                    val y = point.y - dotRadius - 4f
                    canvas.save()
                    canvas.rotate(-projection.orientation, point.x.toFloat(), point.y.toFloat())
                    canvas.drawText("П${p.nomer}", point.x.toFloat(), y, labelHalo)
                    canvas.drawText("П${p.nomer}", point.x.toFloat(), y, labelPaint)
                    canvas.restore()
                }
            }
        }
        // черновик — всегда, на любом масштабе: человек сейчас ставит пробу
        draftRing?.let { ring ->
            ringPath(ring.toFlatRing(), projection)
            fillPaint.color = withAlpha(SELECTED_COLOR, 50)
            linePaint.color = SELECTED_COLOR
            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, linePaint)
        }
        draftCenter?.let { c ->
            geo.setCoords(c.lat, c.lon)
            projection.toPixels(geo, point)
            dotPaint.color = SELECTED_COLOR
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), dotRadius * 1.4f, dotPaint)
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), dotRadius * 1.4f, dotStroke)
        }
    }

    private fun ringPath(ring: DoubleArray, projection: Projection) {
        path.rewind()
        var i = 0
        while (i + 1 < ring.size) {
            geo.setCoords(ring[i], ring[i + 1])
            projection.toPixels(geo, point)
            if (i == 0) path.moveTo(point.x.toFloat(), point.y.toFloat()) else path.lineTo(point.x.toFloat(), point.y.toFloat())
            i += 2
        }
        path.close()
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val projection = mapView.projection
        if (projection.zoomLevel < MIN_ZOOM || draftRing != null || draftCenter != null) return false
        val p = projection.fromPixels(e.x.toInt(), e.y.toInt())
        // сначала — по точке центра (проба маленькая, в её контур на общем плане не попасть)
        for (proba in proby.asReversed()) {
            geo.setCoords(proba.center.lat, proba.center.lon)
            projection.toPixels(geo, point)
            val dx = e.x - point.x
            val dy = e.y - point.y
            if (dx * dx + dy * dy <= touchPx * touchPx) {
                onProbaTap(proba)
                return true
            }
        }
        if (projection.zoomLevel >= OUTLINE_ZOOM) {
            proby.lastOrNull { it.contains(p.latitude, p.longitude) }?.let {
                onProbaTap(it)
                return true
            }
        }
        return false
    }

    private fun withAlpha(color: Int, alpha: Int) =
        AndroidColor.argb(alpha, AndroidColor.red(color), AndroidColor.green(color), AndroidColor.blue(color))

    companion object {
        const val MIN_ZOOM = 13.5
        const val OUTLINE_ZOOM = 16.0
        const val LABEL_ZOOM = 17.5
        val SELECTED_COLOR: Int = AndroidColor.parseColor("#FFB74D")
        val PENDING_COLOR: Int = AndroidColor.parseColor("#BDBDBD")
    }
}
