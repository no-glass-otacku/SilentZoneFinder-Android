package com.example.silentzonefinder_android

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseManager {
    private const val TAG = "SupabaseManager"
    
    val client: SupabaseClient by lazy {
        val supabaseUrl = BuildConfig.SUPABASE_URL
        val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        
        // 디버깅: 설정된 값 확인 (키는 일부만 표시)
        Log.d(TAG, "Supabase URL: $supabaseUrl")
        Log.d(TAG, "Supabase Key (처음 20자): ${if (supabaseKey.length > 20) supabaseKey.take(20) + "..." else supabaseKey}")
        Log.d(TAG, "Supabase Key 길이: ${supabaseKey.length}")
        
        // 빈 값 체크
        if (supabaseUrl.isEmpty() || supabaseKey.isEmpty()) {
            Log.e(TAG, "Supabase URL 또는 Key가 설정되지 않았습니다.")
            Log.e(TAG, "URL이 비어있음: ${supabaseUrl.isEmpty()}")
            Log.e(TAG, "Key가 비어있음: ${supabaseKey.isEmpty()}")
            Log.e(TAG, "local.properties 파일을 확인하세요.")
            throw IllegalStateException(
                "Supabase 설정이 누락되었습니다. local.properties에 supabase.url과 supabase.anon.key를 설정하세요."
            )
        }
        
        // URL 형식 검증
        if (!supabaseUrl.startsWith("https://") || !supabaseUrl.contains(".supabase.co")) {
            Log.w(TAG, "Supabase URL 형식이 올바르지 않을 수 있습니다: $supabaseUrl")
        }
        
        // API Key 형식 검증 (JWT 토큰은 보통 3개 부분으로 나뉨)
        val keyParts = supabaseKey.split(".")
        if (keyParts.size != 3) {
            Log.w(TAG, "API Key 형식이 올바르지 않을 수 있습니다. JWT 토큰은 3개 부분으로 나뉩니다.")
            Log.w(TAG, "현재 Key 부분 개수: ${keyParts.size}")
        }
        
        try {
            Log.d(TAG, "Supabase 클라이언트 생성 시도 중...")
            val client = createSupabaseClient(
                supabaseUrl = supabaseUrl,
                supabaseKey = supabaseKey
            ) {
                install(Auth)
                install(Postgrest)
                install(Storage)
            }
            Log.d(TAG, "Supabase 클라이언트 생성 성공!")
            client
        } catch (e: Exception) {
            Log.e(TAG, "Supabase 클라이언트 초기화 실패", e)
            Log.e(TAG, "오류 타입: ${e.javaClass.simpleName}")
            Log.e(TAG, "오류 메시지: ${e.message}")
            e.printStackTrace()
            throw IllegalStateException("Supabase 클라이언트 초기화에 실패했습니다: ${e.message}", e)
        }
    }
}

