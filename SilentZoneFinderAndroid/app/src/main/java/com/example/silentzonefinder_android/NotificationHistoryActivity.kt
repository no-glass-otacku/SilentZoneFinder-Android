package com.example.silentzonefinder_android

import android.content.Intent
import android.os.Bundle
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

        // 상단 뒤로가기 버튼: @+id/btn_back
        binding.btnBack.setOnClickListener {
            finish()
        }

        // RecyclerView: @+id/history_list_container
        binding.historyListContainer.layoutManager = LinearLayoutManager(this)
        adapter = NotificationHistoryAdapter(emptyList()) { item ->
            // 알림 아이템 클릭 시 장소 상세로 이동 (placeId 있을 때만)
            if (!item.placeId.isNullOrBlank()) {
                val intent = Intent(this, PlaceDetailActivity::class.java).apply {
                    putExtra("kakao_place_id", item.placeId)
                }
                startActivity(intent)
            }
        }
        binding.historyListContainer.adapter = adapter


    }

    override fun onResume() {
        super.onResume()
        loadNotificationHistory()
    }

    private fun loadNotificationHistory() {
        val history = NotificationHistoryManager.getHistory(this)
        // 이 레이아웃에는 "빈 화면용 TextView"가 따로 없으니까
        // 비었으면 그냥 아무것도 안 보이고, 있으면 리스트만 업데이트
        adapter.submitList(history)
    }
}
