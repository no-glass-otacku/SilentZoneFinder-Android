package com.example.silentzonefinder_android.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class NotificationHistoryItem(
    val id: String,
    val type: NotificationType,
    val title: String,
    val message: String,
    val placeName: String?,
    val placeId: String?,
    val timestamp: Long,
    val thresholdDb: Double? = null,
    val detectedDb: Double? = null
)

enum class NotificationType {
    QUIET_ZONE_RECOMMENDATION,
    THRESHOLD_ALERT
}

object NotificationHistoryManager {
    private const val PREFS_NAME = "notification_history"
    private const val KEY_HISTORY = "history"
    private const val MAX_HISTORY_SIZE = 50

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun addNotification(context: Context, item: NotificationHistoryItem) {
        val history = getHistory(context).toMutableList()
        history.add(0, item) // 최신 항목을 맨 앞에 추가

        // 최대 개수 제한
        if (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.size - 1)
        }

        saveHistory(context, history)
    }

    fun getHistory(context: Context): List<NotificationHistoryItem> {
        val json = getPrefs(context).getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<NotificationHistoryItem>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveHistory(context: Context, history: List<NotificationHistoryItem>) {
        val json = Gson().toJson(history)
        getPrefs(context).edit().putString(KEY_HISTORY, json).apply()
    }

    fun clearHistory(context: Context) {
        getPrefs(context).edit().remove(KEY_HISTORY).apply()
    }
}









