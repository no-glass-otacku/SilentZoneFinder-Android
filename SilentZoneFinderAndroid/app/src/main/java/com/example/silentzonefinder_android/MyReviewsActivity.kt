package com.example.silentzonefinder_android

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.silentzonefinder_android.adapter.MyReviewsAdapter
import com.example.silentzonefinder_android.data.Review
import com.example.silentzonefinder_android.databinding.ActivityMyReviewsBinding
import com.example.silentzonefinder_android.SupabaseManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class MyReviewsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyReviewsBinding
    private lateinit var reviewAdapter: MyReviewsAdapter
    private val originalReviewList = mutableListOf<Review>()
    private var currentFilter: String = FILTER_ALL
    private var currentSort: String = SORT_RECENT

    private val editReviewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadMyReviews()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyReviewsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        
        // 버튼 텍스트 색상을 코드에서 강제로 설정 (스타일 오버라이드 방지)
        val blackColor = android.graphics.Color.parseColor("#FF000000")
        binding.btnFilter.setTextColor(blackColor)
        binding.btnSort.setTextColor(blackColor)
        binding.btnFilter.iconTint = android.content.res.ColorStateList.valueOf(blackColor)
        binding.btnSort.iconTint = android.content.res.ColorStateList.valueOf(blackColor)
        
        // 버튼 텍스트 스타일 강제 설정
        binding.btnFilter.typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
        binding.btnSort.typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)

        // 필터 버튼 클릭 이벤트 설정
        binding.btnFilter.setOnClickListener { view ->
            showFilterMenu(view)
        }

        // 정렬 버튼 클릭 이벤트 설정
        binding.btnSort.setOnClickListener { view ->
            showSortMenu(view)
        }

        binding.btnEmptyNewReview.setOnClickListener {
            navigateToMapForNewReview()
        }
    }
    override fun onResume() {
        super.onResume()
        setupBottomNavigation()
        binding.bottomNavigation.selectedItemId = R.id.navigation_my_reviews
        // 재활용되어 화면에 나타날 때, 애니메이션을 강제로 다시 제거
        overridePendingTransition(0, 0)
        // 라벨 가시성 명시적으로 설정
        binding.bottomNavigation.labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED
        loadMyReviews()
    }

    private fun setupRecyclerView() {
        reviewAdapter = MyReviewsAdapter(
            emptyList(),
            onReviewClick = { review -> openEditReview(review) },
            onDeleteClick = { review -> confirmDeleteReview(review) }
        )
        binding.recyclerViewReviews.apply {
            layoutManager = LinearLayoutManager(this@MyReviewsActivity)
            adapter = reviewAdapter
        }
    }

    // filter 팝업 메뉴를 보여주는 함수
    private fun showFilterMenu(anchorView: View) {
        val popupMenu = PopupMenu(this, anchorView)
        popupMenu.menu.add("All Reviews")
        popupMenu.menu.add("Library Quiet")
        popupMenu.menu.add("Quiet Conversation")
        popupMenu.menu.add("Lively Chatter")
        popupMenu.menu.add("High Traffic")

        popupMenu.setOnMenuItemClickListener { menuItem ->
            val selectedFilter = menuItem.title.toString()
            binding.btnFilter.text = "$selectedFilter ↓" // 버튼 텍스트를 선택된 항목으로 변경
            binding.btnFilter.setTextColor(android.graphics.Color.parseColor("#FF000000")) // 색상 강제 설정

            applyFilter(selectedFilter) // 필터링 로직을 처리하는 함수 호출
            // 굳이 필요없지만 그냥 넣어둠
            Toast.makeText(this, "$selectedFilter 선택됨. 아자스!", Toast.LENGTH_SHORT).show()
            true
        }
        popupMenu.show()
    }

    private fun applyFilter(filterOption: String) {
        currentFilter = filterOption
        applyCurrentFilterAndSort()
    }

    // sort 팝업 메뉴를 보여주는 함수
    private fun showSortMenu(anchorView: View) {
        val popupMenu = PopupMenu(this, anchorView)

        // 코드로 직접 메뉴 항목을 추가합니다.
        popupMenu.menu.add("Most Recent")
        popupMenu.menu.add("Highest Rating")
        popupMenu.menu.add("Optimal to Loud")

        popupMenu.setOnMenuItemClickListener { menuItem ->
            val selectedSort = menuItem.title.toString()
            binding.btnSort.text = "$selectedSort ↓"
            binding.btnSort.setTextColor(android.graphics.Color.parseColor("#FF000000")) // 색상 강제 설정

            applySort(selectedSort) // 정렬 로직을 처리할 함수 호출
            Toast.makeText(this, "$selectedSort 선택됨", Toast.LENGTH_SHORT).show()
            true
        }
        popupMenu.show()
    }
    private fun applySort(sortOption: String) {
        currentSort = sortOption
        applyCurrentFilterAndSort()
    }

    private fun loadMyReviews() {
        lifecycleScope.launch {
            showLoading(true)
            try {
                val session = withContext(Dispatchers.IO) {
                    SupabaseManager.client.auth.currentSessionOrNull()
                }

                val user = session?.user
                if (user == null) {
                    showEmptyState(
                        message = getString(R.string.my_reviews_empty_message),
                        showButton = true
                    )
                    Toast.makeText(
                        this@MyReviewsActivity,
                        "로그인 후 리뷰를 확인할 수 있어요.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val userId = user.id.toString()

                val reviewDtos = withContext(Dispatchers.IO) {
                    SupabaseManager.client.postgrest["reviews"]
                        .select()
                        .decodeList<ReviewDto>()
                        .filter { it.userId == userId }
                }

                if (reviewDtos.isEmpty()) {
                    originalReviewList.clear()
                    applyCurrentFilterAndSort()
                    showEmptyState(
                        message = getString(R.string.my_reviews_empty_message),
                        showButton = true
                    )
                    return@launch
                }

                val placeMap = fetchPlacesFor(reviewDtos.map { it.kakaoPlaceId }.distinct())
                val uiList = reviewDtos.map { dto ->
                    val placeInfo = placeMap[dto.kakaoPlaceId]
                    Review(
                        id = dto.id,
                        kakaoPlaceId = dto.kakaoPlaceId,
                        placeName = placeInfo?.name ?: getString(R.string.unknown_place_label),
                        placeAddress = placeInfo?.address.orEmpty(),
                        decibel = dto.noiseLevelDb.roundToInt(),
                        status = convertNoiseToStatus(dto.noiseLevelDb),
                        date = formatDate(dto.createdAt),
                        reviewText = dto.text.orEmpty(),
                        rating = dto.rating
                    )
                }

                originalReviewList.clear()
                originalReviewList.addAll(uiList)
                applyCurrentFilterAndSort()
                showContentState()
            } catch (e: Exception) {
                e.printStackTrace()
                showEmptyState(
                    message = "리뷰를 불러오지 못했어요. 잠시 후 다시 시도해주세요.",
                    showButton = false
                )
                Toast.makeText(
                    this@MyReviewsActivity,
                    "리뷰 불러오기 실패: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private suspend fun fetchPlacesFor(placeIds: List<String>): Map<String, PlaceDto> {
        if (placeIds.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            SupabaseManager.client.postgrest["places"]
                .select()
                .decodeList<PlaceDto>()
                .filter { placeIds.contains(it.kakaoPlaceId) }
                .associateBy { it.kakaoPlaceId }
        }
    }

    private fun openEditReview(review: Review) {
        val intent = ReviewWriteActivity.createEditIntent(
            context = this,
            reviewId = review.id,
            kakaoPlaceId = review.kakaoPlaceId,
            placeName = review.placeName,
            address = review.placeAddress,
            lat = null,
            lng = null
        )
        editReviewLauncher.launch(intent)
    }

    private fun confirmDeleteReview(review: Review) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.my_reviews_delete_title))
            .setMessage(getString(R.string.my_reviews_delete_message))
            .setNegativeButton(getString(R.string.my_reviews_delete_cancel), null)
            .setPositiveButton(getString(R.string.my_reviews_delete_confirm)) { _, _ ->
                deleteReview(review)
            }
            .show()
    }

    private fun deleteReview(review: Review) {
        lifecycleScope.launch {
            try {
                showLoading(true)
                val session = withContext(Dispatchers.IO) {
                    SupabaseManager.client.auth.currentSessionOrNull()
                }
                val userId = session?.user?.id?.toString()
                if (userId == null) {
                    Toast.makeText(
                        this@MyReviewsActivity,
                        getString(R.string.edit_review_login_required),
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    SupabaseManager.client.postgrest["reviews"].delete {
                        filter {
                            eq("id", review.id)
                            eq("user_id", userId)
                        }
                    }
                }

                Toast.makeText(
                    this@MyReviewsActivity,
                    getString(R.string.my_reviews_delete_success),
                    Toast.LENGTH_SHORT
                ).show()
                loadMyReviews()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MyReviewsActivity,
                    getString(R.string.my_reviews_delete_failure, e.message ?: "-"),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun applyCurrentFilterAndSort() {
        val filteredList = if (currentFilter == FILTER_ALL) {
            originalReviewList
        } else {
            originalReviewList.filter { it.status == currentFilter }
        }

        val sortedList = when (currentSort) {
            SORT_RECENT -> filteredList.sortedByDescending { it.date }
            SORT_RATING -> filteredList.sortedByDescending { it.rating }
            SORT_STATUS -> {
                val order = mapOf(
                    "Library Quiet" to 0,
                    "Quiet Conversation" to 1,
                    "Lively Chatter" to 2,
                    "High Traffic" to 3
                )
                filteredList.sortedBy { order[it.status] ?: Int.MAX_VALUE }
            }
            else -> filteredList
        }

        reviewAdapter.updateReviews(sortedList)
        if (sortedList.isEmpty()) {
            binding.recyclerViewReviews.isVisible = false
            binding.emptyStateContainer.isVisible = true
            binding.emptyStateMessage.text =
                if (originalReviewList.isEmpty()) {
                    getString(R.string.my_reviews_empty_message)
                } else {
                    "선택한 조건에 맞는 리뷰가 없어요."
                }
            binding.btnEmptyNewReview.isVisible = originalReviewList.isEmpty()
        } else {
            binding.recyclerViewReviews.isVisible = true
            binding.emptyStateContainer.isVisible = false
        }
    }

    private fun convertNoiseToStatus(db: Double): String = when {
        db <= 45 -> "Library Quiet"
        db <= 55 -> "Quiet Conversation"
        db <= 65 -> "Lively Chatter"
        else -> "High Traffic"
    }

    private fun formatDate(isoString: String): String {
        return try {
            val date = OffsetDateTime.parse(isoString).toLocalDate()
            date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            isoString.take(10)
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.isVisible = show
        if (show) {
            binding.recyclerViewReviews.isVisible = false
            binding.emptyStateContainer.isVisible = false
        }
    }

    private fun showEmptyState(message: String, showButton: Boolean) {
        binding.recyclerViewReviews.isVisible = false
        binding.emptyStateContainer.isVisible = true
        binding.emptyStateMessage.text = message
        binding.btnEmptyNewReview.isVisible = showButton
    }

    private fun showContentState() {
        binding.recyclerViewReviews.isVisible = originalReviewList.isNotEmpty()
        binding.emptyStateContainer.isVisible = originalReviewList.isEmpty()
        if (originalReviewList.isEmpty()) {
            binding.emptyStateMessage.text = getString(R.string.my_reviews_empty_message)
            binding.btnEmptyNewReview.isVisible = true
        }
    }

    private fun navigateToMapForNewReview() {
        Toast.makeText(this, "지도에서 장소를 선택한 후 리뷰를 작성할 수 있어요.", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            // 1. 이동할 Activity 클래스를 먼저 결정합니다.
            val targetActivity = when (item.itemId) {
                R.id.navigation_map -> MainActivity::class.java
                R.id.navigation_my_reviews -> {
                    // 현재 화면을 또 클릭한 경우: 여기서 추가 동작(예: 스크롤 최상단 이동)을 구현할 수 있습니다.
                    return@setOnItemSelectedListener true
                }
                R.id.navigation_my_favorite -> MyFavoritesActivity::class.java
                R.id.navigation_profile -> ProfileActivity::class.java
                else -> return@setOnItemSelectedListener false // 예상치 못한 ID
            }

            // 2. 공통 로직을 한 번만 실행합니다.
            val intent = Intent(this, targetActivity)

            // Activity 재활용 및 부드러운 전환을 위한 플래그
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)

            startActivity(intent)

            // 3. 애니메이션 설정 (0, 0은 애니메이션 없음)
            overridePendingTransition(0, 0) // <--- 여기에 R.anim.ID 사용을 고려하세요!

            true // 이벤트 처리가 완료되었음을 반환
        }
    }

}

private const val FILTER_ALL = "All Reviews"
private const val SORT_RECENT = "Most Recent"
private const val SORT_RATING = "Highest Rating"
private const val SORT_STATUS = "Optimal to Loud"

@Serializable
private data class ReviewDto(
    val id: Long,
    @SerialName("kakao_place_id") val kakaoPlaceId: String,
    @SerialName("user_id") val userId: String,
    val rating: Int,
    val text: String? = null,
    val images: List<String>? = null,
    @SerialName("noise_level_db") val noiseLevelDb: Double,
    @SerialName("created_at") val createdAt: String
)

@Serializable
private data class PlaceDto(
    @SerialName("kakao_place_id") val kakaoPlaceId: String,
    val name: String? = null,
    val address: String? = null
)
