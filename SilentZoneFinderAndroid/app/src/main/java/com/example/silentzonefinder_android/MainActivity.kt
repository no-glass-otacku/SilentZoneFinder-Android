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
import android.text.TextWatcher
import android.text.Editable
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
import com.example.silentzonefinder_android.utils.SearchHistoryManager
import io.github.jan.supabase.postgrest.postgrest
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraPosition
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
import com.example.silentzonefinder_android.notifications.ReviewNotificationWatcher
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var kakaoMap: KakaoMap? = null
    private lateinit var searchResultsAdapter: PlaceSearchAdapter
    private var searchHistoryAdapter: ArrayAdapter<String>? = null
    private var labelLayer: LabelLayer? = null
    private var infoWindowLayer: InfoWindowLayer? = null
    private var currentInfoWindow: InfoWindow? = null
    private val defaultLocation = LatLng.from(37.5665, 126.9780)
    private var lastKnownCenter: LatLng = defaultLocation
    private val currentMarkerLabelIds = mutableListOf<String>()
    private var allPlaceMarkers: List<PlaceMarkerData> = emptyList()
    private var visiblePlaceMarkers: List<PlaceMarkerData> = emptyList()
    private var selectedPlaceLabelId: String? = null
    private var currentLocationLabelId: String? = null
    private var cameraMoveReloadJob: Job? = null
    private var lastLoadedCameraCenter: LatLng? = null
    private var lastLoadedZoomLevel: Int = -1
    private val cameraMoveDebounceMillis = 600L
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    private var shouldClearSearchResultsOnResume = false

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

    private val searchHistoryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedQuery = result.data
                ?.getStringExtra(SearchHistoryActivity.EXTRA_SELECTED_QUERY)
                ?.takeIf { it.isNotBlank() }
                ?: return@registerForActivityResult

            binding.searchEditText.setText(selectedQuery)
            binding.searchEditText.setSelection(selectedQuery.length)
            performSearch()
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
        setupFilterDropdown()
        setupMyLocationButton()

        ReviewNotificationWatcher(this, SupabaseManager.client).start()
    }

    override fun onResume() {
        super.onResume()
        
        // Called whenever the activity becomes visible.
        setupBottomNavigation()

        // Highlight the current tab in the bottom navigation bar.
        binding.bottomNavigation.selectedItemId = R.id.navigation_map
        // Disable transition animations when returning to this activity.
        overridePendingTransition(0, 0)

        if (shouldClearSearchResultsOnResume) {
            shouldClearSearchResultsOnResume = false
            exitSearchMode(resetQueryField = false, reason = "return_from_detail")
        }

        if (kakaoMap != null) {
            loadPlacesWithReviews()
        }
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
                setupCameraMoveListener(map)
                map.setOnLabelClickListener { _, _, label ->
                    when (val payload = label.tag) {
                        is PlaceMarkerData -> {
                            openPlaceDetail(
                                payload.kakaoPlaceId,
                                payload.name,
                                payload.address,
                                payload.categoryLabel,
                                payload.latitude,
                                payload.longitude
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
                                payload.category_name,
                                payload.y.toDoubleOrNull(),
                                payload.x.toDoubleOrNull()
                            )
                            true
                        }
                        else -> false
                    }
                }
                map.setOnMapClickListener { _, _, _, _ -> closeInfoWindow() }
                lastKnownCenter = defaultLocation
                android.util.Log.d("MainActivity", "Map is ready.")
                loadPlacesWithReviews()
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

    private fun setupCameraMoveListener(map: KakaoMap) {
        map.setOnCameraMoveEndListener { _, cameraPosition, _ ->
            lastKnownCenter = cameraPosition.position
            scheduleCameraReload(cameraPosition)
        }
    }

    private fun setupSearch() {
        binding.searchButton.setOnClickListener {
            performSearch()
        }

        binding.searchHistoryButton.setOnClickListener {
            val intent = Intent(this, SearchHistoryActivity::class.java)
            searchHistoryLauncher.launch(intent)
        }

        binding.resetSearchButton.setOnClickListener {
            exitSearchMode(resetQueryField = true, reason = "manual")
        }

        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        // 검색 입력 필드 포커스 시 검색 기록 표시
        binding.searchEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showSearchHistory()
            } else {
                hideSearchHistory()
            }
        }

        // 검색 입력 필드 텍스트 변경 시 필터링
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrEmpty()) {
                    showSearchHistory()
                } else {
                    filterSearchHistory(s.toString())
                }
            }
        })

        // 검색 기록 어댑터 초기화
        setupSearchHistoryAdapter()
    }

    private fun exitSearchMode(
        resetQueryField: Boolean,
        reason: String = "auto"
    ) {
        shouldClearSearchResultsOnResume = false
        if (!binding.searchResultsCard.isVisible &&
            !binding.searchResultsRecyclerView.isVisible &&
            binding.searchEditText.text.isNullOrEmpty()
        ) {
            return
        }

        if (resetQueryField) {
            binding.searchEditText.text?.clear()
        }
        binding.searchEditText.clearFocus()
        searchResultsAdapter.submitList(emptyList())
        binding.searchResultsCard.isVisible = false
        binding.searchResultsRecyclerView.isVisible = false
        closeInfoWindow()
        clearSelectedPlaceMarker()
        clearMarkers()
        renderFilteredMarkers()
        android.util.Log.d("MainActivity", "Exited search mode ($reason)")
    }

    private fun setupSearchResultsList() {
        searchResultsAdapter = PlaceSearchAdapter { place ->
            val lat = place.y.toDoubleOrNull()
            val lng = place.x.toDoubleOrNull()
            moveMapToPlace(place)
            showSearchResults(emptyList(), renderMarkersWhenEmpty = false)
            val address = place.road_address_name.takeIf { it.isNotBlank() } ?: place.address_name
            shouldClearSearchResultsOnResume = true
            openPlaceDetail(
                placeId = place.id,
                name = place.place_name,
                address = address,
                category = place.category_name,
                lat = lat,
                lng = lng
            )
        }
        binding.searchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = searchResultsAdapter
            setHasFixedSize(true)
        }
        binding.searchResultsCard.isVisible = false
    }

    private fun showSearchResults(
        results: List<PlaceDocument>,
        renderMarkersWhenEmpty: Boolean = true
    ) {
        searchResultsAdapter.submitList(results)
        val hasResults = results.isNotEmpty()
        binding.searchResultsCard.isVisible = hasResults
        binding.searchResultsRecyclerView.isVisible = hasResults
        if (hasResults) {
            closeInfoWindow()
            clearMarkers()
            binding.searchResultsRecyclerView.scrollToPosition(0)
        } else if (renderMarkersWhenEmpty) {
            renderFilteredMarkers()
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
        val matchedMarker = visiblePlaceMarkers.firstOrNull { it.kakaoPlaceId == place.id }
        if (matchedMarker != null) {
            // 샘플 데이터가 있으면 바로 사용
            val markerBitmap = renderNoiseMarker(matchedMarker)
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
            val fallbackMarker = allPlaceMarkers.firstOrNull { it.kakaoPlaceId == place.id }
            if (fallbackMarker != null) {
                val markerBitmap = renderNoiseMarker(fallbackMarker)
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
                        
                        // PlaceDocument를 PlaceMarkerData로 변환
                        val lat = place.y.toDoubleOrNull() ?: 0.0
                        val lng = place.x.toDoubleOrNull() ?: 0.0
                        val address = place.road_address_name.takeIf { it.isNotBlank() } 
                            ?: place.address_name
                        
                        val placeUiSample = PlaceMarkerData(
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
    }

    private fun clearMarkers() {
        obtainLabelLayer()?.let { layer ->
            currentMarkerLabelIds.forEach { id ->
                layer.getLabel(id)?.let { layer.remove(it) }
            }
        }
        currentMarkerLabelIds.clear()
        closeInfoWindow()
    }

    private fun closeInfoWindow() {
        currentInfoWindow?.let { window ->
            window.remove()
        }
        currentInfoWindow = null
    }

    private fun renderMarkers(
        places: List<PlaceMarkerData>,
        moveCameraToFirstMarker: Boolean = false
    ) {
        val map = kakaoMap ?: return
        val labelManager = map.labelManager ?: return
        val layer = obtainLabelLayer() ?: return

        clearMarkers()

        var firstPosition: LatLng? = null
        places.forEach { place ->
            val position = LatLng.from(place.latitude, place.longitude)
            if (firstPosition == null) {
                firstPosition = position
            }
            val markerBitmap = renderNoiseMarker(place)
            val labelStyles = labelManager.addLabelStyles(
                LabelStyles.from(
                    LabelStyle.from(markerBitmap)
                        .setAnchorPoint(0.5f, 1.0f)
                )
            )

            val labelId = "marker_${place.kakaoPlaceId}"
            val labelOptions = LabelOptions.from(labelId, position)
                .setStyles(labelStyles)
                .setClickable(true)
                .setTag(place)

            layer.addLabel(labelOptions)
            currentMarkerLabelIds.add(labelId)
        }

        if (moveCameraToFirstMarker) {
            firstPosition?.let { position ->
                lastKnownCenter = position
                map.moveCamera(CameraUpdateFactory.newCenterPosition(position))
            }
        }
    }

    private fun renderFilteredMarkers() {
        if (visiblePlaceMarkers.isEmpty()) {
            clearMarkers()
            return
        }

        val places = when (currentNoiseFilter) {
            NoiseFilter.ALL -> visiblePlaceMarkers
            NoiseFilter.OPTIMAL -> visiblePlaceMarkers.filter { it.noiseLevel == NoiseLevel.OPTIMAL }
            NoiseFilter.GOOD -> visiblePlaceMarkers.filter { it.noiseLevel == NoiseLevel.GOOD }
            NoiseFilter.NORMAL -> visiblePlaceMarkers.filter { it.noiseLevel == NoiseLevel.NORMAL }
            NoiseFilter.LOUD -> visiblePlaceMarkers.filter { it.noiseLevel == NoiseLevel.LOUD }
        }
        if (places.isEmpty()) {
            clearMarkers()
            Toast.makeText(
                this,
                getString(R.string.map_no_places_for_filter),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        renderMarkers(places)
    }

    private fun scheduleCameraReload(cameraPosition: CameraPosition) {
        cameraMoveReloadJob?.cancel()
        cameraMoveReloadJob = lifecycleScope.launch {
            delay(cameraMoveDebounceMillis)
            if (shouldReloadForCamera(cameraPosition)) {
                loadPlacesWithReviews()
            } else {
                updateVisibleMarkersForCurrentCamera()
            }
        }
    }

    private fun shouldReloadForCamera(cameraPosition: CameraPosition): Boolean {
        val lastCenter = lastLoadedCameraCenter ?: return true
        val distance = calculateDistanceMeters(lastCenter, cameraPosition.position)
        val zoomChanged = lastLoadedZoomLevel != cameraPosition.zoomLevel
        return zoomChanged || distance >= requiredDistanceForZoom(cameraPosition.zoomLevel)
    }

    private fun requiredDistanceForZoom(zoomLevel: Int): Double = when {
        zoomLevel >= 16 -> 120.0
        zoomLevel >= 15 -> 180.0
        zoomLevel >= 14 -> 260.0
        else -> 520.0
    }

    private fun viewportRadiusForZoom(zoomLevel: Int): Double = when {
        zoomLevel >= 17 -> 500.0
        zoomLevel >= 16 -> 900.0
        zoomLevel >= 15 -> 1400.0
        zoomLevel >= 14 -> 2200.0
        else -> 4000.0
    }

    private fun updateVisibleMarkersForCurrentCamera() {
        val map = kakaoMap ?: return
        val cameraPosition = map.cameraPosition ?: return
        val center = cameraPosition.position
        lastKnownCenter = center
        val radius = viewportRadiusForZoom(cameraPosition.zoomLevel)

        visiblePlaceMarkers = allPlaceMarkers.filter {
            val markerLatLng = LatLng.from(it.latitude, it.longitude)
            calculateDistanceMeters(center, markerLatLng) <= radius
        }

        renderFilteredMarkers()
    }

    private fun loadPlacesWithReviews() {
        lifecycleScope.launch {
            try {
                val reviews = withContext(Dispatchers.IO) {
                    SupabaseManager.client.postgrest["reviews"]
                        .select()
                        .decodeList<ReviewDto>()
                }

                if (reviews.isEmpty()) {
                    allPlaceMarkers = emptyList()
                    visiblePlaceMarkers = emptyList()
                    clearMarkers()
                    return@launch
                }

                val placeIds = reviews.map { it.kakaoPlaceId }.toSet()
                val places = withContext(Dispatchers.IO) {
                    SupabaseManager.client.postgrest["places"]
                        .select()
                        .decodeList<PlaceSummaryDto>()
                        .filter { it.kakaoPlaceId in placeIds }
                }.associateBy { it.kakaoPlaceId }

                allPlaceMarkers = reviews.groupBy { it.kakaoPlaceId }
                    .mapNotNull { (placeId, reviewList) ->
                        val info = places[placeId] ?: return@mapNotNull null
                        val lat = info.lat ?: return@mapNotNull null
                        val lng = info.lng ?: return@mapNotNull null
                        val avgDb = reviewList.map { it.noiseLevelDb }.average()
                        val avgRating = reviewList.map { it.rating }.average()
                        val latestReview = reviewList.maxByOrNull { it.createdAt }

                        PlaceMarkerData(
                            kakaoPlaceId = placeId,
                            name = info.name ?: getString(R.string.unknown_place_label),
                            address = info.address ?: "",
                            latitude = lat,
                            longitude = lng,
                            noiseLevel = getNoiseLevelFromDb(avgDb),
                            rating = avgRating.toFloat(),
                            noiseValueDb = avgDb.roundToInt(),
                            reviewCount = reviewList.size,
                            latestReviewSnippet = latestReview?.text ?: "",
                            categoryLabel = info.category
                        )
                    }

                updateVisibleMarkersForCurrentCamera()
                lastLoadedCameraCenter = lastKnownCenter
                lastLoadedZoomLevel = kakaoMap?.cameraPosition?.zoomLevel ?: lastLoadedZoomLevel
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load place markers", e)
                if (visiblePlaceMarkers.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.map_load_reviews_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun applyNoiseFilter(option: String) {
        currentNoiseFilter = when (option) {
            getString(R.string.filter_option_optimal) -> NoiseFilter.OPTIMAL
            getString(R.string.filter_option_good) -> NoiseFilter.GOOD
            getString(R.string.filter_option_normal) -> NoiseFilter.NORMAL
            getString(R.string.filter_option_loud) -> NoiseFilter.LOUD
            else -> NoiseFilter.ALL
        }
        renderFilteredMarkers()
    }

    private fun renderNoiseMarker(place: PlaceMarkerData): Bitmap {
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

    private fun renderCurrentLocationMarker(): Bitmap {
        val sizePx = dp(24f)
        val size = sizePx.toFloat()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 파란색 원 그리기
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_dark)
            style = android.graphics.Paint.Style.FILL
        }
        
        val centerX = size / 2f
        val centerY = size / 2f
        val density: Float = resources.displayMetrics.density
        val strokeWidthValue: Float = 3f * density
        val radius = size / 2f - (2f * density)
        
        // 외곽 흰색 테두리
        val strokePaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = strokeWidthValue
        }
        
        canvas.drawCircle(centerX, centerY, radius, paint)
        canvas.drawCircle(centerX, centerY, radius, strokePaint)
        
        return bitmap
    }

    private fun updateCurrentLocationMarker(position: LatLng) {
        val map = kakaoMap ?: return
        val labelManager = map.labelManager ?: return
        val layer = obtainLabelLayer() ?: return

        // 기존 마커 제거
        currentLocationLabelId?.let { id ->
            layer.getLabel(id)?.let { layer.remove(it) }
        }

        // 새 마커 추가
        val markerBitmap = renderCurrentLocationMarker()
        val labelStyles = labelManager.addLabelStyles(
            LabelStyles.from(
                LabelStyle.from(markerBitmap)
                    .setAnchorPoint(0.5f, 0.5f)
            )
        )

        val labelId = "current_location_${System.currentTimeMillis()}"
        val labelOptions = LabelOptions.from(labelId, position)
            .setStyles(labelStyles)
            .setClickable(false)

        layer.addLabel(labelOptions)
        currentLocationLabelId = labelId
    }

    private fun calculateDistanceMeters(a: LatLng, b: LatLng): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val sinLat = sin(dLat / 2)
        val sinLon = sin(dLon / 2)
        val haversine = sinLat * sinLat + sinLon * sinLon * cos(lat1) * cos(lat2)
        return 2 * earthRadius * asin(min(1.0, sqrt(haversine)))
    }

    @Serializable
    private data class ReviewDto(
        val id: Long,
        @SerialName("kakao_place_id") val kakaoPlaceId: String,
        val rating: Int,
        val text: String? = null,
        val images: List<String>? = null,
        @SerialName("noise_level_db") val noiseLevelDb: Double,
        @SerialName("created_at") val createdAt: String,
        @SerialName("user_id") val userId: String? = null
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
        clearMarkers()
    }

    private fun openPlaceDetail(
        placeId: String,
        name: String,
        address: String?,
        category: String?,
        lat: Double? = null,
        lng: Double? = null
    ) {
        Log.d(
            "PlaceIdCheck",
            "placeId=$placeId, name=$name, address=$address, category=$category, lat=$lat, lng=$lng"
        )

        val intent = PlaceDetailActivity.createIntent(
            context = this,
            placeId = placeId,
            placeName = name,
            address = address,
            category = category,
            lat = lat,
            lng = lng
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
        showSearchResults(emptyList(), renderMarkersWhenEmpty = false)
        clearSelectedPlaceMarker()
        closeInfoWindow()
        clearMarkers()

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
            // 검색 성공 시 검색 기록 저장
            SearchHistoryManager.addSearchQuery(this@MainActivity, query)
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
        binding.filterDropdown.setOnItemClickListener { _, _, position, _ ->
            val selectedOption = filterOptions.getOrNull(position)
                ?: getString(R.string.filter_option_all)
            binding.filterDropdown.setText(selectedOption, false)
            applyNoiseFilter(selectedOption)
        }
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
                    // 현재 위치 마커 추가/업데이트
                    updateCurrentLocationMarker(currentLatLng)
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

    private enum class NoiseFilter {
        ALL, OPTIMAL, GOOD, NORMAL, LOUD
    }

    private var currentNoiseFilter: NoiseFilter = NoiseFilter.ALL

    private data class PlaceMarkerData(
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
        val categoryLabel: String? = null
    )

    @Serializable
    private data class PlaceSummaryDto(
        @SerialName("kakao_place_id") val kakaoPlaceId: String,
        val name: String? = null,
        val address: String? = null,
        val lat: Double? = null,
        val lng: Double? = null,
        val category: String? = null
    )

    private fun setupSearchHistoryAdapter() {
        val history = SearchHistoryManager.getSearchQueries(this)
        searchHistoryAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, history)
        binding.searchEditText.setAdapter(searchHistoryAdapter)
        
        // 검색 기록 클릭 시 자동 검색
        binding.searchEditText.setOnItemClickListener { _, _, position, _ ->
            val selectedQuery = searchHistoryAdapter?.getItem(position) ?: return@setOnItemClickListener
            binding.searchEditText.setText(selectedQuery)
            binding.searchEditText.setSelection(selectedQuery.length)
            binding.searchEditText.dismissDropDown()
            performSearch()
        }
    }

    private fun showSearchHistory() {
        val history = SearchHistoryManager.getSearchQueries(this)
        if (history.isNotEmpty()) {
            searchHistoryAdapter?.clear()
            searchHistoryAdapter?.addAll(history)
            searchHistoryAdapter?.notifyDataSetChanged()
        }
    }

    private fun hideSearchHistory() {
        // 검색 기록 숨김 (자동으로 처리됨)
    }

    private fun filterSearchHistory(query: String) {
        val allHistory = SearchHistoryManager.getSearchQueries(this)
        val filtered = allHistory.filter { it.contains(query, ignoreCase = true) }
        searchHistoryAdapter?.clear()
        if (filtered.isNotEmpty()) {
            searchHistoryAdapter?.addAll(filtered)
        }
        searchHistoryAdapter?.notifyDataSetChanged()
    }
}
