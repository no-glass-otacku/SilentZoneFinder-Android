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
    private val onRemoveFavorite: (FavoritePlace) -> Unit
) : RecyclerView.Adapter<MyFavoritesAdapter.FavoriteViewHolder>() {

    inner class FavoriteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val placeNameTextView: TextView = itemView.findViewById(R.id.tvPlaceName)
        val addressTextView: TextView = itemView.findViewById(R.id.tvAddress)
        val decibelTextView: TextView = itemView.findViewById(R.id.tvDecibel)
        val statusBadgeTextView: TextView = itemView.findViewById(R.id.tvStatusBadge)
        val ratingTextView: TextView = itemView.findViewById(R.id.tvRating)
        val reviewCountTextView: TextView = itemView.findViewById(R.id.tvReviewCount)
        val removeFavoriteButton: ImageView = itemView.findViewById(R.id.btnRemoveFavorite)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite_place, parent, false)
        return FavoriteViewHolder(view)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        val favorite = favoriteList[position]
        val context = holder.itemView.context

        holder.placeNameTextView.text = favorite.placeName
        holder.addressTextView.text = favorite.address
        holder.decibelTextView.text = context.getString(
            R.string.my_review_noise_format,
            favorite.avgNoiseDb
        )
        holder.statusBadgeTextView.text = favorite.noiseStatus
        holder.ratingTextView.text = getStars(favorite.avgRating)
        holder.reviewCountTextView.text = when (favorite.reviewCount) {
            0 -> "(0 reviews)"
            1 -> "(1 review)"
            else -> "(${favorite.reviewCount} reviews)"
        }

        updateStatusBadgeColor(context, holder.statusBadgeTextView, favorite.noiseStatus)

        holder.itemView.setOnClickListener { onPlaceClick(favorite) }
        holder.removeFavoriteButton.setOnClickListener { onRemoveFavorite(favorite) }
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
}

