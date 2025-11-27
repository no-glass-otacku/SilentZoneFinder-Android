package com.example.silentzonefinder_android.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.silentzonefinder_android.PlaceDetailActivity
import com.example.silentzonefinder_android.R
import com.example.silentzonefinder_android.data.ReviewDto

object NotificationHelper {

    const val CHANNEL_ID = "review_notifications"
    private const val CHANNEL_NAME = "조용한 리뷰 알림"
    private const val CHANNEL_DESCRIPTION = "즐겨찾기 장소에 새로운 조용한 리뷰가 등록되면 알려드려요."

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESCRIPTION
        }

        notificationManager.createNotificationChannel(channel)
    }

    fun showNewReviewNotification(
        context: Context,
        review: ReviewDto
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = PlaceDetailActivity.createIntent(
            context = context,
            placeId = review.kakaoPlaceId,
            placeName = "조용한 장소",
            address = null,
            category = null
        )

        val pendingIntent = PendingIntent.getActivity(
            context,
            review.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("조용한 리뷰 추가됨")
            .setContentText("소음 ${review.noiseLevelDb} dB")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(review.id, notification)
    }
}
