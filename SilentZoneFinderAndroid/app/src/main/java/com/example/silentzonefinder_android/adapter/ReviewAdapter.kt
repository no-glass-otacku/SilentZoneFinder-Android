package com.example.silentzonefinder_android.adapter

// com.example.silentzonefinder_android.adapter.ReviewAdapter.kt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.silentzonefinder_android.R
import com.example.silentzonefinder_android.data.Review
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

class ReviewAdapter(private var reviewList: List<Review>) :
    RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder>() {

    // 1. ViewHolder (item_review.xml의 View들을 미리 저장)
    inner class ReviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // XML View 요소들을 findViewById()로 연결
        val tvPlaceName: TextView = itemView.findViewById(R.id.tvPlaceName)
        val tvDecibel: TextView = itemView.findViewById(R.id.tvDecibel)
        val tvStatusBadge: TextView = itemView.findViewById(R.id.tvStatusBadge)
        val tvRating: TextView = itemView.findViewById(R.id.tvRating)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvReviewText: TextView = itemView.findViewById(R.id.tvReviewText)
        val amenityTagsLayout: LinearLayout = itemView.findViewById(R.id.amenityTagsLayout)
    }

    // 2. onCreateViewHolder (ViewHolder 상자를 만듭니다)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        // item_review.xml을 읽어와서 View 객체로 만듭니다.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_review, parent, false)
        return ReviewViewHolder(view)
    }

    // 3. onBindViewHolder (데이터를 View에 채웁니다)
    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val currentReview = reviewList[position] // 현재 위치의 리뷰 데이터를 가져옵니다.
        val context = holder.itemView.context

        // 데이터 설정
        holder.tvPlaceName.text = currentReview.placeName
        holder.tvDecibel.text = "${currentReview.decibel} dB"
        holder.tvStatusBadge.text = currentReview.status

        // 평점 (별표)
        val ratingStars = getStars(currentReview.rating)
        holder.tvRating.text = "$ratingStars"

        holder.tvDate.text = "• ${currentReview.date}"

        holder.tvReviewText.text = currentReview.reviewText

        // --- 태그 동적 생성 ---
        // 1. 기존 태그 초기화 (재활용을 위해 중요)
        holder.amenityTagsLayout.removeAllViews()

        // 2. amenityTags 리스트를 돌며 태그 TextView를 생성
        currentReview.amenities.forEach { amenity ->
            val tagView = TextView(context).apply {
                text = amenity
                textSize = 12f

                // 드로어블 리소스인 rounded_shape_bg.xml을 배경으로 설정
                background = ContextCompat.getDrawable(context, R.drawable.rounded_shape_bg)

                // 패딩 설정 (dp 값을 픽셀 값으로 변환하는 함수를 사용해야 하지만, 간단하게 하드코딩)
                setPadding(dpToPx(context, 8), dpToPx(context, 4), dpToPx(context, 8), dpToPx(context, 4))
            }
            // LayoutParams 설정 (태그 간 간격)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(context, 8) // 태그 사이에 8dp 간격
            }
            tagView.layoutParams = params

            // LinearLayout에 태그 추가
            holder.amenityTagsLayout.addView(tagView)
        }
        // -------------------------------------------------------------------

        // 참고: 상태 배지 색상 변경
        // 1. 상태에 맞는 색상 ID(R.color....)를 가져옵니다.
        val colorResId = when (currentReview.status) {
            "Library Quiet" -> R.color.library_quiet_color
            "Quiet Conversation" -> R.color.quiet_conversation_color
            "Lively Chatter" -> R.color.lively_chatter_color
            "High Traffic" -> R.color.high_traffic_color
            else -> R.color.black // 기본 색상 설정
        }

        // 2. badge_shape.xml 드로어블 객체를 가져옵니다.
        val backgroundDrawable = ContextCompat.getDrawable(context, R.drawable.badge_shape)

        // 3. 드로어블에 색상을 입힙니다.
        if (backgroundDrawable != null) {
            val wrappedDrawable = DrawableCompat.wrap(backgroundDrawable).mutate()
            val color = ContextCompat.getColor(context, colorResId)
            DrawableCompat.setTint(wrappedDrawable, color)

            // 4. 배경으로 설정합니다.
            holder.tvStatusBadge.background = wrappedDrawable
        }
    }

    // 4. getItemCount (총 데이터 개수)
    override fun getItemCount(): Int {
        return reviewList.size
    }

    fun updateReviews(newReviews: List<Review>) {
        // 정렬 전 리뷰 리스트(this.reviewList)를 정렬 후 리뷰 리스트(newReviews)로 교체합니다.
        this.reviewList = newReviews
        // 2. ReviewAdapter가 RecyclerView(전광판)에게 "바뀌었으니까 화면 싹 지우고 새로 그려!" 라고 신호를 보냅니다.
        notifyDataSetChanged()
    }

    // --- 헬퍼 함수 ---
    private fun getStars(rating: Int): String {
        return "★".repeat(rating) + "☆".repeat(5 - rating) // 5점 만점 기준 별표 생성
    }

    private fun dpToPx(context: android.content.Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}