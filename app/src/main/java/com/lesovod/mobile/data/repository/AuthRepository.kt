package com.lesovod.mobile.data.repository

import com.lesovod.mobile.data.network.ApiService
import com.lesovod.mobile.data.network.dto.WorkerLoginRequest
import com.lesovod.mobile.data.network.extractErrorMessage
import com.lesovod.mobile.data.session.SessionManager
import retrofit2.HttpException

class AuthRepository(
    private val api: ApiService,
    private val sessionManager: SessionManager,
) {
    suspend fun login(login: String, pin: String): Result<Unit> {
        return try {
            val response = api.workerLogin(WorkerLoginRequest(login = login, pin = pin))
            sessionManager.save(response)
            Result.success(Unit)
        } catch (e: HttpException) {
            Result.failure(Exception(extractErrorMessage(e, "Неверный логин или PIN")))
        } catch (e: Exception) {
            Result.failure(Exception("Не удалось связаться с сервером. Проверьте подключение к интернету."))
        }
    }

    suspend fun logout() {
        val token = sessionManager.session.value?.token
        try {
            if (token != null) {
                api.logout("Bearer $token")
            }
        } catch (_: Exception) {
            // сервер недоступен — всё равно выходим локально
        } finally {
            sessionManager.clear()
        }
    }
}
