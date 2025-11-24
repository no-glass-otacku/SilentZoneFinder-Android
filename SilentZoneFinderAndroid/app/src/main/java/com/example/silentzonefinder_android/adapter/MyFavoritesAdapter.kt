package com.example.silentzonefinder_android.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.silentzonefinder_android.R
import com.example.silentzonefinder_android.data.FavoritePlace

class MyFavoritesAdapter(
    private var favoriteList: List<FavoritePlace>,
    private val onPlaceClick: (FavoritePlace) -> Unit,
    private val onRemoveFavorite: (FavoritePlace) -> Unit,
    private val onToggleNotification: (String, Boolean) -> Unit
) : RecyclerView.Adapter<MyFavoritesAdapter.FavoriteViewHolder>() {

    // 🔔 Supabase 기준 알림 ON인 장소 목록
    private val notificationPlaceIds: MutableSet<String> = mutableSetOf()

    fun updateNotificationPlaces(newSet: Set<String>) {
        notificationPlaceIds.clear()
        notificationPlaceIds.addAll(newSet)
        notifyDataSetChanged()
    }

    inner class FavoriteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val placeNameTextView: TextView = itemView.findViewById(R.id.tvPlaceName)
        val addressTextView: TextView = itemView.findViewById(R.id.tvAddress)
        val decibelTextView: TextView = itemView.findViewById(R.id.tvDecibel)
        val statusBadgeTextView: TextView = itemView.findViewById(R.id.tvStatusBadge)
        val ratingTextView: TextView = itemView.findViewById(R.id.tvRating)
        val reviewCountTextView: TextView = itemView.findViewById(R.id.tvReviewCount)
        val removeFavoriteButton: ImageView = itemView.findViewById(R.id.btnRemoveFavorite)
        val notificationStatusView: ImageView = itemView.findViewById(R.id.ivNotificationStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite_place, parent, false)
        return FavoriteViewHolder(view)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        val favorite = favoriteList[position]
        val context = holder.itemView.context

        // 기본 표시
        holder.placeNameTextView.text = favorite.placeName
        holder.addressTextView.text = favorite.address
        holder.decibelTextView.text = context.getString(
            R.string.my_review_noise_format, favorite.avgNoiseDb
        )
        holder.statusBadgeTextView.text = favorite.noiseStatus
        holder.ratingTextView.text = getStars(favorite.avgRating)
        holder.reviewCountTextView.text = "(${favorite.reviewCount} reviews)"

        updateStatusBadgeColor(context, holder.statusBadgeTextView, favorite.noiseStatus)

        // 🔔 알림 상태 표시
        val isNotificationOn = notificationPlaceIds.contains(favorite.kakaoPlaceId)
        updateNotificationIcon(context, holder.notificationStatusView, isNotificationOn)

        // 🔔 클릭 시 Activity에게 Supabase 업데이트 요청
        holder.notificationStatusView.setOnClickListener {
            val newState = !isNotificationOn

            // Activity 에게 Supabase 업데이트 요청
            onToggleNotification(favorite.kakaoPlaceId, newState)

            // 즉시 UI 업데이트
            if (newState) {
                notificationPlaceIds.add(favorite.kakaoPlaceId)
            } else {
                notificationPlaceIds.remove(favorite.kakaoPlaceId)
            }
            updateNotificationIcon(context, holder.notificationStatusView, newState)
        }

        holder.removeFavoriteButton.setOnClickListener { onRemoveFavorite(favorite) }
        holder.itemView.setOnClickListener { onPlaceClick(favorite) }
    }

    override fun getItemCount(): Int = favoriteList.size

    fun updateFavorites(newFavorites: List<FavoritePlace>) {
        favoriteList = newFavorites
        notifyDataSetChanged()
    }

    private fun getStars(rating: Double): String {
        val clampedRating = rating.coerceIn(0.0, 5.0).toInt()
        return "★".repeat(clampedRating) + "☆".repeat(5 - clampedRating)
    }

    private fun updateStatusBadgeColor(context: Context, badgeView: TextView, status: String) {
        val colorResId = when (status) {
            "Optimal" -> R.color.filter_indicator_optimal
            "Good" -> R.color.filter_indicator_good
            "Normal" -> R.color.filter_indicator_normal
            "Loud" -> R.color.filter_indicator_loud
            else -> R.color.grey_dark
        }

        ContextCompat.getDrawable(context, R.drawable.badge_shape)?.let { drawable ->
            val wrapped = DrawableCompat.wrap(drawable).mutate()
            DrawableCompat.setTint(wrapped, ContextCompat.getColor(context, colorResId))
            badgeView.background = wrapped
        }
    }

    private fun updateNotificationIcon(context: Context, iconView: ImageView, isOn: Boolean) {
        iconView.setImageResource(R.drawable.ic_notifications)

        val colorResId =
            if (isOn) R.color.primary_purple
            else R.color.grey

        iconView.setColorFilter(
            ContextCompat.getColor(context, colorResId),
            android.graphics.PorterDuff.Mode.SRC_IN
        )
    }
}