package com.example.silentzonefinder_android

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.silentzonefinder_android.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 상단 뒤로가기
        binding.btnBack.setOnClickListener {
            finish()
        }

        // 하단 네비게이션 (Profile 탭 유지)
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


        // 언어 / 테마 / 약관 / 개인정보 클릭 시 임시 토스트
        binding.rowLanguage.setOnClickListener {
            Toast.makeText(this, "Language 선택 화면은 추후 추가 예정", Toast.LENGTH_SHORT).show()
        }

        binding.rowTheme.setOnClickListener {
            Toast.makeText(this, "Theme 변경 기능은 추후 추가 예정", Toast.LENGTH_SHORT).show()
        }

        binding.rowTerms.setOnClickListener {
            Toast.makeText(this, "이용약관 화면은 추후 추가 예정", Toast.LENGTH_SHORT).show()
        }

        binding.rowPrivacy.setOnClickListener {
            Toast.makeText(this, "개인정보처리방침 화면은 추후 추가 예정", Toast.LENGTH_SHORT).show()
        }
    }
}