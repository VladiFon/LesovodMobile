package com.lesovod.mobile.data.network

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

suspend fun buildPhotoPart(context: Context, uri: Uri): MultipartBody.Part = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri) ?: "image/jpeg"
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Не удалось прочитать файл фотографии")
    val extension = mimeType.substringAfter('/', "jpg")
    val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
    MultipartBody.Part.createFormData("file", "photo.$extension", requestBody)
}
