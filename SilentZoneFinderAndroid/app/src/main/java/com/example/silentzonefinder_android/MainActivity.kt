package com.example.silentzonefinder_android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.example.silentzonefinder_android.databinding.ActivityMainBinding
import com.example.silentzonefinder_android.adapter.PlaceSearchAdapter
import com.example.silentzonefinder_android.PlaceDetailActivity
import com.example.silentzonefinder_android.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
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
import com.kakao.vectormap.mapwidget.InfoWindow
import com.kakao.vectormap.mapwidget.InfoWindowLayer
import com.kakao.vectormap.mapwidget.InfoWindowOptions
import com.kakao.vectormap.mapwidget.component.GuiImage
import com.kakao.vectormap.mapwidget.component.GuiLayout
import com.kakao.vectormap.mapwidget.component.GuiText
import com.kakao.vectormap.mapwidget.component.Horizontal
import com.kakao.vectormap.mapwidget.component.Orientation
import com.kakao.vectormap.mapwidget.component.Vertical
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var kakaoMap: KakaoMap? = null
    private lateinit var searchResultsAdapter: PlaceSearchAdapter
    private var categorySearchJob: Job? = null
    private var labelLayer: LabelLayer? = null
    private var infoWindowLayer: InfoWindowLayer? = null
    private var currentInfoWindow: InfoWindow? = null
    private val defaultLocation = LatLng.from(37.5665, 126.9780)
    private var lastKnownCenter: LatLng = defaultLocation
    private val currentCategoryLabelIds = mutableListOf<String>()
    private val currentSampleLabelIds = mutableListOf<String>()
    private var selectedPlaceLabelId: String? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            moveToCurrentLocation()
        } else {
            Toast.makeText(
                this,
                getString(R.string.location_permission_denied),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        setupMap()
        setupSearch()
        setupSearchResultsList()
        setupCategoryChips()
        setupFilterDropdown()
        setupMyLocationButton()
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
                labelLayer = map.labelManager?.layer
                labelLayer?.setClickable(true)
                infoWindowLayer = map.mapWidgetManager?.infoWindowLayer?.also { it.setVisible(true) }
                map.setOnLabelClickListener { _, _, label ->
                    when (val payload = label.tag) {
                        is PlaceUiSample -> {
                            openPlaceDetail(
                                payload.kakaoPlaceId,
                                payload.name,
                                payload.address,
                                getString(R.string.category_cafes)
                            )
                            true
                        }
                        is PlaceDocument -> {
                            val address = payload.road_address_name.takeIf { it.isNotBlank() }
                                ?: payload.address_name
                            openPlaceDetail(
                                payload.id,
                                payload.place_name,
                                address,
                                payload.category_name
                            )
                            true
                        }
                        else -> false
                    }
                }
                map.setOnMapClickListener { _, _, _, _ -> closeInfoWindow() }
                lastKnownCenter = defaultLocation
                android.util.Log.d("MainActivity", "Map is ready.")
                renderSampleMarkers()
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
            showSearchResults(emptyList(), showSamplesWhenEmpty = false)
            val address = place.road_address_name.takeIf { it.isNotBlank() } ?: place.address_name
            openPlaceDetail(place.id, place.place_name, address, place.category_name)
        }
        binding.searchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = searchResultsAdapter
            setHasFixedSize(true)
        }
        binding.searchResultsCard.isVisible = false
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
        // 현재 카메라 위치 사용 (moveCamera 호출 시 lastKnownCenter가 업데이트됨)
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
                showSearchResults(emptyList(), showSamplesWhenEmpty = false)
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

    private fun showSearchResults(
        results: List<PlaceDocument>,
        showSamplesWhenEmpty: Boolean = true
    ) {
        searchResultsAdapter.submitList(results)
        val hasResults = results.isNotEmpty()
        binding.searchResultsCard.isVisible = hasResults
        binding.searchResultsRecyclerView.isVisible = hasResults
        if (hasResults) {
            closeInfoWindow()
            clearSampleMarkers()
            binding.searchResultsRecyclerView.scrollToPosition(0)
        } else if (showSamplesWhenEmpty) {
            renderSampleMarkers()
        }
    }

    private fun updateCategoryLabels(categoryLabel: String, places: List<PlaceDocument>) {
        val map = kakaoMap ?: return
        val labelManager = map.labelManager ?: return
        val layer = obtainLabelLayer() ?: return

        clearSampleMarkers()
        closeInfoWindow()
        // Remove previously added labels.
        currentCategoryLabelIds.forEach { id ->
            layer.getLabel(id)?.let { layer.remove(it) }
        }
        currentCategoryLabelIds.clear()

        if (places.isEmpty()) {
            return
        }

        clearSelectedPlaceMarker()

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

    private fun obtainLabelLayer(): LabelLayer? {
        val map = kakaoMap ?: return null
        val labelManager = map.labelManager ?: return null
        return labelLayer ?: labelManager.layer?.also { labelLayer = it }
    }

    private fun clearSelectedPlaceMarker() {
        val layer = obtainLabelLayer() ?: return
        selectedPlaceLabelId?.let { id ->
            layer.getLabel(id)?.let { layer.remove(it) }
        }
        selectedPlaceLabelId = null
    }

    private fun showSelectedPlaceMarker(place: PlaceDocument, position: LatLng) {
        val map = kakaoMap ?: return
        val labelManager = map.labelManager ?: return
        val layer = obtainLabelLayer() ?: return

        selectedPlaceLabelId?.let { id ->
            layer.getLabel(id)?.let { layer.remove(it) }
        }

        // 먼저 하드코딩된 샘플 데이터 확인
        val matchedSample = samplePlaces.firstOrNull { it.kakaoPlaceId == place.id }
        if (matchedSample != null) {
            // 샘플 데이터가 있으면 바로 사용
            val markerBitmap = renderNoiseMarker(matchedSample)
            val labelStyles = labelManager.addLabelStyles(
                LabelStyles.from(
                    LabelStyle.from(markerBitmap)
                        .setAnchorPoint(0.5f, 1.0f)
                )
            )

            val labelId = "selected_${place.id}_${System.currentTimeMillis()}"
            val labelOptions = LabelOptions.from(labelId, position)
                .setStyles(labelStyles)
                .setClickable(true)
                .setTag(place)

            layer.addLabel(labelOptions)
            selectedPlaceLabelId = labelId
        } else {
            // DB에서 리뷰 조회하여 평균 데시벨 계산
            lifecycleScope.launch {
                try {
                    val reviews: List<ReviewDto> = withContext(Dispatchers.IO) {
                        SupabaseManager.client.postgrest["reviews"]
                            .select()
                            .decodeList<ReviewDto>()
                            .filter { it.kakaoPlaceId == place.id }
                    }

                    val markerBitmap = if (reviews.isNotEmpty()) {
                        // 평균 데시벨 계산
                        val avgDb = reviews.map { review -> review.noiseLevelDb }.average()
                        val avgRating = reviews.map { review -> review.rating.toFloat() }.average().toFloat()
                        val noiseLevel = getNoiseLevelFromDb(avgDb)
                        
                        // PlaceDocument를 PlaceUiSample로 변환
                        val lat = place.y.toDoubleOrNull() ?: 0.0
                        val lng = place.x.toDoubleOrNull() ?: 0.0
                        val address = place.road_address_name.takeIf { it.isNotBlank() } 
                            ?: place.address_name
                        
                        val placeUiSample = PlaceUiSample(
                            kakaoPlaceId = place.id,
                            name = place.place_name,
                            address = address,
                            latitude = lat,
                            longitude = lng,
                            noiseLevel = noiseLevel,
                            rating = avgRating,
                            noiseValueDb = avgDb.toInt(),
                            reviewCount = reviews.size,
                            latestReviewSnippet = reviews.firstOrNull()?.text?.take(30) ?: "",
                            categoryLabel = place.category_name
                        )
                        renderNoiseMarker(placeUiSample)
                    } else {
                        // 리뷰가 없으면 기본 마커
                        renderBasicMarker(place)
                    }

                    val labelStyles = labelManager.addLabelStyles(
                        LabelStyles.from(
                            LabelStyle.from(markerBitmap)
                                .setAnchorPoint(0.5f, 1.0f)
                        )
                    )

                    val labelId = "selected_${place.id}_${System.currentTimeMillis()}"
                    val labelOptions = LabelOptions.from(labelId, position)
                        .setStyles(labelStyles)
                        .setClickable(true)
                        .setTag(place)

                    layer.addLabel(labelOptions)
                    selectedPlaceLabelId = labelId
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to load reviews for marker", e)
                    // 에러 발생 시 기본 마커 표시
                    val markerBitmap = renderBasicMarker(place)
                    val labelStyles = labelManager.addLabelStyles(
                        LabelStyles.from(
                            LabelStyle.from(markerBitmap)
                                .setAnchorPoint(0.5f, 1.0f)
                        )
                    )

                    val labelId = "selected_${place.id}_${System.currentTimeMillis()}"
                    val labelOptions = LabelOptions.from(labelId, position)
                        .setStyles(labelStyles)
                        .setClickable(true)
                        .setTag(place)

                    layer.addLabel(labelOptions)
                    selectedPlaceLabelId = labelId
                }
            }
        }
    }

    private fun clearSampleMarkers() {
        obtainLabelLayer()?.let { layer ->
            currentSampleLabelIds.forEach { id ->
                layer.getLabel(id)?.let { layer.remove(it) }
            }
        }
        currentSampleLabelIds.clear()
        closeInfoWindow()
    }

    private fun closeInfoWindow() {
        currentInfoWindow?.let { window ->
            window.remove()
        }
        currentInfoWindow = null
    }

    private fun renderSampleMarkers() {
        val map = kakaoMap ?: return
        val labelManager = map.labelManager ?: return
        val layer = obtainLabelLayer() ?: return

        clearSampleMarkers()

        samplePlaces.forEach { place ->
            val position = LatLng.from(place.latitude, place.longitude)
            val markerBitmap = renderNoiseMarker(place)
            val labelStyles = labelManager.addLabelStyles(
                LabelStyles.from(
                    LabelStyle.from(markerBitmap)
                        .setAnchorPoint(0.5f, 1.0f)
                )
            )

            val labelId = "sample_${place.kakaoPlaceId}"
            val labelOptions = LabelOptions.from(labelId, position)
                .setStyles(labelStyles)
                .setClickable(true)
                .setTag(place)

            layer.addLabel(labelOptions)
            currentSampleLabelIds.add(labelId)
        }
    }

    private fun renderNoiseMarker(place: PlaceUiSample): Bitmap {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.view_noise_marker, null, false)
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val dbTextView = view.findViewById<TextView>(R.id.markerDbTextView)
        val statusTextView = view.findViewById<TextView>(R.id.markerStatusTextView)
        val nameTextView = view.findViewById<TextView>(R.id.markerPlaceNameTextView)

        dbTextView.text = place.noiseValueDb.toString()
        statusTextView.text = getString(place.noiseLevel.labelRes)
        nameTextView.text = place.name.take(12)

        // 배경은 더 투명한 흰색 유지, 테두리만 색상 변경 (더 두껍게)
        (dbTextView.background?.mutate() as? GradientDrawable)?.apply {
            // 더 투명한 흰색 배경 (0xB3 = 약 70% 불투명도)
            setColor(0xB3FFFFFF.toInt())
            setStroke(
                dp(4f),
                ContextCompat.getColor(this@MainActivity, noiseColorRes(place.noiseLevel))
            )
        }
        statusTextView.setTextColor(ContextCompat.getColor(this, noiseColorRes(place.noiseLevel)))
        nameTextView.setTextColor(ContextCompat.getColor(this, R.color.grey_dark))

        val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(spec, spec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        return Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            view.draw(canvas)
        }
    }

    private fun renderBasicMarker(place: PlaceDocument): Bitmap {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.view_noise_marker, null, false).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val dbTextView = view.findViewById<TextView>(R.id.markerDbTextView)
        val statusTextView = view.findViewById<TextView>(R.id.markerStatusTextView)
        val nameTextView = view.findViewById<TextView>(R.id.markerPlaceNameTextView)

        nameTextView.text = place.place_name
        dbTextView.text = "-"
        statusTextView.text = getString(R.string.map_selected_place_label)
        statusTextView.setTextColor(ContextCompat.getColor(this, R.color.grey))
        nameTextView.setTextColor(ContextCompat.getColor(this, R.color.grey_dark))

        // 배경은 더 투명한 흰색 유지, 테두리만 회색 (더 두껍게)
        (dbTextView.background?.mutate() as? GradientDrawable)?.apply {
            // 더 투명한 흰색 배경 (0xB3 = 약 70% 불투명도)
            setColor(0xB3FFFFFF.toInt())
            setStroke(
                dp(4f),
                ContextCompat.getColor(this@MainActivity, R.color.grey)
            )
        }

        val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        return Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            view.draw(canvas)
        }
    }

    private fun noiseColorRes(noiseLevel: NoiseLevel): Int = when (noiseLevel) {
        NoiseLevel.OPTIMAL -> R.color.filter_indicator_optimal
        NoiseLevel.GOOD -> R.color.filter_indicator_good
        NoiseLevel.NORMAL -> R.color.filter_indicator_normal
        NoiseLevel.LOUD -> R.color.filter_indicator_loud
    }

    private fun getNoiseLevelFromDb(db: Double): NoiseLevel = when {
        db <= 45.0 -> NoiseLevel.OPTIMAL
        db <= 55.0 -> NoiseLevel.GOOD
        db <= 65.0 -> NoiseLevel.NORMAL
        else -> NoiseLevel.LOUD
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
    )

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun sp(value: Float): Int =
        (value * resources.displayMetrics.scaledDensity).toInt()

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
            showSelectedPlaceMarker(place, location)
            if (showToast) {
                Toast.makeText(this, "Moved to ${place.place_name}.", Toast.LENGTH_SHORT).show()
            }
        }
        lastKnownCenter = location
        updateCategoryLabels("", emptyList())
        clearSampleMarkers()
    }

    private fun openPlaceDetail(placeId: String, name: String, address: String?, category: String?) {
        Log.d("PlaceIdCheck", "placeId=$placeId, name=$name, address=$address, category=$category")
        val intent = PlaceDetailActivity.createIntent(
            context = this,
            placeId = placeId,
            placeName = name,
            address = address,
            category = category
        )
        startActivity(intent)
    }

    private fun performSearch() {
        val query = binding.searchEditText.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "Please enter a search keyword.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.searchEditText.clearFocus()
        binding.categoryChipGroup.clearCheck()
        showSearchResults(emptyList(), showSamplesWhenEmpty = false)
        updateCategoryLabels("", emptyList())
        clearSelectedPlaceMarker()
        closeInfoWindow()
        clearSampleMarkers()

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

    private fun setupFilterDropdown() {
        val filterOptions = resources.getStringArray(R.array.filter_options)
        val typedArray = resources.obtainTypedArray(R.array.filter_indicator_colors)
        val indicatorColors = IntArray(filterOptions.size) { index ->
            typedArray.getColor(
                index,
                ContextCompat.getColor(this, R.color.filter_indicator_all)
            )
        }
        typedArray.recycle()

        val adapter = object : ArrayAdapter<String>(
            this,
            R.layout.item_filter_option,
            R.id.optionTextView,
            filterOptions
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                applyIndicatorColor(view, position, indicatorColors)
                return view
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val view = super.getDropDownView(position, convertView, parent)
                applyIndicatorColor(view, position, indicatorColors)
                return view
            }

            private fun applyIndicatorColor(view: View, position: Int, colors: IntArray) {
                val indicatorView = view.findViewById<View>(R.id.indicatorView) ?: return
                val color = colors.getOrNull(position)
                    ?: ContextCompat.getColor(this@MainActivity, R.color.filter_indicator_all)
                val background = indicatorView.background?.mutate()
                if (background is GradientDrawable) {
                    background.setColor(color)
                }
            }
        }

        adapter.setDropDownViewResource(R.layout.item_filter_option)
        binding.filterDropdown.setAdapter(adapter)
        binding.filterDropdown.setText(getString(R.string.filter_option_all), false)
    }

    private fun setupMyLocationButton() {
        binding.fabMyLocation.setOnClickListener {
            checkLocationPermissionAndMove()
        }
    }

    private fun checkLocationPermissionAndMove() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineLocationGranted || coarseLocationGranted) {
            moveToCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun moveToCurrentLocation() {
        if (kakaoMap == null) {
            Toast.makeText(this, getString(R.string.location_error), Toast.LENGTH_SHORT).show()
            return
        }

        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineLocationGranted && !coarseLocationGranted) {
            Toast.makeText(
                this,
                getString(R.string.location_permission_required),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            val cancellationTokenSource = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val currentLatLng = LatLng.from(location.latitude, location.longitude)
                    kakaoMap?.moveCamera(
                        CameraUpdateFactory.newCenterPosition(currentLatLng)
                    )
                    lastKnownCenter = currentLatLng
                    Log.d("MainActivity", "Moved to current location: ${location.latitude}, ${location.longitude}")
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.location_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.addOnFailureListener { exception ->
                Log.e("MainActivity", "Failed to get location", exception)
                Toast.makeText(
                    this,
                    getString(R.string.location_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: SecurityException) {
            Log.e("MainActivity", "Security exception when getting location", e)
            Toast.makeText(
                this,
                getString(R.string.location_permission_required),
                Toast.LENGTH_SHORT
            ).show()
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

    private enum class NoiseLevel(val labelRes: Int) {
        OPTIMAL(R.string.noise_level_optimal),
        GOOD(R.string.noise_level_good),
        NORMAL(R.string.noise_level_normal),
        LOUD(R.string.noise_level_loud)
    }

    private data class PlaceUiSample(
        val kakaoPlaceId: String,
        val name: String,
        val address: String,
        val latitude: Double,
        val longitude: Double,
        val noiseLevel: NoiseLevel,
        val rating: Float,
        val noiseValueDb: Int,
        val reviewCount: Int,
        val latestReviewSnippet: String,
        val categoryLabel: String = ""
    )

    private val samplePlaces = listOf(
        PlaceUiSample(
            kakaoPlaceId = "10834151",
            name = "투썸플레이스 노원공릉점",
            address = "서울 노원구 동일로192길 52 1층",
            latitude = 37.62569501,
            longitude = 127.0784260,
            noiseLevel = NoiseLevel.OPTIMAL,
            rating = 4.6f,
            noiseValueDb = 42,
            reviewCount = 12,
            latestReviewSnippet = "카페 내 음악 볼륨이 낮아서 조용했어요."
        ),
        PlaceUiSample(
            kakaoPlaceId = "18520333",
            name = "파운드그레도",
            address = "서울 노원구 동일로 1102 1층",
            latitude = 37.62624471,
            longitude = 127.0784260,
            noiseLevel = NoiseLevel.GOOD,
            rating = 4.4f,
            noiseValueDb = 48,
            reviewCount = 7,
            latestReviewSnippet = "오후엔 주변이 살짝 붐비지만 대화는 무난해요."
        ),
        PlaceUiSample(
            kakaoPlaceId = "20923010",
            name = "코타브레드",
            address = "서울 노원구 공릉로 165 1층",
            latitude = 37.62624471,
            longitude = 127.0772097,
            noiseLevel = NoiseLevel.NORMAL,
            rating = 4.2f,
            noiseValueDb = 58,
            reviewCount = 5,
            latestReviewSnippet = "빵 굽는 소리랑 손님들 대화가 은근 크게 들려요."
        ),
        PlaceUiSample(
            kakaoPlaceId = "19374025",
            name = "무이로커피",
            address = "서울 노원구 동일로186길 77-7 명문빌딩 1층",
            latitude = 37.62319451,
            longitude = 127.0766327,
            noiseLevel = NoiseLevel.OPTIMAL,
            rating = 4.7f,
            noiseValueDb = 39,
            reviewCount = 9,
            latestReviewSnippet = "콘센트 많고 조용해서 작업하기 좋아요."
        ),
        PlaceUiSample(
            kakaoPlaceId = "20786938",
            name = "블루마일스커피로스터즈",
            address = "서울 노원구 동일로190길 65 우화빌딩 2층",
            latitude = 37.62536851,
            longitude = 127.0776785,
            noiseLevel = NoiseLevel.GOOD,
            rating = 4.5f,
            noiseValueDb = 50,
            reviewCount = 6,
            latestReviewSnippet = "로스터기 돌아가는 소리가 조금 있지만 무난해요."
        ),
        PlaceUiSample(
            kakaoPlaceId = "15674001",
            name = "칼퇴근김대리 공릉점",
            address = "서울 노원구 동일로192길 53 1층",
            latitude = 37.62580571,
            longitude = 127.0778897,
            noiseLevel = NoiseLevel.LOUD,
            rating = 4.1f,
            noiseValueDb = 68,
            reviewCount = 10,
            latestReviewSnippet = "저녁에는 사람들이 많아서 꽤 시끄러워요."
        ),
        PlaceUiSample(
            kakaoPlaceId = "15858055",
            name = "스타벅스 공릉역점",
            address = "서울 노원구 동일로 1081",
            latitude = 37.62243271,
            longitude = 127.0780287,
            noiseLevel = NoiseLevel.NORMAL,
            rating = 4.3f,
            noiseValueDb = 55,
            reviewCount = 21,
            latestReviewSnippet = "출퇴근 시간대엔 자리 찾기 힘들고 소음도 커요."
        ),
        PlaceUiSample(
            kakaoPlaceId = "23011311",
            name = "역전할머니맥주 서울공릉점",
            address = "서울 노원구 동일로192길 80 1층",
            latitude = 37.62640571,
            longitude = 127.0778897,
            noiseLevel = NoiseLevel.LOUD,
            rating = 4.0f,
            noiseValueDb = 72,
            reviewCount = 3,
            latestReviewSnippet = "술집 분위기라 음악과 대화 소리가 크네요."
        ),
        PlaceUiSample(
            kakaoPlaceId = "16397395",
            name = "술잔에타",
            address = "서울 노원구 공릉로51길 7 1층",
            latitude = 37.62885971,
            longitude = 127.0818817,
            noiseLevel = NoiseLevel.LOUD,
            rating = 4.2f,
            noiseValueDb = 70,
            reviewCount = 4,
            latestReviewSnippet = "주말 밤에는 거의 떠들썩한 편."
        ),
        PlaceUiSample(
            kakaoPlaceId = "15364843",
            name = "아너카페",
            address = "서울 노원구 동일로192다길 9 A동 1-2층",
            latitude = 37.62479571,
            longitude = 127.0784260,
            noiseLevel = NoiseLevel.GOOD,
            rating = 4.6f,
            noiseValueDb = 52,
            reviewCount = 14,
            latestReviewSnippet = "적당한 음악과 조용한 좌석이 많아요."
        )
    )
}
