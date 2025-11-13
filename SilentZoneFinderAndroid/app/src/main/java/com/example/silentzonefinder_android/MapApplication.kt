package com.example.silentzonefinder_android

import android.app.Application
import android.util.Log
import com.kakao.vectormap.KakaoMapSdk

class MapApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        try {
            val appKey = BuildConfig.KAKAO_NATIVE_APP_KEY
            val packageName = packageName

            Log.d("MapApplication", "앱 패키지명: $packageName")
            Log.d("MapApplication", "카카오맵 네이티브 앱 키 길이: ${appKey.length}")
            Log.d("MapApplication", "카카오맵 네이티브 앱 키 (처음 10자): ${if (appKey.isNotEmpty()) appKey.take(10) + "..." else "비어있음"}")

            if (appKey.isNotEmpty()) {
                Log.d("MapApplication", "카카오맵 SDK 초기화 시도...")
                KakaoMapSdk.init(this, appKey)
                Log.d("MapApplication", "카카오맵 SDK 초기화 완료")
            } else {
                Log.e("MapApplication", "카카오맵 네이티브 앱 키가 설정되지 않았습니다.")
                Log.e("MapApplication", "local.properties 파일에 kakao.native.app.key를 확인하세요.")
            }
        } catch (e: Exception) {
            Log.e("MapApplication", "카카오맵 SDK 초기화 실패", e)
            Log.e("MapApplication", "오류 타입: ${e.javaClass.simpleName}")
            Log.e("MapApplication", "오류 메시지: ${e.message}")
        }
    }
}

