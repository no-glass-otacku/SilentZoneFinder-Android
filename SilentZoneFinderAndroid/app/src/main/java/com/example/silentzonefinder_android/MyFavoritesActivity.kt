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

        // 상단 뒤로가기
        binding.btnBack.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            // Stack을 꼬이지 않게: 새 액티비티로 이동
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()  // 현재 즐겨찾기 화면은 제거
            overridePendingTransition(0, 0)
        }
    }

    override fun onResume() {
        super.onResume()

        // 네비게이션 리스너 재등록
        setupBottomNavigation()

        // 현재 탭 고정 (파란색 유지)
        binding.bottomNavigation.selectedItemId = R.id.navigation_my_favorite

        // 애니메이션 제거
        overridePendingTransition(0, 0)
    }

    private fun setupBottomNavigation() {

        binding.bottomNavigation.setOnItemSelectedListener { item ->

            val targetActivity = when (item.itemId) {

                // Map 화면
                R.id.navigation_map -> MainActivity::class.java

                // My Reviews 화면
                R.id.navigation_my_reviews -> MyReviewsActivity::class.java

                // My Favorites 화면 (현재 화면)
                R.id.navigation_my_favorite -> {
                    return@setOnItemSelectedListener true
                }

                // Profile 화면
                R.id.navigation_profile -> ProfileActivity::class.java

                else -> return@setOnItemSelectedListener false
            }

            // 동일 화면이면 다시 실행하지 않음
            if (targetActivity == this::class.java) {
                return@setOnItemSelectedListener true
            }

            val intent = Intent(this, targetActivity).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }

            startActivity(intent)
            overridePendingTransition(0, 0)

            true
        }
    }
}