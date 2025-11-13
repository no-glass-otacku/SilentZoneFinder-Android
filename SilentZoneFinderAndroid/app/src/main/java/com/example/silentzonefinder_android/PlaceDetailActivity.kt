package com.example.silentzonefinder_android

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.silentzonefinder_android.adapter.ReviewAdapter
import com.example.silentzonefinder_android.adapter.ReviewUiModel
import com.example.silentzonefinder_android.databinding.ActivityPlaceDetailBinding
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class PlaceDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaceDetailBinding
    private val reviewAdapter = ReviewAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val placeId = intent.getStringExtra(EXTRA_PLACE_ID)
        if (placeId.isNullOrBlank()) {
            finish()
            return
        }

        val placeName = intent.getStringExtra(EXTRA_PLACE_NAME).orEmpty()
        val address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
        val category = intent.getStringExtra(EXTRA_CATEGORY).orEmpty()

        setupToolbar(placeName)
        setupPlaceInfo(placeName, address, category)
        setupReviewsList()

        loadReviews(placeId)
    }

    private fun setupToolbar(placeName: String) {
        binding.toolbar.title = placeName
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupPlaceInfo(placeName: String, address: String, category: String) {
        binding.placeNameTextView.text = placeName
        binding.addressTextView.text = if (address.isBlank()) {
            getString(R.string.place_detail_address_placeholder)
        } else {
            address
        }
        binding.categoryTextView.text = if (category.isBlank()) {
            getString(R.string.place_detail_category_placeholder)
        } else {
            category
        }
    }

    private fun setupReviewsList() {
        binding.reviewsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PlaceDetailActivity)
            adapter = reviewAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun loadReviews(placeId: String) {
        lifecycleScope.launch {
            binding.progressBar.isVisible = true
            try {
                Log.d(TAG, "Loading reviews for placeId=$placeId")
                val reviews = withContext(Dispatchers.IO) {
                    SupabaseManager.client.postgrest["reviews"]
                        .select()
                        .decodeList<ReviewDto>()
                        .filter { it.kakaoPlaceId == placeId }
                        .sortedByDescending { it.createdAt }
                }
                Log.d(TAG, "Fetched ${reviews.size} reviews for placeId=$placeId")

                val uiModels = reviews.map { review -> review.toUiModel() }
                reviewAdapter.submitList(uiModels)
                updateSummary(uiModels)

                binding.emptyView.isVisible = uiModels.isEmpty()
                binding.reviewsRecyclerView.isVisible = uiModels.isNotEmpty()
                binding.reviewsSectionTitle.isVisible = uiModels.isNotEmpty()
                if (uiModels.isEmpty()) {
                    Log.d(TAG, "No reviews matched placeId=$placeId")
                } else {
                    Log.d(TAG, "Displaying ${uiModels.size} reviews in RecyclerView")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load reviews", e)
                binding.emptyView.isVisible = true
                binding.reviewsRecyclerView.isVisible = false
                binding.emptyView.text = getString(R.string.place_detail_error_loading_reviews)
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    private fun updateSummary(reviews: List<ReviewUiModel>) {
        if (reviews.isEmpty()) {
            binding.averageDbTextView.text = getString(R.string.place_detail_dash)
            binding.averageRatingTextView.text = getString(R.string.place_detail_dash)
            binding.reviewCountTextView.text = getString(R.string.place_detail_review_count_format, 0)
            binding.noiseStatusBadge.text = getString(R.string.place_detail_status_unknown)
            (binding.noiseStatusBadge.background?.mutate() as? GradientDrawable)?.setColor(
                ContextCompat.getColor(this, R.color.grey)
            )
            return
        }

        val avgDb = reviews.map { it.noiseLevelDb }.average()
        val avgRating = reviews.map { it.rating }.average()
        val reviewCount = reviews.size

        binding.averageDbTextView.text = getString(R.string.place_detail_average_db_format, avgDb)
        binding.averageRatingTextView.text = getString(R.string.place_detail_average_rating_format, avgRating)
        binding.reviewCountTextView.text = getString(R.string.place_detail_review_count_format, reviewCount)

        val (statusText, colorRes) = getNoiseStatus(avgDb)
        binding.noiseStatusBadge.text = statusText
        (binding.noiseStatusBadge.background?.mutate() as? GradientDrawable)?.setColor(
            ContextCompat.getColor(this, colorRes)
        )
    }

    private fun getNoiseStatus(db: Double): Pair<String, Int> = when (db) {
        in 0.0..45.0 -> getString(R.string.noise_status_optimal) to R.color.filter_indicator_optimal
        in 45.0..55.0 -> getString(R.string.noise_status_good) to R.color.filter_indicator_good
        in 55.0..65.0 -> getString(R.string.noise_status_normal) to R.color.filter_indicator_normal
        else -> getString(R.string.noise_status_loud) to R.color.filter_indicator_loud
    }

    @Serializable
    private data class ReviewDto(
        val id: Int,
        @SerialName("kakao_place_id") val kakaoPlaceId: String,
        val rating: Int,
        val text: String,
        val images: List<String>? = null,
        @SerialName("noise_level_db") val noiseLevelDb: Double,
        @SerialName("created_at") val createdAt: String,
        @SerialName("user_id") val userId: String? = null,
        val amenities: List<String>? = null
    ) {
        fun toUiModel(): ReviewUiModel {
            val displayDate = createdAt.takeIf { it.length >= 10 }?.substring(0, 10).orEmpty()
            return ReviewUiModel(
                id = id,
                rating = rating,
                text = text,
                noiseLevelDb = noiseLevelDb,
                createdDate = displayDate,
                amenities = amenities ?: emptyList()
            )
        }
    }

    companion object {
        private const val TAG = "PlaceDetailActivity"
        private const val EXTRA_PLACE_ID = "extra_place_id"
        private const val EXTRA_PLACE_NAME = "extra_place_name"
        private const val EXTRA_ADDRESS = "extra_address"
        private const val EXTRA_CATEGORY = "extra_category"

        fun createIntent(
            context: Context,
            placeId: String,
            placeName: String,
            address: String?,
            category: String?
        ): Intent {
            return Intent(context, PlaceDetailActivity::class.java).apply {
                putExtra(EXTRA_PLACE_ID, placeId)
                putExtra(EXTRA_PLACE_NAME, placeName)
                putExtra(EXTRA_ADDRESS, address)
                putExtra(EXTRA_CATEGORY, category)
            }
        }
    }
}

