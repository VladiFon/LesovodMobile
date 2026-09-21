package com.lesovod.mobile.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
}

/**
 * Разовая геометка "здесь и сейчас" — не подписка на постоянное
 * слежение (см. докстринг раздела 3.6 плана мобильного приложения).
 * Возвращает null и без исключения, если разрешение не выдано или
 * координаты по какой-то причине не удалось получить — отметка
 * присутствия должна всё равно сохраниться, просто без геометки.
 */
suspend fun getCurrentLocationOrNull(context: Context): Location? {
    if (!hasLocationPermission(context)) return null

    val client = LocationServices.getFusedLocationProviderClient(context)
    val cancellationTokenSource = CancellationTokenSource()

    return try {
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { cancellationTokenSource.cancel() }
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationTokenSource.token)
                .addOnSuccessListener { location -> cont.resume(location) }
                .addOnFailureListener { cont.resume(null) }
        }
    } catch (_: SecurityException) {
        null
    }
}
