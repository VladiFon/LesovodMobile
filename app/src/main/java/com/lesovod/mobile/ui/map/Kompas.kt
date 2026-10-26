package com.lesovod.mobile.ui.map

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Компас телефона: куда смотрит верх экрана, градусы от истинного севера
 * (магнитное склонение поправляем по месту, если оно известно).
 * null — датчика нет или он ещё не прислал данных.
 *
 * Нужен для азимута ряда у прямоугольной пробы: буссоли у мастера с собой
 * обычно нет, а телефон вдоль ряда направить — дело секунды.
 */
@Composable
fun rememberKompas(place: LatLon?): State<Double?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<Double?>(null) }
    DisposableEffect(place?.lat?.toInt(), place?.lon?.toInt()) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        val sklonenie = place?.let {
            GeomagneticField(it.lat.toFloat(), it.lon.toFloat(), 0f, System.currentTimeMillis()).declination.toDouble()
        } ?: 0.0
        @Suppress("DEPRECATION")
        val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val rot = FloatArray(9)
        val remapped = FloatArray(9)
        val orient = FloatArray(3)
        var smooth: Double? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                val (x, y) = when (display.rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rot, x, y, remapped)
                SensorManager.getOrientation(remapped, orient)
                val raw = norm360(Math.toDegrees(orient[0].toDouble()) + sklonenie)
                // Сглаживаем по кругу, чтобы цифра не прыгала (и не ломалась на 359→0).
                val prev = smooth
                smooth = if (prev == null) raw else norm360(prev + 0.15 * (((raw - prev + 540) % 360) - 180))
                state.value = smooth
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return state
}

private fun norm360(d: Double) = ((d % 360) + 360) % 360
