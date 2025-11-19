package com.example.silentzonefinder_android.adapter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.silentzonefinder_android.R
import com.example.silentzonefinder_android.databinding.ItemReviewBinding
import kotlin.math.roundToInt

data class ReviewUiModel(
    val id: Int,
    val rating: Int,
    val text: String,
    val noiseLevelDb: Double,
    val createdDate: String,
    val amenities: List<String> = emptyList()
)

class ReviewAdapter :
    ListAdapter<ReviewUiModel, ReviewAdapter.ReviewViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemReviewBinding.inflate(inflater, parent, false)
        return ReviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ReviewViewHolder(
        private val binding: ItemReviewBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ReviewUiModel) {
            setupRatingStars(binding.ratingStarsLayout, item.rating)
            binding.noiseDbTextView.text = "${item.noiseLevelDb.roundToInt()} dB"
            
            val (statusText, statusColorRes) = getNoiseStatus(item.noiseLevelDb)
            binding.noiseStatusBadge.text = statusText
            val backgroundDrawable = ContextCompat.getDrawable(binding.root.context, R.drawable.bg_status_badge)
            if (backgroundDrawable is GradientDrawable) {
                backgroundDrawable.setColor(ContextCompat.getColor(binding.root.context, statusColorRes))
                binding.noiseStatusBadge.background = backgroundDrawable
            }
            
            binding.reviewTextView.text = item.text
            binding.dateTextView.text = item.createdDate
            
            setupAmenities(binding.amenitiesLayout, item.amenities)
        }

        private fun setupRatingStars(layout: LinearLayout, rating: Int) {
            layout.removeAllViews()
            val context = layout.context
            val starSize = context.resources.getDimensionPixelSize(R.dimen.star_size)
            
            for (i in 1..5) {
                val imageView = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(starSize, starSize).apply {
                        marginEnd = if (i < 5) 4.dpToPx(context) else 0
                    }
                    setImageResource(if (i <= rating) R.drawable.ic_star_filled else R.drawable.ic_star_empty)
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                }
                layout.addView(imageView)
            }
        }

        private fun setupAmenities(layout: LinearLayout, amenities: List<String>) {
            layout.removeAllViews()
            if (amenities.isEmpty()) {
                layout.visibility = View.GONE
                return
            }
            
            layout.visibility = View.VISIBLE
            val context = layout.context
            
            amenities.forEach { amenity ->
                val chip = TextView(context).apply {
                    text = amenity
                    textSize = 11f
                    setPadding(8.dpToPx(context), 4.dpToPx(context), 8.dpToPx(context), 4.dpToPx(context))
                    background = ContextCompat.getDrawable(context, R.drawable.rounded_shape_bg)
                    setTextColor(ContextCompat.getColor(context, R.color.grey_dark))
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 6.dpToPx(context)
                }
                chip.layoutParams = params
                layout.addView(chip)
            }
        }

        private fun getNoiseStatus(db: Double): Pair<String, Int> = when (db) {
            in 0.0..45.0 -> "Optimal" to R.color.filter_indicator_optimal
            in 45.0..55.0 -> "Good" to R.color.filter_indicator_good
            in 55.0..65.0 -> "Normal" to R.color.filter_indicator_normal
            else -> "Loud" to R.color.filter_indicator_loud
        }

        private fun Int.dpToPx(context: android.content.Context): Int {
            return (this * context.resources.displayMetrics.density).toInt()
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ReviewUiModel>() {
        override fun areItemsTheSame(oldItem: ReviewUiModel, newItem: ReviewUiModel): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ReviewUiModel, newItem: ReviewUiModel): Boolean =
            oldItem == newItem
    }
}