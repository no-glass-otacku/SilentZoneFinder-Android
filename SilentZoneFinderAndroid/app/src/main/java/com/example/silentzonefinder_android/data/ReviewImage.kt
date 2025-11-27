package com.example.silentzonefinder_android.data

import android.net.Uri
data class ReviewImage(
    val uri: Uri, // 기기 내 파일의 URI
    var isUploaded: Boolean = false, // Supabase 업로드 완료 여부
    var uploadedUrl: String? = null // Supabase에 업로드된 최종 URL
)
