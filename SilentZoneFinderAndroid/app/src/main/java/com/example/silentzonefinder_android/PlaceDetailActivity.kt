package com.example.silentzonefinder_android

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.silentzonefinder_android.adapter.ReviewAdapter
import com.example.silentzonefinder_android.adapter.ReviewUiModel
import com.example.silentzonefinder_android.databinding.ActivityPlaceDetailBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class PlaceDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaceDetailBinding
    private val reviewAdapter = ReviewAdapter()
    private var currentPlaceId: String = ""
    private var currentPlaceName: String = ""
    private var currentAddress: String = ""
    private var currentLat: Double? = null
    private var currentLng: Double? = null
    private var allReviewUiModels: List<ReviewUiModel> = emptyList()
    private var currentReviewFilter: ReviewFilter = ReviewFilter.ALL
    private var currentUserId: String? = null
    private var isFavorite: Boolean = false
    private var isFavoriteLoading: Boolean = false

    private val newReviewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // 리뷰가 성공적으로 저장되었으므로 리뷰 목록 갱신
            loadReviews(currentPlaceId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentPlaceId = intent.getStringExtra(EXTRA_PLACE_ID) ?: ""
        if (currentPlaceId.isBlank()) {
            finish()
            return
        }

        currentPlaceName = intent.getStringExtra(EXTRA_PLACE_NAME).orEmpty()
        currentAddress = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
        val category = intent.getStringExtra(EXTRA_CATEGORY).orEmpty()
        currentLat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN).takeIf { !it.isNaN() }
        currentLng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN).takeIf { !it.isNaN() }

        setupPlaceInfo(currentPlaceName, currentAddress, category)
        setupReviewsList()
        setupNewReviewButton()
        setupHeaderControls()
        setupReviewFilters()
        loadFavoriteStatus()
        loadReviews(currentPlaceId)
    }

    private fun setupHeaderControls() = with(binding) {
        backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        favoriteButton.setOnClickListener {
            toggleFavorite()
        }
        notificationButton.setOnClickListener {
            Toast.makeText(this@PlaceDetailActivity, "알림 기능 준비 중입니다.", Toast.LENGTH_SHORT).show()
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

    private fun setupReviewFilters() = with(binding) {
        reviewFilterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val selectedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            currentReviewFilter = when (selectedId) {
                R.id.chipFilterOptimal -> ReviewFilter.OPTIMAL
                R.id.chipFilterGood -> ReviewFilter.GOOD
                R.id.chipFilterNormal -> ReviewFilter.NORMAL
                R.id.chipFilterLoud -> ReviewFilter.LOUD
                else -> ReviewFilter.ALL
            }
            updateReviewListForCurrentFilter()
        }
    }

    private fun setupNewReviewButton() {
        // FAB 버튼 클릭 시 리뷰 작성 화면으로 이동
        binding.fabNewReview.setOnClickListener {
            openNewReviewActivity()
        }
        
        // 리뷰 섹션 헤더의 "리뷰 작성하기" 버튼
        binding.btnWriteReview.setOnClickListener {
            openNewReviewActivity()
        }
        
        // 빈 리뷰 뷰의 "리뷰 작성하기" 버튼
        binding.btnWriteReviewFromEmpty.setOnClickListener {
            openNewReviewActivity()
        }
    }

    private fun openNewReviewActivity() {
        val intent = NewReviewActivity.createIntent(
            context = this,
            kakaoPlaceId = currentPlaceId,
            placeName = currentPlaceName,
            address = currentAddress,
            lat = currentLat,
            lng = currentLng
        )
        newReviewLauncher.launch(intent)
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
                allReviewUiModels = uiModels
                updateSummary(allReviewUiModels)
                updateNoiseTrendChart(allReviewUiModels)
                updateReviewListForCurrentFilter()

                Log.d(TAG, "Displaying ${uiModels.size} total reviews in RecyclerView")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load reviews", e)
                allReviewUiModels = emptyList()
                reviewAdapter.submitList(emptyList())
                binding.reviewsSectionHeader.isVisible = false
                binding.reviewFilterGroup.isVisible = false
                binding.reviewsRecyclerView.isVisible = false
                binding.emptyView.isVisible = true
                binding.emptyMessageText.text = getString(R.string.place_detail_error_loading_reviews)
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

    private fun updateReviewListForCurrentFilter() {
        val hasAnyReviews = allReviewUiModels.isNotEmpty()
        val filtered = when (currentReviewFilter) {
            ReviewFilter.ALL -> allReviewUiModels
            ReviewFilter.OPTIMAL -> allReviewUiModels.filter { it.noiseLevelDb in 0.0..45.0 }
            ReviewFilter.GOOD -> allReviewUiModels.filter { it.noiseLevelDb in 45.0..55.0 }
            ReviewFilter.NORMAL -> allReviewUiModels.filter { it.noiseLevelDb in 55.0..65.0 }
            ReviewFilter.LOUD -> allReviewUiModels.filter { it.noiseLevelDb > 65.0 }
        }

        reviewAdapter.submitList(filtered)

        val showFilteredEmptyState = hasAnyReviews && filtered.isEmpty()
        binding.reviewsRecyclerView.isVisible = filtered.isNotEmpty()
        binding.reviewsSectionHeader.isVisible = hasAnyReviews
        binding.reviewFilterGroup.isVisible = hasAnyReviews

        binding.emptyView.isVisible = !hasAnyReviews || showFilteredEmptyState
        binding.emptyMessageText.text = when {
            !hasAnyReviews -> getString(R.string.place_detail_empty_reviews)
            showFilteredEmptyState -> getString(R.string.place_detail_empty_reviews_filtered)
            else -> ""
        }
    }

    private fun updateFavoriteButtonIcon() {
        if (isFavorite) {
            // 즐겨찾기 상태: 채워진 하트 아이콘 사용
            binding.favoriteButton.setImageResource(R.drawable.ic_favorite)
            binding.favoriteButton.imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.primary_purple)
            )
            binding.favoriteButton.contentDescription = getString(R.string.place_detail_favorite_remove)
        } else {
            // 즐겨찾기 아님: 빈 하트 아이콘 사용
            binding.favoriteButton.setImageResource(R.drawable.ic_favorite_outline)
            binding.favoriteButton.imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.primary_purple)
            )
            binding.favoriteButton.contentDescription = getString(R.string.place_detail_favorite_add)
        }
    }

    private fun loadFavoriteStatus() {
        lifecycleScope.launch {
            isFavoriteLoading = true
            try {
                currentUserId = withContext(Dispatchers.IO) {
                    SupabaseManager.client.auth.currentSessionOrNull()?.user?.id?.toString()
                }
                val userId = currentUserId
                if (userId == null) {
                    updateFavoriteButtonIcon()
                    return@launch
                }

                val favorites = withContext(Dispatchers.IO) {
                    SupabaseManager.client.postgrest["favorites"]
                        .select {
                            filter {
                                eq("user_id", userId)
                                eq("kaka_place_id", currentPlaceId)
                            }
                        }
                        .decodeList<FavoriteDto>()
                }
                isFavorite = favorites.isNotEmpty()
                updateFavoriteButtonIcon()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load favorite status", e)
                Toast.makeText(
                    this@PlaceDetailActivity,
                    getString(R.string.place_detail_favorite_status_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isFavoriteLoading = false
            }
        }
    }

    private fun toggleFavorite() {
        if (isFavoriteLoading) return
        val userId = currentUserId
        if (userId == null) {
            Toast.makeText(
                this,
                getString(R.string.place_detail_login_required),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        lifecycleScope.launch {
            isFavoriteLoading = true
            try {
                if (isFavorite) {
                    withContext(Dispatchers.IO) {
                        SupabaseManager.client.postgrest["favorites"].delete {
                            filter {
                                eq("user_id", userId)
                                eq("kaka_place_id", currentPlaceId)
                            }
                        }
                    }
                    isFavorite = false
                    Toast.makeText(
                        this@PlaceDetailActivity,
                        getString(R.string.place_detail_favorite_removed),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    // 즐겨찾기 추가 전에 profiles 테이블에 사용자 레코드가 있는지 확인하고 없으면 생성
                    withContext(Dispatchers.IO) {
                        try {
                            // profiles 테이블에 사용자 레코드가 있는지 확인
                            val existingProfiles = SupabaseManager.client.postgrest["profiles"]
                                .select {
                                    filter {
                                        eq("id", userId)
                                    }
                                }
                                .decodeList<ProfileDto>()
                            
                            // 없으면 생성
                            if (existingProfiles.isEmpty()) {
                                SupabaseManager.client.postgrest["profiles"].insert(
                                    ProfileInsertDto(
                                        id = userId,
                                        nickname = null,
                                        avatarUrl = null
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            // profiles 조회/생성 실패해도 즐겨찾기 추가는 계속 진행
                            // (이미 존재하는 경우 등)
                            Log.d(TAG, "Profile check/creation failed, continuing with favorite", e)
                        }
                        
                        // 즐겨찾기 추가
                        SupabaseManager.client.postgrest["favorites"].insert(
                            FavoriteInsertDto(
                                userId = userId,
                                kakaoPlaceId = currentPlaceId
                            )
                        )
                    }
                    isFavorite = true
                    Toast.makeText(
                        this@PlaceDetailActivity,
                        getString(R.string.place_detail_favorite_added),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                updateFavoriteButtonIcon()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle favorite", e)
                Toast.makeText(
                    this@PlaceDetailActivity,
                    getString(R.string.place_detail_favorite_failed),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isFavoriteLoading = false
            }
        }
    }

    private fun updateNoiseTrendChart(reviews: List<ReviewUiModel>) {
        if (reviews.size < 2) {
            binding.noiseTrendChart.isVisible = false
            binding.noiseTrendEmptyLabel.isVisible = true
            binding.noiseTrendLatestValue.text = getString(R.string.place_detail_dash)
            binding.noiseTrendLatestTime.text = ""
            return
        }

        val chronologicalReviews = reviews.sortedBy { it.createdDate }
        val recent = chronologicalReviews.takeLast(12)
        val noiseValues = recent.map { it.noiseLevelDb }

        binding.noiseTrendChart.isVisible = true
        binding.noiseTrendEmptyLabel.isVisible = false
        binding.noiseTrendChart.setData(noiseValues)

        val latest = recent.last()
        binding.noiseTrendLatestValue.text =
            getString(R.string.place_detail_average_db_format, latest.noiseLevelDb)
        binding.noiseTrendLatestTime.text = latest.createdDate
    }

    private fun getNoiseStatus(db: Double): Pair<String, Int> = when (db) {
        in 0.0..45.0 -> getString(R.string.noise_status_optimal) to R.color.filter_indicator_optimal
        in 45.0..55.0 -> getString(R.string.noise_status_good) to R.color.filter_indicator_good
        in 55.0..65.0 -> getString(R.string.noise_status_normal) to R.color.filter_indicator_normal
        else -> getString(R.string.noise_status_loud) to R.color.filter_indicator_loud
    }

    private enum class ReviewFilter {
        ALL, OPTIMAL, GOOD, NORMAL, LOUD
    }

    @Serializable
    private data class FavoriteDto(
        @SerialName("user_id") val userId: String,
        @SerialName("kaka_place_id") val kakaoPlaceId: String
    )

    @Serializable
    private data class FavoriteInsertDto(
        @SerialName("user_id") val userId: String,
        @SerialName("kaka_place_id") val kakaoPlaceId: String
    )
    
    @Serializable
    private data class ProfileDto(
        val id: String,
        val nickname: String? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null
    )
    
    @Serializable
    private data class ProfileInsertDto(
        val id: String,
        val nickname: String? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null
    )

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
        private const val EXTRA_LAT = "extra_lat"
        private const val EXTRA_LNG = "extra_lng"
        const val REQUEST_CODE_NEW_REVIEW = 1001

        fun createIntent(
            context: Context,
            placeId: String,
            placeName: String,
            address: String?,
            category: String?,
            lat: Double? = null,
            lng: Double? = null
        ): Intent {
            return Intent(context, PlaceDetailActivity::class.java).apply {
                putExtra(EXTRA_PLACE_ID, placeId)
                putExtra(EXTRA_PLACE_NAME, placeName)
                putExtra(EXTRA_ADDRESS, address)
                putExtra(EXTRA_CATEGORY, category)
                lat?.let { putExtra(EXTRA_LAT, it) }
                lng?.let { putExtra(EXTRA_LNG, it) }
            }
        }
    }
}

