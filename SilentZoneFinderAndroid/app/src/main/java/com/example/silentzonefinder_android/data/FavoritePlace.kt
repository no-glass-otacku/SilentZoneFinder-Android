package com.example.silentzonefinder_android.data

data class FavoritePlace(
    val kakaoPlaceId: String,
    val placeName: String,
    val address: String,
    val avgNoiseDb: Int,
    val noiseStatus: String,
    val avgRating: Double,
    val reviewCount: Int
)

