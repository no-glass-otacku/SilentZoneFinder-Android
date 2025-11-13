package com.example.silentzonefinder_android

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.silentzonefinder_android.databinding.ActivityMainBinding
import com.example.silentzonefinder_android.adapter.PlaceSearchAdapter
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelLayer
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.label.LabelTextBuilder
import com.kakao.vectormap.label.LabelTextStyle
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var kakaoMap: KakaoMap? = null
    private lateinit var searchResultsAdapter: PlaceSearchAdapter
    private var categorySearchJob: Job? = null
    private var categoryLayer: LabelLayer? = null
    private val defaultLocation = LatLng.from(37.5665, 126.9780)
    private var lastKnownCenter: LatLng = defaultLocation
    private val currentCategoryLabelIds = mutableListOf<String>()
    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMap()
        setupSearch()
        setupSearchResultsList()
        setupCategoryChips()
    }

    override fun onResume() {
        super.onResume()
        
        // Called whenever the activity becomes visible.
        setupBottomNavigation()

        // Highlight the current tab in the bottom navigation bar.
        binding.bottomNavigation.selectedItemId = R.id.navigation_map
        // Disable transition animations when returning to this activity.
        overridePendingTransition(0, 0)
    }

    private fun setupMap() {
        val defaultZoomLevel = 15
        
        val lifeCycleCallback = object : MapLifeCycleCallback() {
            override fun onMapDestroy() {
                android.util.Log.d("MainActivity", "Map has been destroyed.")
            }

            override fun onMapError(error: Exception) {
                android.util.Log.e("MainActivity", "Map error", error)
                android.util.Log.e("MainActivity", "Error type: ${error.javaClass.simpleName}")
                android.util.Log.e("MainActivity", "Error message: ${error.message}")
                
                val errorMessage = when {
                    error.message?.contains("401") == true || error.message?.contains("Unauthorized") == true ->
                        "Kakao Map authentication failed. Please check the Kakao Developers console."
                    else -> "An error occurred while loading the map: ${error.message}"
                }
                Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
        
        val readyCallback = object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                kakaoMap = map
                categoryLayer = map.labelManager?.layer
                lastKnownCenter = defaultLocation
                android.util.Log.d("MainActivity", "Map is ready.")
            }

            override fun getPosition(): LatLng {
                return defaultLocation
            }

            override fun getZoomLevel(): Int {
                return defaultZoomLevel
            }
        }
        
        binding.mapView.start(lifeCycleCallback, readyCallback)
    }

    private fun setupSearch() {
        binding.searchButton.setOnClickListener {
            performSearch()
        }

        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
    }

    private fun setupSearchResultsList() {
        searchResultsAdapter = PlaceSearchAdapter { place ->
            moveMapToPlace(place)
            binding.categoryChipGroup.clearCheck()
            showSearchResults(emptyList())
        }
        binding.searchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = searchResultsAdapter
            isVisible = false
            setHasFixedSize(true)
        }
    }

    private fun setupCategoryChips() {
        binding.categoryChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) {
                categorySearchJob?.cancel()
                showSearchResults(emptyList())
                updateCategoryLabels("", emptyList())
                return@setOnCheckedStateChangeListener
            }

            val option = when (checkedIds.first()) {
                R.id.chipRestaurant -> CategoryOption(code = "FD6", label = "Restaurants")
                R.id.chipCafe -> CategoryOption(code = "CE7", label = "Cafes")
                R.id.chipBar -> CategoryOption(code = "FD6", label = "Bars", query = "bar")
                else -> null
            } ?: return@setOnCheckedStateChangeListener

            loadCategoryPlaces(option)
        }
    }

    private fun loadCategoryPlaces(option: CategoryOption) {
        val center = lastKnownCenter
        binding.searchEditText.clearFocus()
        categorySearchJob?.cancel()
        categorySearchJob = lifecycleScope.launch {
            try {
                val results = requestCategoryPlaces(option, center)
                updateCategoryLabels(option.label, results)
                val message = if (results.isEmpty()) {
                    "No ${option.label.lowercase(Locale.US)} found nearby."
                } else {
                    "${results.size} ${option.label.lowercase(Locale.US)} found nearby."
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                showSearchResults(emptyList())
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Category search failed", e)
                Toast.makeText(this@MainActivity, "Category search failed: ${e.message}", Toast.LENGTH_SHORT).show()
                updateCategoryLabels(option.label, emptyList())
                showSearchResults(emptyList())
                binding.categoryChipGroup.clearCheck()
            }
        }
    }

    private suspend fun requestCategoryPlaces(option: CategoryOption, center: LatLng): List<PlaceDocument> {
        val apiKey = BuildConfig.KAKAO_REST_API_KEY
        if (apiKey.isEmpty()) {
            throw IllegalStateException("Kakao REST API key is not configured.")
        }

        val response: KakaoPlaceResponse = httpClient.get("https://dapi.kakao.com/v2/local/search/category.json") {
            url {
                parameters.append("category_group_code", option.code)
                parameters.append("radius", "1000")
                parameters.append("sort", "distance")
                parameters.append("page", "1")
                parameters.append("size", "15")
                parameters.append("x", center.longitude.toString())
                parameters.append("y", center.latitude.toString())
                option.query?.let { parameters.append("query", it) }
            }
            headers {
                append("Authorization", "KakaoAK $apiKey")
                append("Accept-Language", "en-US")
            }
        }.body()

        return response.documents
    }

    private fun showSearchResults(results: List<PlaceDocument>) {
        searchResultsAdapter.submitList(results)
        binding.searchResultsRecyclerView.isVisible = results.isNotEmpty()
        if (results.isNotEmpty()) {
            binding.searchResultsRecyclerView.scrollToPosition(0)
        }
    }

    private fun updateCategoryLabels(categoryLabel: String, places: List<PlaceDocument>) {
        val map = kakaoMap ?: return
        val labelManager = map.labelManager ?: return
        val layer = categoryLayer ?: labelManager.layer?.also { categoryLayer = it } ?: return

        // Remove previously added labels.
        currentCategoryLabelIds.forEach { id ->
            layer.getLabel(id)?.let { layer.remove(it) }
        }
        currentCategoryLabelIds.clear()

        if (places.isEmpty()) {
            return
        }

        val labelStyles = labelManager.addLabelStyles(
            LabelStyles.from(
                LabelStyle.from().setTextStyles(
                    LabelTextStyle.from(this, R.style.KakaoLabelTextStyle)
                )
            )
        )

        var firstLocation: LatLng? = null
        places.forEachIndexed { index, place ->
            val lat = place.y.toDoubleOrNull()
            val lng = place.x.toDoubleOrNull()
            if (lat != null && lng != null) {
                val position = LatLng.from(lat, lng)
                val labelText = place.place_name.takeIf { it.isNotBlank() } ?: categoryLabel
                val labelId = "category_${index}_${labelText.hashCode()}"
                val labelOptions = LabelOptions.from(labelId, position)
                    .setStyles(labelStyles)
                    .setTexts(LabelTextBuilder().setTexts(labelText))
                layer.addLabel(labelOptions)
                currentCategoryLabelIds.add(labelId)
                if (firstLocation == null) {
                    firstLocation = position
                    lastKnownCenter = position
                }
            }
        }

        firstLocation?.let { position ->
            map.moveCamera(CameraUpdateFactory.newCenterPosition(position))
        }
    }

    private fun moveMapToPlace(place: PlaceDocument, showToast: Boolean = true) {
        val lat = place.y.toDoubleOrNull()
        val lng = place.x.toDoubleOrNull()
        if (lat == null || lng == null) {
            Toast.makeText(this, "Invalid coordinates for the selected place.", Toast.LENGTH_SHORT).show()
            return
        }

        val location = LatLng.from(lat, lng)
        kakaoMap?.let { map ->
            map.moveCamera(CameraUpdateFactory.newCenterPosition(location))
            if (showToast) {
                Toast.makeText(this, "Moved to ${place.place_name}.", Toast.LENGTH_SHORT).show()
            }
        }
        lastKnownCenter = location
        updateCategoryLabels("", emptyList())
    }

    private fun performSearch() {
        val query = binding.searchEditText.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "Please enter a search keyword.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.searchEditText.clearFocus()
        binding.categoryChipGroup.clearCheck()
        showSearchResults(emptyList())
        updateCategoryLabels("", emptyList())

        lifecycleScope.launch {
            try {
                searchPlace(query)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Place search failed", e)
                Toast.makeText(this@MainActivity, "Failed to search places: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun searchPlace(query: String) {
        val apiKey = BuildConfig.KAKAO_REST_API_KEY
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Kakao REST API key is not configured.", Toast.LENGTH_SHORT).show()
            return
        }

        val response: KakaoPlaceResponse = httpClient.get("https://dapi.kakao.com/v2/local/search/keyword.json") {
            url {
                parameters.append("query", query)
                parameters.append("size", "10")
            }
            headers {
                append("Authorization", "KakaoAK $apiKey")
                append("Accept-Language", "en-US")
            }
        }.body()

        showSearchResults(response.documents)

        if (response.documents.isNotEmpty()) {
            moveMapToPlace(response.documents[0], showToast = false)
        } else {
            Toast.makeText(this, "No results found.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            // 1. Determine the target activity.
            val targetActivity = when (item.itemId) {
                R.id.navigation_map -> {
                    return@setOnItemSelectedListener true
                }
                R.id.navigation_my_reviews -> MyReviewsActivity::class.java
                R.id.navigation_my_favorite -> MyFavoritesActivity::class.java
                R.id.navigation_profile -> ProfileActivity::class.java
                else -> return@setOnItemSelectedListener false // Unexpected menu id.
            }

            // 2. Launch the target activity.
            val intent = Intent(this, targetActivity)

            // Reuse activities already in the stack and remove animations.
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)

            startActivity(intent)

            // 3. Disable transition animations.
            overridePendingTransition(0, 0)

            true // Indicate that the event has been handled.
        }
    }

    private data class CategoryOption(
        val code: String,
        val label: String,
        val query: String? = null
    )

    @Serializable
    data class KakaoPlaceResponse(
        val documents: List<PlaceDocument>,
        val meta: Meta
    )

    @Serializable
    data class PlaceDocument(
        val id: String,
        val place_name: String,
        val category_name: String,
        val category_group_code: String,
        val category_group_name: String,
        val phone: String,
        val address_name: String,
        val road_address_name: String,
        val x: String,
        val y: String,
        val place_url: String,
        val distance: String
    )

    @Serializable
    data class Meta(
        val total_count: Int,
        val pageable_count: Int,
        val is_end: Boolean
    )
}
