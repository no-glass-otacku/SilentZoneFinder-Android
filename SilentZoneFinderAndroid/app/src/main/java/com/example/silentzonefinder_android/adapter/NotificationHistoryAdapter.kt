package com.example.silentzonefinder_android.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.silentzonefinder_android.PlaceDetailActivity
import com.example.silentzonefinder_android.R
import com.example.silentzonefinder_android.utils.NotificationHistoryItem
import com.example.silentzonefinder_android.utils.NotificationType
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationHistoryAdapter(
    private val items: List<NotificationHistoryItem>,
    private val onItemClick: (NotificationHistoryItem) -> Unit
) : RecyclerView.Adapter<NotificationHistoryAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.card_notification)
        val iconContainer: MaterialCardView = itemView.findViewById(R.id.icon_container)
        val title: TextView = itemView.findViewById(R.id.tv_title)
        val time: TextView = itemView.findViewById(R.id.tv_time)
        val message: TextView = itemView.findViewById(R.id.tv_message)
        val placeChip: TextView = itemView.findViewById(R.id.chip_place)
        val dateChip: TextView = itemView.findViewById(R.id.chip_date)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        // 아이콘 배경색 설정
        val iconColor = when (item.type) {
            NotificationType.QUIET_ZONE_RECOMMENDATION -> "#DCFCE7" // 녹색
            NotificationType.THRESHOLD_ALERT -> "#FEE2E2" // 빨간색
        }
        holder.iconContainer.setCardBackgroundColor(android.graphics.Color.parseColor(iconColor))

        holder.title.text = item.title
        holder.message.text = item.message

        // 시간 포맷팅
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        holder.time.text = timeFormat.format(Date(item.timestamp))

        // 날짜 칩
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val today = Date()
        val itemDate = Date(item.timestamp)
        val dateText = when {
            isSameDay(today, itemDate) -> "Today"
            isSameDay(Date(today.time - 86400000), itemDate) -> "Yesterday"
            else -> dateFormat.format(itemDate)
        }
        holder.dateChip.text = dateText

        // 장소 칩
        holder.placeChip.text = item.placeName ?: "알 수 없는 장소"
        holder.placeChip.visibility = if (item.placeName != null) View.VISIBLE else View.GONE

        // 클릭 이벤트
        holder.card.setOnClickListener {
            if (item.placeId != null && item.placeName != null) {
                val intent = Intent(context, PlaceDetailActivity::class.java).apply {
                    putExtra("kakao_place_id", item.placeId)
                    putExtra("place_name", item.placeName)
                }
                context.startActivity(intent)
            }
            onItemClick(item)
        }
    }

    override fun getItemCount() = items.size

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = java.util.Calendar.getInstance()
        val cal2 = java.util.Calendar.getInstance()
        cal1.time = date1
        cal2.time = date2
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
                cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }
}









