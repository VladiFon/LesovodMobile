package com.lesovod.mobile.data.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.create
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.lesovod.mobile.BuildConfig
import com.lesovod.mobile.data.session.SessionExpiryBus
import com.lesovod.mobile.data.session.SessionManager

object NetworkModule {
    private const val BASE_URL = "https://lesovodapipom.store/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
    }

    /**
     * 401 от сервера = токен недействителен/истёк — чистим сессию и просим экраны вернуться на вход.
     * Сам логин исключён: неверный пароль там же отдаёт 401, но это не истёкшая сессия, а
     * обычная ошибка формы — её показывает LoginScreen, редирект тут не нужен.
     */
    private val authExpiryInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code == 401 && !request.url.encodedPath.endsWith("auth/worker-login")) {
            SessionManager.peekInstance()?.clear()
            SessionExpiryBus.notifyExpired()
        }
        response
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authExpiryInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val api: ApiService = retrofit.create()
}
