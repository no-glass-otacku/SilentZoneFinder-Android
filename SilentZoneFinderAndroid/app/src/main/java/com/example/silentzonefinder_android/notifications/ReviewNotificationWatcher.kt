package com.example.silentzonefinder_android.notifications

import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import android.content.Context
import com.example.silentzonefinder_android.data.ReviewDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ReviewNotificationWatcher(
    private val context: Context,
    private val client: SupabaseClient
) {

    private var channel: RealtimeChannel? = null

    fun start() {
        // 이미 시작돼 있으면 다시 만들지 않음
        if (channel != null) return

        // Realtime 채널 생성 (채널 이름은 아무거나 가능)
        val ch = client.realtime.channel("reviews_listener")
        channel = ch

        // Postgres 변경사항 Flow 생성
        val changeFlow = ch.postgresChangeFlow<PostgresAction>(
            schema = "public"
        ) {
            table = "reviews"
        }

        // Flow 수집
        changeFlow.onEach { action ->
            // INSERT 이벤트만 사용
            if (action is PostgresAction.Insert) {
                // record를 ReviewDto로 디코딩
                val review = action.decodeRecord<ReviewDto>()

                val placeIds = NotificationPlacesRepository.places
                if (placeIds.contains(review.kakaoPlaceId) &&
                    review.noiseLevelDb <= 50.0
                ) {
                    // 알림 띄우기 (함수 이름에 맞게 호출)
                    NotificationHelper.showNewReviewNotification(context, review)
                }
            }
        }.launchIn(GlobalScope)

        // subscribe 는 suspend 함수라 코루틴에서 호출
        GlobalScope.launch {
            ch.subscribe(blockUntilSubscribed = false)
        }
    }

    fun stop() {
        val ch = channel ?: return
        GlobalScope.launch {
            ch.unsubscribe()
        }
        channel = null
    }
}
