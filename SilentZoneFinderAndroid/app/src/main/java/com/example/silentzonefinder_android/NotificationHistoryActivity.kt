package com.example.silentzonefinder_android

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.silentzonefinder_android.databinding.ActivityNotificationHistoryBinding

class NotificationHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 상단 뒤로가기 버튼
        binding.btnBack.setOnClickListener {
            finish()
        }

        // 아래 바 : Profile 탭 기준으로 유지
        binding.bottomNavigation.selectedItemId = R.id.navigation_profile

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.navigation_map -> MainActivity::class.java
                R.id.navigation_my_reviews -> MyReviewsActivity::class.java
                R.id.navigation_my_favorite -> MyFavoritesActivity::class.java
                R.id.navigation_profile -> ProfileActivity::class.java
                else -> null
            }

            if (target != null && target != this::class.java) {
                val intent = Intent(this, target).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                startActivity(intent)
                overridePendingTransition(0, 0)
            }
            true
        }
    }
}