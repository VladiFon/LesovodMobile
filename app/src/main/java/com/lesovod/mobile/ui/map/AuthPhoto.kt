package com.lesovod.mobile.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Фото с сервера по адресу вида "/api/bot/geo-notes/12/photo" — с токеном рабочего (Coil в
 * проекте нет, а картинок немного). Уменьшается при декодировании, чтобы 12-мегапиксельное фото
 * не съело память; хранится на диске, чтобы открытое однажды фото показывалось без связи.
 */
@Composable
fun AuthPhoto(relativeUrl: String, modifier: Modifier = Modifier, maxHeightDp: Int = 220) {
    val context = LocalContext.current
    var fullscreen by remember { mutableStateOf(false) }
    val state by produceState<PhotoState>(PhotoState.Loading, relativeUrl) {
        value = withContext(Dispatchers.IO) { loadPhoto(context, relativeUrl) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp, max = maxHeightDp.dp)
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state) {
            PhotoState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            is PhotoState.Error -> Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            is PhotoState.Ready -> Image(
                bitmap = s.bitmap.asImageBitmap(),
                contentDescription = "Фото",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().clickable { fullscreen = true },
            )
        }
    }

    val ready = state as? PhotoState.Ready
    if (fullscreen && ready != null) {
        Dialog(onDismissRequest = { fullscreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                modifier = Modifier.fillMaxSize().clickable { fullscreen = false },
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(0.dp).clip(RoundedCornerShape(0.dp))) {
                    Image(
                        bitmap = ready.bitmap.asImageBitmap(),
                        contentDescription = "Фото",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    "Нажмите, чтобы закрыть",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                )
            }
        }
    }
}

private sealed interface PhotoState {
    data object Loading : PhotoState
    data class Ready(val bitmap: Bitmap) : PhotoState
    data class Error(val message: String) : PhotoState
}

private const val MAX_SIDE_PX = 1600

private fun loadPhoto(context: android.content.Context, relativeUrl: String): PhotoState {
    val cacheDir = File(context.cacheDir, "map_photos").apply { mkdirs() }
    val file = File(cacheDir, relativeUrl.replace(Regex("[^A-Za-z0-9]"), "_") + ".img")
    if (!file.exists()) {
        val token = SessionManager.getInstance(context).session.value?.token
            ?: return PhotoState.Error("Войдите заново, чтобы увидеть фото")
        val url = NetworkModule.BASE_URL.trimEnd('/') + "/" + relativeUrl.trimStart('/')
        try {
            NetworkModule.httpClient.newCall(
                Request.Builder().url(url).header("Authorization", "Bearer $token").build(),
            ).execute().use { response ->
                if (!response.isSuccessful) return PhotoState.Error("Фото не загрузилось (${response.code})")
                val body = response.body ?: return PhotoState.Error("Пустой ответ сервера")
                val tmp = File(cacheDir, file.name + ".part")
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            return PhotoState.Error("Нет связи — фото не загружено")
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > MAX_SIDE_PX || bounds.outHeight / sample > MAX_SIDE_PX) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return PhotoState.Error("Не удалось открыть фото").also { file.delete() }
    return PhotoState.Ready(bitmap)
}
