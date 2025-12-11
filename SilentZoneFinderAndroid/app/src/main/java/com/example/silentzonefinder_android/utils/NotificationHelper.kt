package com.example.silentzonefinder_android.utils

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.silentzonefinder_android.MainActivity
import com.example.silentzonefinder_android.PlaceDetailActivity
import com.example.silentzonefinder_android.R
import com.example.silentzonefinder_android.data.ReviewDto
import java.util.UUID

object NotificationHelper {

    private const val CHANNEL_NEW_REVIEW = "channel_new_review"
    private const val CHANNEL_THRESHOLD_ALERT = "channel_threshold_alert"
    private const val CHANNEL_QUIET_ZONE = "channel_quiet_zone"

    /** 앱 시작 시 채널 3개를 한 번에 생성 (MapApplication에서 호출) */
    fun createNotificationChannels(context: Context) {
        createChannelIfNeeded(context, CHANNEL_NEW_REVIEW, "New Review Alert")
        createChannelIfNeeded(context, CHANNEL_THRESHOLD_ALERT, "Threshold Alert")
        createChannelIfNeeded(context, CHANNEL_QUIET_ZONE, "Quiet Place Alert")
    }

    // ================== 내부 공통 유틸 ==================

    private fun createChannelIfNeeded(context: Context, channelId: String, channelName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(channelId)
            if (existing == null) {
                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH
                )
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildPlaceDetailPendingIntent(
        context: Context,
        kakaoPlaceId: String?
    ): PendingIntent {
        val intent = if (kakaoPlaceId.isNullOrBlank()) {
            Intent(context, MainActivity::class.java)
        } else {
            Intent(context, PlaceDetailActivity::class.java).apply {
                putExtra("kakao_place_id", kakaoPlaceId)
            }
        }

        return PendingIntent.getActivity(
            context,
            (kakaoPlaceId ?: "main").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ================== 1) 새 리뷰 알림 ==================

    @SuppressLint("MissingPermission")
    fun showNewReviewNotification(
        context: Context,
        review: ReviewDto
    ) {
        createChannelIfNeeded(context, CHANNEL_NEW_REVIEW, "New Review Alert")

        val placeName = review.placeName
        val placeAddress = review.placeAddress
        val title =
            if (!placeName.isNullOrBlank()) {
                "$placeName — New quiet review added"
            } else {
                "New review added"
            }
        val message =
            if (!review.text.isNullOrBlank()) review.text!!
            else "Noise ${review.noiseLevelDb.toInt()} dB review has been added."

        val pendingIntent = buildPlaceDetailPendingIntent(context, review.kakaoPlaceId)
        val notificationId = UUID.randomUUID().toString()

        val notification = NotificationCompat.Builder(context, CHANNEL_NEW_REVIEW)
            .setSmallIcon(R.drawable.ic_notifications)  // 프로젝트에 존재하는 아이콘으로 유지
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context)
            .notify(notificationId.hashCode(), notification)

        // 히스토리 저장
        NotificationHistoryManager.addHistoryItem(
            context,
            NotificationHistoryItem(
                id = notificationId,
                type = NotificationType.NEW_REVIEW,
                title = title,
                message = message,
                placeName = placeName,                    // ReviewDto에는 placeName 없음
                placeId = review.kakaoPlaceId,
                timestamp = System.currentTimeMillis(),
                thresholdDb = null,
                detectedDb = review.noiseLevelDb
            )
        )
    }

    // ================== 2) 조용한 장소 추천 알림 ==================

    @SuppressLint("MissingPermission")
    fun showQuietZoneNotification(
        context: Context,
        placeName: String,
        placeAddress: String,
        detectedDb: Double,
        kakaoPlaceId: String
    ) {
        createChannelIfNeeded(context, CHANNEL_QUIET_ZONE, "Quiet Place Alert")

        val title = "It's quiet now"
        val message =
            "$placeName is currently around ${detectedDb.toInt()} dB and relatively quiet."

        val pendingIntent = buildPlaceDetailPendingIntent(context, kakaoPlaceId)
        val notificationId = UUID.randomUUID().toString()

        val bigText =
            if (placeAddress.isNotBlank()) "$message\n$placeAddress" else message

        val notification = NotificationCompat.Builder(context, CHANNEL_QUIET_ZONE)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context)
            .notify(notificationId.hashCode(), notification)

        NotificationHistoryManager.addHistoryItem(
            context,
            NotificationHistoryItem(
                id = notificationId,
                type = NotificationType.THRESHOLD_ALERT, // 필요하면 QUIET_ZONE 타입 추가해서 변경
                title = title,
                message = message,
                placeName = placeName,
                placeId = kakaoPlaceId,
                timestamp = System.currentTimeMillis(),
                thresholdDb = null,
                detectedDb = detectedDb
            )
        )
    }

    // ================== 3) 임계값 알림 ==================

    @SuppressLint("MissingPermission")
    fun showThresholdAlertNotification(
        context: Context,
        kakaoPlaceId: String,
        placeName: String,
        thresholdDb: Double,
        detectedDb: Double
    ) {
        createChannelIfNeeded(context, CHANNEL_THRESHOLD_ALERT, "Threshold Alert")

        val title = "It's quieter now"
        val message =
            "$placeName dropped below your threshold ${thresholdDb.toInt()} dB (detected ${detectedDb.toInt()} dB)."

        val pendingIntent = buildPlaceDetailPendingIntent(context, kakaoPlaceId)
        val notificationId = UUID.randomUUID().toString()

        val notification = NotificationCompat.Builder(context, CHANNEL_THRESHOLD_ALERT)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context)
            .notify(notificationId.hashCode(), notification)

        NotificationHistoryManager.addHistoryItem(
            context,
            NotificationHistoryItem(
                id = notificationId,
                type = NotificationType.THRESHOLD_ALERT,
                title = title,
                message = message,
                placeName = placeName,
                placeId = kakaoPlaceId,
                timestamp = System.currentTimeMillis(),
                thresholdDb = thresholdDb,
                detectedDb = detectedDb
            )
        )
    }
}
