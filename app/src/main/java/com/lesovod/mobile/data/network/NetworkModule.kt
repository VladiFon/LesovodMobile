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
    const val BASE_URL = "https://lesovodapipom.store/"

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

    /**
     * Токен рабочего ко всем запросам /api/, где его не поставили явно: с 28.09.2026 сервер
     * отдаёт карту (кварталы, выделы, слои из QGIS, склады) только вошедшим.
     */
    private val authHeaderInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request()
        val token = SessionManager.peekInstance()?.session?.value?.token
        if (token.isNullOrBlank() || request.header("Authorization") != null ||
            !request.url.encodedPath.startsWith("/api/")
        ) {
            chain.proceed(request)
        } else {
            chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
        }
    }

    // OkHttp по умолчанию даёт 10 с на каждую фазу — маловато для мобильной связи в
    // лесничествах (медленная/нестабильная сеть), и SocketTimeoutException попадает в тот же
    // catch (IOException), что и настоящее отсутствие сети, поэтому "Нет соединения с
    // интернетом" мог показываться там, где сеть на самом деле есть, просто сервер не успел
    // ответить за 10 с.
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor(authHeaderInterceptor)
        .addInterceptor(authExpiryInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val api: ApiService = retrofit.create()

    /** Для загрузки картинок (фото меток и работ) тем же клиентом — с теми же таймаутами и выходом по 401. */
    val httpClient: OkHttpClient get() = okHttpClient
}
