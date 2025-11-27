package com.example.silentzonefinder_android.notifications

object NotificationPlacesRepository {
    private val ids = mutableSetOf<String>()

    val places: Set<String>
        get() = ids

    fun update(newSet: Set<String>) {
        ids.clear()
        ids.addAll(newSet)
    }
}
