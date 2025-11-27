package com.example.silentzonefinder_android.utils

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
import java.util.UUID

object NotificationHelper {
    private const val CHANNEL_ID_QUIET_ZONE = "quiet_zone_recommendation"
    private const val CHANNEL_ID_THRESHOLD_ALERT = "noise_threshold_alert"
    private const val CHANNEL_NAME_QUIET_ZONE = "조용한 장소 추천"
    private const val CHANNEL_NAME_THRESHOLD_ALERT = "소음 임계값 알림"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 조용한 장소 추천 채널
            val quietZoneChannel = NotificationChannel(
                CHANNEL_ID_QUIET_ZONE,
                CHANNEL_NAME_QUIET_ZONE,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "주변 조용한 장소 추천 알림"
                enableVibration(true)
                enableLights(true)
            }

            // 소음 임계값 알림 채널
            val thresholdAlertChannel = NotificationChannel(
                CHANNEL_ID_THRESHOLD_ALERT,
                CHANNEL_NAME_THRESHOLD_ALERT,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "설정한 소음 임계값 초과 시 알림"
                enableVibration(true)
                enableLights(true)
            }

            notificationManager.createNotificationChannel(quietZoneChannel)
            notificationManager.createNotificationChannel(thresholdAlertChannel)
        }
    }

    fun showQuietZoneNotification(
        context: Context,
        placeName: String,
        placeAddress: String,
        noiseLevelDb: Double,
        kakaoPlaceId: String
    ) {
        val intent = Intent(context, PlaceDetailActivity::class.java).apply {
            putExtra("kakao_place_id", kakaoPlaceId)
            putExtra("place_name", placeName)
            putExtra("address", placeAddress)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            kakaoPlaceId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_QUIET_ZONE)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("조용한 장소 발견!")
            .setContentText("${placeName}이 조용해졌습니다 (${noiseLevelDb.toInt()} dB) - 공부하기 좋은 시간이에요!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${placeName}이 조용해졌습니다 (${noiseLevelDb.toInt()} dB) - 공부하기 좋은 시간이에요!")
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(
            kakaoPlaceId.hashCode(),
            notification
        )

        // 알림 히스토리에 저장
        NotificationHistoryManager.addNotification(
            context,
            NotificationHistoryItem(
                id = UUID.randomUUID().toString(),
                type = NotificationType.QUIET_ZONE_RECOMMENDATION,
                title = "조용한 장소 발견!",
                message = "${placeName}이 조용해졌습니다 (${noiseLevelDb.toInt()} dB) - 공부하기 좋은 시간이에요!",
                placeName = placeName,
                placeId = kakaoPlaceId,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun showThresholdAlertNotification(
        context: Context,
        placeName: String,
        placeAddress: String,
        thresholdDb: Double,
        detectedDb: Double,
        kakaoPlaceId: String
    ) {
        val intent = Intent(context, PlaceDetailActivity::class.java).apply {
            putExtra("kakao_place_id", kakaoPlaceId)
            putExtra("place_name", placeName)
            putExtra("address", placeAddress)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            kakaoPlaceId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_THRESHOLD_ALERT)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("조용해졌어요")
            .setContentText("${placeName}이 ${thresholdDb.toInt()} dB 임계값 이하로 내려갔습니다 (${detectedDb.toInt()} dB 감지).")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${placeName}이 ${thresholdDb.toInt()} dB 임계값 이하로 내려갔습니다 (${detectedDb.toInt()} dB 감지).")
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(
            kakaoPlaceId.hashCode(),
            notification
        )

        // 알림 히스토리에 저장
        NotificationHistoryManager.addNotification(
            context,
            NotificationHistoryItem(
                id = UUID.randomUUID().toString(),
                    type = NotificationType.THRESHOLD_ALERT,
                    title = "조용해졌어요",
                    message = "${placeName}이 ${thresholdDb.toInt()} dB 임계값 이하로 내려갔습니다 (${detectedDb.toInt()} dB 감지).",
                placeName = placeName,
                placeId = kakaoPlaceId,
                timestamp = System.currentTimeMillis(),
                thresholdDb = thresholdDb,
                detectedDb = detectedDb
            )
        )
    }
}

