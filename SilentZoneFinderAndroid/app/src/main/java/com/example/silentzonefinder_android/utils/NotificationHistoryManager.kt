package com.example.silentzonefinder_android.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

enum class NotificationType {
    NEW_REVIEW,
    THRESHOLD_ALERT
}

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

    fun getHistory(context: Context): List<NotificationHistoryItem> {
        val json = getPrefs(context).getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<NotificationHistoryItem>>() {}.type
            gson.fromJson<List<NotificationHistoryItem>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addHistoryItem(context: Context, newItem: NotificationHistoryItem) {
        val current = getHistory(context).toMutableList()
        current.add(0, newItem)
        val trimmed = current.take(200)
        val json = gson.toJson(trimmed)
        getPrefs(context).edit().putString(KEY_HISTORY, json).apply()
    }

    fun clearHistory(context: Context) {
        getPrefs(context).edit().remove(KEY_HISTORY).apply()
    }
}

