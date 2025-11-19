package com.example.silentzonefinder_android.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.silentzonefinder_android.R
import com.example.silentzonefinder_android.data.Review

class MyReviewsAdapter(
    private var reviewList: List<Review>,
    private val onReviewClick: (Review) -> Unit,
    private val onDeleteClick: (Review) -> Unit
) : RecyclerView.Adapter<MyReviewsAdapter.ReviewViewHolder>() {

    inner class ReviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val placeNameTextView: TextView = itemView.findViewById(R.id.tvPlaceName)
        val decibelTextView: TextView = itemView.findViewById(R.id.tvDecibel)
        val statusBadgeTextView: TextView = itemView.findViewById(R.id.tvStatusBadge)
        val ratingTextView: TextView = itemView.findViewById(R.id.tvRating)
        val dateTextView: TextView = itemView.findViewById(R.id.tvDate)
        val reviewTextView: TextView = itemView.findViewById(R.id.tvReviewText)
        val amenityTagsLayout: LinearLayout = itemView.findViewById(R.id.amenityTagsLayout)
        val deleteButton: View = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_my_review, parent, false)
        return ReviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = reviewList[position]
        val context = holder.itemView.context

        holder.placeNameTextView.text = review.placeName
        holder.decibelTextView.text = context.getString(
            R.string.my_review_noise_format,
            review.decibel
        )
        holder.statusBadgeTextView.text = review.status
        holder.ratingTextView.text = getStars(review.rating)
        holder.dateTextView.text = review.date
        holder.reviewTextView.text = review.reviewText

        updateStatusBadgeColor(context, holder.statusBadgeTextView, review.status)
        renderAmenityTags(context, holder.amenityTagsLayout, review.amenities)

        holder.itemView.setOnClickListener { onReviewClick(review) }
        holder.deleteButton.setOnClickListener { onDeleteClick(review) }
    }

    override fun getItemCount(): Int = reviewList.size

    fun updateReviews(newReviews: List<Review>) {
        reviewList = newReviews
        notifyDataSetChanged()
    }

    private fun getStars(rating: Int): String {
        val clampedRating = rating.coerceIn(0, 5)
        return "★".repeat(clampedRating) + "☆".repeat(5 - clampedRating)
    }

    private fun updateStatusBadgeColor(context: Context, badgeView: TextView, status: String) {
        val colorResId = when (status) {
            "Library Quiet" -> R.color.library_quiet_color
            "Quiet Conversation" -> R.color.quiet_conversation_color
            "Lively Chatter" -> R.color.lively_chatter_color
            "High Traffic" -> R.color.high_traffic_color
            else -> R.color.grey_dark
        }

        ContextCompat.getDrawable(context, R.drawable.badge_shape)?.let { drawable ->
            val wrapped = DrawableCompat.wrap(drawable).mutate()
            DrawableCompat.setTint(wrapped, ContextCompat.getColor(context, colorResId))
            badgeView.background = wrapped
        }
    }

    private fun renderAmenityTags(
        context: Context,
        container: LinearLayout,
        amenities: List<String>
    ) {
        container.removeAllViews()

        amenities.forEach { amenity ->
            val tagView = TextView(context).apply {
                text = amenity
                textSize = 12f
                setPadding(
                    dpToPx(context, 8),
                    dpToPx(context, 4),
                    dpToPx(context, 8),
                    dpToPx(context, 4)
                )
                background = ContextCompat.getDrawable(context, R.drawable.rounded_shape_bg)
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(context, 8)
                bottomMargin = dpToPx(context, 4)
            }
            tagView.layoutParams = params

            container.addView(tagView)
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}

