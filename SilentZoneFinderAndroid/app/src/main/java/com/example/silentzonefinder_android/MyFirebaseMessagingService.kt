package com.example.silentzonefinder_android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

// Supabase 관련
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.auth.auth

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")

        val session = SupabaseManager.client.auth.currentSessionOrNull()
        session?.user?.let { user ->
            val userId = user.id.toString()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    SupabaseManager.client.postgrest["user_devices"].upsert(
                        mapOf(
                            "user_id" to userId,
                            "fcm_token" to token
                        )
                    )
                    Log.d("FCM", "Token saved to Supabase")
                } catch (e: Exception) {
                    Log.e("FCM", "Failed to save token", e)
                }
            }
        }
    }


    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: "SilentZone 알림"
        val body = message.notification?.body ?: ""

        Log.d("FCM", "onMessageReceived: $title / $body")

        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "silentzone_channel"

        // 1) 채널 생성 (Oreo 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                "SilentZone 알림",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        // 2) Notification 빌더 생성
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)   // ✅ 실제로 존재하는 아이콘으로!
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        // 3) 권한 체크 후 표시
        val canNotify =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

        if (canNotify) {
            with(NotificationManagerCompat.from(this)) {
                // 매번 다른 ID로 띄우기 위해 현재 시간 사용
                notify(System.currentTimeMillis().toInt(), builder.build())
            }
        } else {
            Log.w("FCM", "POST_NOTIFICATIONS 권한이 없어 알림을 표시하지 못했습니다.")
        }
    }
}
