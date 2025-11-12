package com.example.silentzonefinder_android

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.silentzonefinder_android.databinding.ActivityMyFavoritesBinding

class MyFavoritesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMyFavoritesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
    override fun onResume() {
        super.onResume()
        setupBottomNavigation()
        binding.bottomNavigation.selectedItemId = R.id.navigation_my_favorite
        overridePendingTransition(0, 0)
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            // 1. 이동할 Activity 클래스를 먼저 결정합니다.
            val targetActivity = when (item.itemId) {
                R.id.navigation_map -> MainActivity::class.java
                R.id.navigation_my_reviews -> MyReviewsActivity::class.java
                R.id.navigation_my_favorite -> {
                    return@setOnItemSelectedListener true
                }
                R.id.navigation_profile -> ProfileActivity::class.java
                else -> return@setOnItemSelectedListener false // 예상치 못한 ID
            }

            // 2. 공통 로직을 한 번만 실행합니다.
            val intent = Intent(this, targetActivity)

            // Activity 재활용 및 부드러운 전환을 위한 플래그
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)

            startActivity(intent)

            // 3. 애니메이션 설정 (0, 0은 애니메이션 없음)
            overridePendingTransition(0, 0) // <--- 여기에 R.anim.ID 사용을 고려하세요!

            true // 이벤트 처리가 완료되었음을 반환
        }
    }
}