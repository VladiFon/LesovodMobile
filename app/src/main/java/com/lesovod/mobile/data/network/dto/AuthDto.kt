package com.lesovod.mobile.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkerLoginRequest(
    val login: String,
    val pin: String,
)

@Serializable
data class WorkerUserDto(
    val id: Int? = null,
    val login: String? = null,
    val fio: String? = null,
    val dolzhnost: String? = null,
    val uchastok: String? = null,
    @SerialName("app_identity") val appIdentity: String? = null,
)

@Serializable
data class LoginResponseDto(
    val token: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    val worker: WorkerUserDto? = null,
)
