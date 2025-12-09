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
import com.example.silentzonefinder_android.data.ReviewDto
import kotlinx.coroutines.delay

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
    private var isNotificationOn: Boolean = false
    private var alertThresholdDb: Double = 50.0   // 기본값 50 dB (원하면 바꿔도 됨)


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

        lifecycleScope.launch {
            // userId 로딩이 완료될 때까지 잠시 대기
            delay(150)   // 100~200ms 정도면 충분 (UI 블로킹X)
            loadNotificationStatus()
        }

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
            if (!isFavorite) {
                Toast.makeText(
                    this@PlaceDetailActivity,
                    getString(R.string.place_detail_favorite_required_for_notification),
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            val wasOn = isNotificationOn
            toggleNotification { isNowOn ->
                if (isNowOn && !wasOn) {
                    Toast.makeText(
                        this@PlaceDetailActivity,
                        getString(R.string.notification_enabled_message),
                        Toast.LENGTH_SHORT
                    ).show()
                    openNoiseThresholdSettings()
                } else if (!isNowOn && wasOn) {
                    Toast.makeText(
                        this@PlaceDetailActivity,
                        getString(R.string.notification_disabled_message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
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

    private fun openNoiseThresholdSettings() {
        val intent = Intent(this@PlaceDetailActivity, NoiseThresholdActivity::class.java)
        startActivity(intent)
    }

    private fun ReviewDto.toUiModel(): ReviewUiModel {
        val displayDate = createdAt
            ?.takeIf { it.length >= 10 }
            ?.substring(0, 10)
            .orEmpty()

        return ReviewUiModel(
            id = id,
            rating = rating,
            text = text ?: "",
            noiseLevelDb = noiseLevelDb,
            createdDate = displayDate,
            amenities = amenities ?: emptyList(),
            images = images ?: emptyList()
        )
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

    private fun updateNotificationButtonIcon() {
        if (isNotificationOn) {
            // 알림 ON: 채워진 느낌의 알림 아이콘 사용 (이미 있는 ic_notifications)
            binding.notificationButton.setImageResource(R.drawable.ic_notifications)
            binding.notificationButton.imageTintList =
                android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this, R.color.primary_purple)
                )
        } else {
            // 알림 OFF: 기존 벨 아이콘 + 회색
            binding.notificationButton.setImageResource(R.drawable.ic_bell)
            binding.notificationButton.imageTintList =
                android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this, R.color.grey)
                )
        }

        // 접근성 텍스트는 하나만 공통으로 사용
        binding.notificationButton.contentDescription =
            getString(R.string.place_detail_notification)
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
                                eq("kakao_place_id", currentPlaceId)
                            }
                        }
                        .decodeList<FavoriteDto>()
                }
                isFavorite = favorites.isNotEmpty()
                // ★ 임계값 로딩: 있으면 그 값 쓰고, 없으면 기본값 유지
                favorites.firstOrNull()?.alertThresholdDb?.let { threshold ->
                    alertThresholdDb = threshold
                }
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

    private fun loadNotificationStatus() {
        lifecycleScope.launch {
            // 1) 세션에서 사용자 ID 가져오기
            val userId = withContext(Dispatchers.IO) {
                // 이미 값 있으면 그거 쓰고, 없으면 Supabase 세션에서 읽기
                currentUserId ?: SupabaseManager.client.auth.currentSessionOrNull()
                    ?.user?.id
                    ?.toString()
            }

            if (userId == null) {
                // 로그인 안 되어 있으면 알림은 기본적으로 OFF
                isNotificationOn = false
                updateNotificationButtonIcon()
                return@launch
            }

            // 전역 변수도 최신 값으로 유지
            currentUserId = userId

            try {
                // 2) place_notifications 에서 현재 장소 알림 상태 조회
                val notifications = withContext(Dispatchers.IO) {
                    SupabaseManager.client.postgrest["place_notifications"]
                        .select {
                            filter {
                                eq("user_id", userId)
                                eq("kakao_place_id", currentPlaceId)
                            }
                        }
                        .decodeList<NotificationDto>()
                }

                // 3) 하나라도 is_enabled(또는 isEnabled)가 true이면 ON
                isNotificationOn = notifications.any { it.isEnabled }
            } catch (e: Exception) {
                Log.e("PlaceDetailActivity", "Failed to load notification status", e)
                isNotificationOn = false
            }

            // 4) 버튼 아이콘 갱신
            updateNotificationButtonIcon()
        }
    }



    private fun toggleNotification(onComplete: ((Boolean) -> Unit)? = null) {
        val userId = currentUserId ?: run {
            Toast.makeText(
                this,
                getString(R.string.place_detail_login_required),
                Toast.LENGTH_LONG
            ).show()
            onComplete?.invoke(isNotificationOn)
            return
        }
        // ★ 즐겨찾기 안 한 상태면 여기서도 바로 차단
        if (!isFavorite) {
            Toast.makeText(
                this,
                getString(R.string.place_detail_favorite_required_for_notification),
                Toast.LENGTH_LONG
            ).show()
            onComplete?.invoke(isNotificationOn)
            return
        }

        lifecycleScope.launch {
            try {
                val newState = !isNotificationOn

                withContext(Dispatchers.IO) {
                    val table = SupabaseManager.client.postgrest["place_notifications"]

                    if (newState) {
                        // ★ 0-1) 즐겨찾기(favorites)에 임계값 포함해서 upsert
                        SupabaseManager.client.postgrest["favorites"].upsert(
                            FavoriteInsertDto(
                                userId = userId,
                                kakaoPlaceId = currentPlaceId,
                                alertThresholdDb = 65.0  // 현재 메모리 값 사용
                            )
                        )

                        // ★ 0-2) 즐겨찾기 상태도 true 로 맞춰주기 (UI 반영)
                        isFavorite = true

                        // 1) place_notifications row 존재 여부 확인
                        val existing = table.select {
                            filter {
                                eq("user_id", userId)
                                eq("kakao_place_id", currentPlaceId)
                            }
                        }.decodeList<NotificationDto>()

                        if (existing.isEmpty()) {
                            table.insert(
                                NotificationInsertDto(
                                    userId = userId,
                                    kakaoPlaceId = currentPlaceId,
                                    isEnabled = true
                                )
                            )
                        } else {
                            table.update(
                                {
                                    set("is_enabled", true)
                                }
                            ) {
                                filter {
                                    eq("user_id", userId)
                                    eq("kakao_place_id", currentPlaceId)
                                }
                            }
                        }
                    } else {
                        // 🔕 OFF: place_notifications 에서 is_enabled = false
                        table.update(
                            {
                                set("is_enabled", false)
                            }
                        ) {
                            filter {
                                eq("user_id", userId)
                                eq("kakao_place_id", currentPlaceId)
                            }
                        }
                    }
                }


                isNotificationOn = newState
                updateNotificationButtonIcon()
                onComplete?.invoke(newState)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle notification", e)
                Toast.makeText(
                    this@PlaceDetailActivity,
                    "알림 설정 변경에 실패했어요.",
                    Toast.LENGTH_SHORT
                ).show()
                onComplete?.invoke(isNotificationOn)
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
                                eq("kakao_place_id", currentPlaceId)
                                alertThresholdDb = alertThresholdDb   // ★ 추가
                            }
                        }
                    }
                    isFavorite = false
                    isNotificationOn = false
                    Toast.makeText(
                        this@PlaceDetailActivity,
                        getString(R.string.place_detail_favorite_removed),
                        Toast.LENGTH_SHORT
                    ).show()
                    withContext(Dispatchers.IO) {
                        SupabaseManager.client.postgrest["place_notifications"].delete {
                            filter {
                                eq("user_id", userId)
                                eq("kakao_place_id", currentPlaceId)
                            }
                        }
                    }
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
                    isNotificationOn = true
                    Toast.makeText(
                        this@PlaceDetailActivity,
                        getString(R.string.place_detail_favorite_added),
                        Toast.LENGTH_SHORT
                    ).show()
                    // 알림 테이블에도 ON 저장
                    withContext(Dispatchers.IO) {
                        SupabaseManager.client.postgrest["place_notifications"].upsert(
                            NotificationInsertDto(
                                userId = userId,
                                kakaoPlaceId = currentPlaceId,
                                isEnabled = true
                            )
                        )
                    }
                }
                updateFavoriteButtonIcon()
                updateNotificationButtonIcon()
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
    data class NotificationDto(
        @SerialName("user_id") val userId: String,
        @SerialName("kakao_place_id") val kakaoPlaceId: String,
        @SerialName("is_enabled") val isEnabled: Boolean
    )

    @Serializable
    data class NotificationInsertDto(
        @SerialName("user_id") val userId: String,
        @SerialName("kakao_place_id") val kakaoPlaceId: String,
        @SerialName("is_enabled") val isEnabled: Boolean = true
    )

    @Serializable
    private data class FavoriteDto(
        @SerialName("user_id") val userId: String,
        @SerialName("kakao_place_id") val kakaoPlaceId: String,
        @SerialName("alert_threshold_db") val alertThresholdDb: Double? = null,
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    private data class FavoriteInsertDto(
        @SerialName("user_id") val userId: String,
        @SerialName("kakao_place_id") val kakaoPlaceId: String,
        @SerialName("alert_threshold_db") val alertThresholdDb: Double? = null
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

