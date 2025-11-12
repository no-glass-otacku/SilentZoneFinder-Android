package com.example.silentzonefinder_android

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.silentzonefinder_android.adapter.ReviewAdapter
import com.example.silentzonefinder_android.data.Review
import com.example.silentzonefinder_android.databinding.ActivityMyReviewsBinding

class MyReviewsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyReviewsBinding
    private lateinit var reviewAdapter: ReviewAdapter
    private val originalReviewList = mutableListOf<Review>() // 원본 데이터 리스트

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyReviewsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadDummyData() // 초기 데이터 로드

        // 필터 버튼 클릭 이벤트 설정
        binding.btnFilter.setOnClickListener { view ->
            showFilterMenu(view)
        }

        // 정렬 버튼 클릭 이벤트 설정
        binding.btnSort.setOnClickListener { view ->
            showSortMenu(view)
        }
    }
    override fun onResume() {
        super.onResume()
        setupBottomNavigation()
        binding.bottomNavigation.selectedItemId = R.id.navigation_my_reviews
        // 재활용되어 화면에 나타날 때, 애니메이션을 강제로 다시 제거
        overridePendingTransition(0, 0)
    }

    private fun setupRecyclerView() {
        reviewAdapter = ReviewAdapter(emptyList()) // 처음에는 빈 리스트로 어댑터 생성
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
            binding.btnFilter.text = selectedFilter // 버튼 텍스트를 선택된 항목으로 변경

            applyFilter(selectedFilter) // 필터링 로직을 처리하는 함수 호출
            // 굳이 필요없지만 그냥 넣어둠
            Toast.makeText(this, "$selectedFilter 선택됨. 아자스!", Toast.LENGTH_SHORT).show()
            true
        }
        popupMenu.show()
    }

    private fun applyFilter(filterOption: String) {
        val filteredList = if (filterOption == "All Reviews") {
            originalReviewList // "All Reviews"를 선택하면 원본 리스트 전체를 보여줍니다.
        } else {
            // 원본 리스트에서 선택된 status와 일치하는 항목만 걸러냅니다.
            originalReviewList.filter { it.status == filterOption }
        }

        // 필터링된 새 리스트로 어댑터의 화면을 업데이트합니다.
        reviewAdapter.updateReviews(filteredList)
    }

    // sort 팝업 메뉴를 보여주는 함수
    private fun showSortMenu(anchorView: View) {val popupMenu = PopupMenu(this, anchorView)

        // 코드로 직접 메뉴 항목을 추가합니다.
        popupMenu.menu.add("Most Recent")
        popupMenu.menu.add("Highest Rating")
        popupMenu.menu.add("Optimal to Loud")

        popupMenu.setOnMenuItemClickListener { menuItem ->
            val selectedSort = menuItem.title.toString()
            binding.btnSort.text = selectedSort

            applySort(selectedSort) // 정렬 로직을 처리할 함수 호출
            Toast.makeText(this, "$selectedSort 선택됨", Toast.LENGTH_SHORT).show()
            true
        }
        popupMenu.show()
    }
    private fun applySort(sortOption: String) {
        val sortedList = when (sortOption) {
            "Most Recent" -> {
                originalReviewList.sortedByDescending { it.date }
            }
            "Highest Rating" -> {
                originalReviewList.sortedByDescending { it.rating }
            }
            "Quiet to Loud" -> {
                // 조용한 순으로 정렬하려면 순서를 직접 지정해줘야 합니다.
                val statusOrder = mapOf("Library Quiet" to 0, "Quiet Conversation" to 1, "Lively Chatter" to 2, "High Traffic" to 3)
                originalReviewList.sortedBy { statusOrder[it.status] }
            }
            else -> {
                originalReviewList // 기본값 (원본 순서)
            }
        }
        // 정렬된 새 리스트로 어댑터의 화면을 업데이트합니다.
        reviewAdapter.updateReviews(sortedList)
    }

    // 테스트용 데이터 로드 함수
    private fun loadDummyData() {
        originalReviewList.clear()
        originalReviewList.addAll(
            listOf(
                Review(
                    placeName = "University Study Hall",
                    decibel = 35,
                    status = "Library Quiet",
                    rating = 5,
                    date = "2024-01-14",
                    reviewText = "Extremely quiet, perfect for exams prep.",
                    amenities = listOf("Wi-Fi", "Outlets", "AC")
                ),
                Review(
                    placeName = "Quiet Cafe",
                    decibel = 50,
                    status = "Quiet Conversation",
                    rating = 4,
                    date = "2024-01-12",
                    reviewText = "Nice ambiance, good for casual work.",
                    amenities = listOf("Wi-Fi", "Coffee")
                ),
                Review(
                    placeName = "Busy Coffee Shop",
                    decibel = 70,
                    status = "High Traffic",
                    rating = 3,
                    date = "2024-01-10",
                    reviewText = "A bit loud for focused work, but okay.",
                    amenities = listOf("Wi-Fi", "Coffee")
                )
            )
        )
        // 어댑터에 데이터 업데이트
        reviewAdapter.updateReviews(originalReviewList)
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
