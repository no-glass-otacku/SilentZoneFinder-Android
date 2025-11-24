package com.example.silentzonefinder_android.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.silentzonefinder_android.PlaceDetailActivity
import com.example.silentzonefinder_android.R
import com.example.silentzonefinder_android.data.ReviewDto

object NotificationHelper {

    private const val CHANNEL_ID = "review_notifications"

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
