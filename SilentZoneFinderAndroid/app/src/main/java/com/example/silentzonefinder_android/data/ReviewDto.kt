package com.example.silentzonefinder_android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReviewDto(
    val id: Int,
    @SerialName("kakao_place_id") val kakaoPlaceId: String,
    val rating: Int,
    val placeName: String? = null,
    val placeAddress: String? = null,
    val text: String? = null,
    val images: List<String>? = null,
    @SerialName("noise_level_db") val noiseLevelDb: Double,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val amenities: List<String>? = null
)
