package com.example.silentzonefinder_android.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SearchHistoryItem(
    val query: String,
    val timestamp: Long
)

object SearchHistoryManager {
    private const val PREFS_NAME = "search_history"
    private const val KEY_HISTORY = "history"
    private const val MAX_HISTORY_SIZE = 20

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun addSearchQuery(context: Context, query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return
        }

        val history = getHistory(context).toMutableList()
        
        // 중복 제거: 같은 검색어가 있으면 제거하고 맨 앞에 추가
        history.removeAll { it.query.equals(trimmedQuery, ignoreCase = true) }
        history.add(0, SearchHistoryItem(trimmedQuery, System.currentTimeMillis()))

        // 최대 개수 제한
        if (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.size - 1)
        }

        saveHistory(context, history)
    }

    fun getHistory(context: Context): List<SearchHistoryItem> {
        val json = getPrefs(context).getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SearchHistoryItem>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getSearchQueries(context: Context): List<String> {
        return getHistory(context).map { it.query }
    }

    private fun saveHistory(context: Context, history: List<SearchHistoryItem>) {
        val json = Gson().toJson(history)
        getPrefs(context).edit().putString(KEY_HISTORY, json).apply()
    }

    fun removeSearchQuery(context: Context, query: String) {
        val history = getHistory(context).toMutableList()
        history.removeAll { it.query == query }
        saveHistory(context, history)
    }

    fun clearHistory(context: Context) {
        getPrefs(context).edit().remove(KEY_HISTORY).apply()
    }
}


