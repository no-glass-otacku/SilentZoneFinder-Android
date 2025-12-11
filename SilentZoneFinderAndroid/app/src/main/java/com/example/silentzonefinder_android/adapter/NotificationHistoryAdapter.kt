package com.example.silentzonefinder_android.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.silentzonefinder_android.R
import com.example.silentzonefinder_android.utils.NotificationHistoryItem
import com.example.silentzonefinder_android.utils.NotificationType
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationHistoryAdapter(
    private var items: List<NotificationHistoryItem>,
    private val onClick: (NotificationHistoryItem) -> Unit
) : RecyclerView.Adapter<NotificationHistoryAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val card: MaterialCardView = view.findViewById(R.id.card_notification)
        private val iconContainer: MaterialCardView = view.findViewById(R.id.icon_container)
        private val titleView: TextView = view.findViewById(R.id.tv_title)
        private val messageView: TextView = view.findViewById(R.id.tv_message)
        private val timeView: TextView = view.findViewById(R.id.tv_time)
        private val chipPlace: TextView = view.findViewById(R.id.chip_place)
        private val chipDate: TextView = view.findViewById(R.id.chip_date)

        fun bind(item: NotificationHistoryItem) {
            titleView.text = item.title
            messageView.text = item.message

            // 장소 이름 칩
            if (item.placeName.isNullOrBlank()) {
                chipPlace.visibility = View.GONE
            } else {
                chipPlace.visibility = View.VISIBLE
                chipPlace.text = item.placeName
            }

            // 상단 시간 텍스트
            timeView.text = formatTime(item.timestamp)

            // 날짜 칩
            chipDate.text = formatDate(item.timestamp)

            // 알림 타입에 따라 아이콘 배경색 바꾸기 (원하면)
            when (item.type) {
                NotificationType.NEW_REVIEW -> {
                    iconContainer.setCardBackgroundColor(Color.parseColor("#DCFCE7"))
                }
                NotificationType.THRESHOLD_ALERT -> {
                    iconContainer.setCardBackgroundColor(Color.parseColor("#FEE2E2"))
                }
            }

            card.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<NotificationHistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun formatTime(timestamp: Long): String {
        val df = SimpleDateFormat("HH:mm", Locale.getDefault())
        return df.format(Date(timestamp))
    }

    private fun formatDate(timestamp: Long): String {
        val df = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
        return df.format(Date(timestamp))
    }
}
