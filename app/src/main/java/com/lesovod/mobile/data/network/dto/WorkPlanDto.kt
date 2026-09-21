package com.lesovod.mobile.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkPlanItemDto(
    val id: Int,
    val data: String,
    val zadacha: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    val kvartal: String? = null,
    val vydel: String? = null,
    val lesnichestvo: String? = null,
)

@Serializable
data class CompleteWorkPlanResponseDto(
    val done: Boolean,
)
