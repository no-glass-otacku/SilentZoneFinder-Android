package com.example.silentzonefinder_android.data

data class Review(
    val id: Long,
    val kakaoPlaceId: String,
    val placeName: String,
    val placeAddress: String,
    val decibel: Int,
    val status: String, // "Library Quiet", "Quiet Conversation", etc.
    val date: String,
    val reviewText: String,
    val rating: Int, // 1~5점
    val amenities: List<String> = emptyList()
)