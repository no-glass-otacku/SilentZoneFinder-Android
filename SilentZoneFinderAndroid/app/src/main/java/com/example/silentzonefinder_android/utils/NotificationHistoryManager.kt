package com.example.silentzonefinder_android.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

// 어떤 종류의 알림인지 구분
enum class NotificationType {
    NEW_REVIEW,         // 새 리뷰 알림
    THRESHOLD_ALERT     // 임계값 알림 (조용해졌어요)
}

// 실제로 히스토리에 한 줄로 저장될 데이터
data class NotificationHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val type: NotificationType,
    val title: String,
    val message: String,
    val placeName: String?,
    val placeId: String?,
    val timestamp: Long,
    val thresholdDb: Double? = null,
    val detectedDb: Double? = null
)

object NotificationHistoryManager {

    private const val PREF_NAME = "notification_history_prefs"
    private const val KEY_HISTORY = "notification_history_list"

    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 전체 히스토리 조회 (최신순) */
    fun getHistory(context: Context): List<NotificationHistoryItem> {
        val json = getPrefs(context).getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<NotificationHistoryItem>>() {}.type
            gson.fromJson<List<NotificationHistoryItem>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 히스토리에 한 줄 추가 (최신이 위로 오도록) */
    fun addHistoryItem(context: Context, newItem: NotificationHistoryItem) {
        val current = getHistory(context).toMutableList()

        // 최신 기록을 맨 앞에 넣기
        current.add(0, newItem)

        // 너무 길어지지 않게 상한을 둘 수도 있음 (예: 200개까지만 유지)
        val trimmed = current.take(200)

        val json = gson.toJson(trimmed)
        getPrefs(context).edit().putString(KEY_HISTORY, json).apply()
    }

    /** 전체 삭제 */
    fun clearHistory(context: Context) {
        getPrefs(context).edit().remove(KEY_HISTORY).apply()
    }
}
