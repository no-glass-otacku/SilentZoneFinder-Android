package com.example.silentzonefinder_android

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.silentzonefinder_android.adapter.NotificationHistoryAdapter
import com.example.silentzonefinder_android.databinding.ActivityNotificationHistoryBinding
import com.example.silentzonefinder_android.utils.NotificationHistoryManager

class NotificationHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationHistoryBinding
    private lateinit var adapter: NotificationHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadNotificationHistory()
    }

    private fun setupUI() {
        // 상단 뒤로가기 버튼
        binding.btnBack.setOnClickListener {
            finish()
        }

        // RecyclerView 설정
        adapter = NotificationHistoryAdapter(emptyList()) { item ->
            // 클릭 이벤트는 어댑터에서 처리
        }
        binding.historyListContainer.layoutManager = LinearLayoutManager(this)
        binding.historyListContainer.adapter = adapter

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

    private fun loadNotificationHistory() {
        val history = NotificationHistoryManager.getHistory(this)
        
        if (history.isEmpty()) {
            // 빈 상태 표시
            binding.historyListContainer.visibility = View.GONE
            // TODO: 빈 상태 UI 추가
        } else {
            binding.historyListContainer.visibility = View.VISIBLE
            adapter = NotificationHistoryAdapter(history) { item ->
                // 클릭 이벤트는 어댑터에서 처리
            }
            binding.historyListContainer.adapter = adapter
        }
    }

    override fun onResume() {
        super.onResume()
        loadNotificationHistory()
    }
}