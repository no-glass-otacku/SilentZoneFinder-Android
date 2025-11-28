package com.example.silentzonefinder_android.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.silentzonefinder_android.SupabaseManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SearchHistoryItem(
    val query: String,
    val timestamp: Long
)

object SearchHistoryManager {
    private const val PREFS_NAME_PREFIX = "search_history"
    private const val KEY_HISTORY = "history"
    private const val MAX_HISTORY_SIZE = 20

    /**
     * 사용자별로 검색 기록을 분리하여 저장
     * @param userId 현재 로그인한 사용자 ID (null이면 기기별 저장)
     */
    private fun getPrefs(context: Context, userId: String? = null): SharedPreferences {
        val prefsName = if (userId != null) {
            "${PREFS_NAME_PREFIX}_$userId"
        } else {
            "${PREFS_NAME_PREFIX}_guest"
        }
        return context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }
    
    /**
     * 현재 로그인한 사용자 ID를 가져옴 (동기, 빠른 조회용)
     * 주의: 비동기로 가져오는 것이 더 안전하지만, 성능을 위해 동기 방식 사용
     */
    private fun getCurrentUserIdSync(): String? {
        return try {
            val session = SupabaseManager.client.auth.currentSessionOrNull()
            session?.user?.id?.toString()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 현재 로그인한 사용자 ID를 가져옴 (비동기)
     */
    suspend fun getCurrentUserId(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val session = SupabaseManager.client.auth.currentSessionOrNull()
                session?.user?.id?.toString()
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 검색어 추가 (사용자별로 분리 저장)
     * @param context Context
     * @param query 검색어
     * @param userId 현재 로그인한 사용자 ID (null이면 자동으로 현재 사용자 ID 조회)
     */
    fun addSearchQuery(context: Context, query: String, userId: String? = null) {
        val currentUserId = userId ?: getCurrentUserIdSync()
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return
        }

        val history = getHistory(context, currentUserId).toMutableList()
        
        // 중복 제거: 같은 검색어가 있으면 제거하고 맨 앞에 추가
        history.removeAll { it.query.equals(trimmedQuery, ignoreCase = true) }
        history.add(0, SearchHistoryItem(trimmedQuery, System.currentTimeMillis()))

        // 최대 개수 제한
        if (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.size - 1)
        }

        saveHistory(context, history, currentUserId)
    }

    /**
     * 검색 기록 조회 (사용자별로 분리)
     * @param context Context
     * @param userId 현재 로그인한 사용자 ID (null이면 자동으로 현재 사용자 ID 조회)
     */
    fun getHistory(context: Context, userId: String? = null): List<SearchHistoryItem> {
        val currentUserId = userId ?: getCurrentUserIdSync()
        val json = getPrefs(context, currentUserId).getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SearchHistoryItem>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 검색어 목록만 조회 (사용자별로 분리)
     * @param context Context
     * @param userId 현재 로그인한 사용자 ID (null이면 자동으로 현재 사용자 ID 조회)
     */
    fun getSearchQueries(context: Context, userId: String? = null): List<String> {
        val currentUserId = userId ?: getCurrentUserIdSync()
        return getHistory(context, currentUserId).map { it.query }
    }

    private fun saveHistory(context: Context, history: List<SearchHistoryItem>, userId: String? = null) {
        val json = Gson().toJson(history)
        getPrefs(context, userId).edit().putString(KEY_HISTORY, json).apply()
    }

    /**
     * 검색어 삭제 (사용자별로 분리)
     * @param context Context
     * @param query 삭제할 검색어
     * @param userId 현재 로그인한 사용자 ID (null이면 자동으로 현재 사용자 ID 조회)
     */
    fun removeSearchQuery(context: Context, query: String, userId: String? = null) {
        val currentUserId = userId ?: getCurrentUserIdSync()
        val history = getHistory(context, currentUserId).toMutableList()
        history.removeAll { it.query == query }
        saveHistory(context, history, currentUserId)
    }

    /**
     * 검색 기록 전체 삭제 (사용자별로 분리)
     * @param context Context
     * @param userId 현재 로그인한 사용자 ID (null이면 자동으로 현재 사용자 ID 조회)
     */
    fun clearHistory(context: Context, userId: String? = null) {
        val currentUserId = userId ?: getCurrentUserIdSync()
        getPrefs(context, currentUserId).edit().remove(KEY_HISTORY).apply()
    }
}


