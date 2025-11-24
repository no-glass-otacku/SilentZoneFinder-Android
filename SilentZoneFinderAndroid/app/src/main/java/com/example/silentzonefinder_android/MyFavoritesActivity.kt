package com.example.silentzonefinder_android

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.silentzonefinder_android.adapter.MyFavoritesAdapter
import com.example.silentzonefinder_android.data.FavoritePlace
import com.example.silentzonefinder_android.databinding.ActivityMyFavoritesBinding
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

class MyFavoritesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyFavoritesBinding
    private lateinit var favoritesAdapter: MyFavoritesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        binding.btnEmptyGoToMap.setOnClickListener {
            navigateToMap()
        }
    }

    override fun onResume() {
        super.onResume()

        // 네비게이션 리스너 재등록
        setupBottomNavigation()

        // 현재 탭 고정 (파란색 유지)
        binding.bottomNavigation.selectedItemId = R.id.navigation_my_favorite

        // 애니메이션 제거
        overridePendingTransition(0, 0)

        // 라벨 가시성 명시적으로 설정
        binding.bottomNavigation.labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED

        // 즐겨찾기 목록 로드
        loadMyFavorites()
    }

    private fun setupRecyclerView() {
        favoritesAdapter = MyFavoritesAdapter(
            emptyList(),
            onPlaceClick = { favorite -> openPlaceDetail(favorite) },
            onRemoveFavorite = { favorite -> confirmRemoveFavorite(favorite) }
        )
        binding.recyclerViewFavorites.apply {
            layoutManager = LinearLayoutManager(this@MyFavoritesActivity)
            adapter = favoritesAdapter
        }
    }

    private fun loadMyFavorites() {
        lifecycleScope.launch {
            showLoading(true)
            try {
                val session = withContext(Dispatchers.IO) {
                    SupabaseManager.client.auth.currentSessionOrNull()
                }

                val user = session?.user
                if (user == null) {
                    showEmptyState(
                        message = "로그인 후 즐겨찾기를 확인할 수 있어요.",
                        showButton = false
                    )
                    Toast.makeText(
                        this@MyFavoritesActivity,
                        "로그인 후 즐겨찾기를 확인할 수 있어요.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val userId = user.id.toString()

                // 1. 즐겨찾기 목록 조회
                val favoriteDtos = withContext(Dispatchers.IO) {
                    SupabaseManager.client.postgrest["favorites"]
                        .select {
                            filter {
                                eq("user_id", userId)
                            }
                        }
                        .decodeList<FavoriteDto>()
                }

                if (favoriteDtos.isEmpty()) {
                    favoritesAdapter.updateFavorites(emptyList())
                    showEmptyState(
                        message = "아직 즐겨찾기한 장소가 없어요.\n장소 상세 페이지에서 즐겨찾기를 추가해보세요!",
                        showButton = true
                    )
                    return@launch
                }

                val placeIds = favoriteDtos.map { it.kakaoPlaceId }.distinct()

                // 2. 장소 정보 조회
                val placeMap = fetchPlacesFor(placeIds)

                // 3. 모든 리뷰를 한 번에 조회 (성능 최적화)
                val allReviews = withContext(Dispatchers.IO) {
                    if (placeIds.isEmpty()) {
                        emptyList<ReviewDto>()
                    } else {
                        SupabaseManager.client.postgrest["reviews"]
                            .select()
                            .decodeList<ReviewDto>()
                            .filter { placeIds.contains(it.kakaoPlaceId) }
                    }
                }

                // 4. 장소별로 리뷰 그룹화
                val reviewsByPlace = allReviews.groupBy { it.kakaoPlaceId }

                // 5. FavoritePlace 객체 생성
                val favoritePlaces = favoriteDtos.mapNotNull { favoriteDto ->
                    val placeInfo = placeMap[favoriteDto.kakaoPlaceId] ?: return@mapNotNull null
                    val reviews = reviewsByPlace[favoriteDto.kakaoPlaceId] ?: emptyList()

                    val avgNoiseDb = if (reviews.isNotEmpty()) {
                        reviews.map { it.noiseLevelDb }.average().roundToInt()
                    } else {
                        0
                    }

                    val avgRating = if (reviews.isNotEmpty()) {
                        reviews.map { it.rating.toDouble() }.average()
                    } else {
                        0.0
                    }

                    val noiseStatus = getNoiseStatus(avgNoiseDb.toDouble())

                    FavoritePlace(
                        kakaoPlaceId = favoriteDto.kakaoPlaceId,
                        placeName = placeInfo.name ?: getString(R.string.unknown_place_label),
                        address = placeInfo.address ?: "",
                        avgNoiseDb = avgNoiseDb,
                        noiseStatus = noiseStatus,
                        avgRating = avgRating,
                        reviewCount = reviews.size
                    )
                }

                favoritesAdapter.updateFavorites(favoritePlaces)
                showContentState()
            } catch (e: Exception) {
                e.printStackTrace()
                showEmptyState(
                    message = "즐겨찾기를 불러오지 못했어요. 잠시 후 다시 시도해주세요.",
                    showButton = false
                )
                Toast.makeText(
                    this@MyFavoritesActivity,
                    "즐겨찾기 불러오기 실패: ${e.message}",
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

    private fun openPlaceDetail(favorite: FavoritePlace) {
        val intent = Intent(this, PlaceDetailActivity::class.java).apply {
            putExtra("kakao_place_id", favorite.kakaoPlaceId)
            putExtra("place_name", favorite.placeName)
            putExtra("address", favorite.address)
        }
        startActivity(intent)
    }

    private fun confirmRemoveFavorite(favorite: FavoritePlace) {
        MaterialAlertDialogBuilder(this)
            .setTitle("즐겨찾기 제거")
            .setMessage("${favorite.placeName}을(를) 즐겨찾기에서 제거할까요?")
            .setNegativeButton("취소", null)
            .setPositiveButton("제거") { _, _ ->
                removeFavorite(favorite)
            }
            .show()
    }

    private fun removeFavorite(favorite: FavoritePlace) {
        lifecycleScope.launch {
            try {
                showLoading(true)
                val session = withContext(Dispatchers.IO) {
                    SupabaseManager.client.auth.currentSessionOrNull()
                }
                val userId = session?.user?.id?.toString()
                if (userId == null) {
                    Toast.makeText(
                        this@MyFavoritesActivity,
                        "로그인 후 이용 가능합니다.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    SupabaseManager.client.postgrest["favorites"].delete {
                        filter {
                            eq("user_id", userId)
                            eq("kakao_place_id", favorite.kakaoPlaceId)
                        }
                    }
                }

                Toast.makeText(
                    this@MyFavoritesActivity,
                    "즐겨찾기에서 제거했어요.",
                    Toast.LENGTH_SHORT
                ).show()
                loadMyFavorites()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MyFavoritesActivity,
                    "즐겨찾기 제거에 실패했습니다: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun getNoiseStatus(db: Double): String = when {
        db <= 45.0 -> "Optimal"
        db <= 55.0 -> "Good"
        db <= 65.0 -> "Normal"
        else -> "Loud"
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.isVisible = show
        if (show) {
            binding.recyclerViewFavorites.isVisible = false
            binding.emptyStateContainer.isVisible = false
        }
    }

    private fun showEmptyState(message: String, showButton: Boolean) {
        binding.recyclerViewFavorites.isVisible = false
        binding.emptyStateContainer.isVisible = true
        binding.emptyStateMessage.text = message
        binding.btnEmptyGoToMap.isVisible = showButton
    }

    private fun showContentState() {
        binding.recyclerViewFavorites.isVisible = true
        binding.emptyStateContainer.isVisible = false
    }

    private fun navigateToMap() {
        Toast.makeText(this, "지도에서 장소를 선택한 후 즐겨찾기를 추가할 수 있어요.", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    private fun setupBottomNavigation() {

        binding.bottomNavigation.setOnItemSelectedListener { item ->

            val targetActivity = when (item.itemId) {

                // Map 화면
                R.id.navigation_map -> MainActivity::class.java

                // My Reviews 화면
                R.id.navigation_my_reviews -> MyReviewsActivity::class.java

                // My Favorites 화면 (현재 화면)
                R.id.navigation_my_favorite -> {
                    return@setOnItemSelectedListener true
                }

                // Profile 화면
                R.id.navigation_profile -> ProfileActivity::class.java

                else -> return@setOnItemSelectedListener false
            }

            // 동일 화면이면 다시 실행하지 않음
            if (targetActivity == this::class.java) {
                return@setOnItemSelectedListener true
            }

            val intent = Intent(this, targetActivity).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }

            startActivity(intent)
            overridePendingTransition(0, 0)

            true
        }
    }

    @Serializable
    private data class FavoriteDto(
        @SerialName("user_id") val userId: String,
        @SerialName("kakao_place_id") val kakaoPlaceId: String,
        @SerialName("alert_threshold_db") val alertThresholdDb: Double? = null,
        @SerialName("created_at") val createdAt: String? = null
    )

    @Serializable
    private data class PlaceDto(
        @SerialName("kakao_place_id") val kakaoPlaceId: String,
        val name: String? = null,
        val address: String? = null
    )

    @Serializable
    private data class ReviewDto(
        val id: Long,
        @SerialName("kakao_place_id") val kakaoPlaceId: String,
        val rating: Int,
        val text: String? = null,
        val images: List<String>? = null,
        @SerialName("noise_level_db") val noiseLevelDb: Double,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("user_id") val userId: String? = null
    )
}