package com.example.silentzonefinder_android.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.silentzonefinder_android.R
import com.example.silentzonefinder_android.data.AirQualityData
import com.example.silentzonefinder_android.data.HourlyForecastItem
import com.example.silentzonefinder_android.data.WeatherResponse
import com.example.silentzonefinder_android.databinding.FragmentWeatherDetailBinding
import com.example.silentzonefinder_android.service.WeatherService
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

class WeatherDetailBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentWeatherDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var weatherService: WeatherService
    private var currentLat: Double = 0.0
    private var currentLon: Double = 0.0
    private var locationName: String = ""

    private val hourlyForecastAdapter = HourlyForecastAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val httpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
        weatherService = WeatherService(httpClient)
        
        arguments?.let {
            currentLat = it.getDouble(ARG_LAT, 0.0)
            currentLon = it.getDouble(ARG_LON, 0.0)
            locationName = it.getString(ARG_LOCATION_NAME, "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWeatherDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.locationNameTextView.text = locationName
        binding.closeButton.setOnClickListener { dismiss() }

        binding.hourlyForecastRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = hourlyForecastAdapter
        }

        // 탭 전환 (시간대별/일별)
        binding.hourlyTab.setOnClickListener {
            binding.hourlyTab.isSelected = true
            binding.dailyTab.isSelected = false
            loadHourlyForecast()
        }

        binding.dailyTab.setOnClickListener {
            binding.hourlyTab.isSelected = false
            binding.dailyTab.isSelected = true
            // 일별 예보는 현재 시간대별로 표시
            loadHourlyForecast()
        }

        binding.hourlyTab.isSelected = true

        loadWeatherData()
    }

    private fun loadWeatherData() {
        lifecycleScope.launch {
            try {
                // 현재 날씨와 대기질 정보 동시 로드
                val weatherResult = weatherService.getCurrentWeather(currentLat, currentLon)
                val airQualityResult = weatherService.getAirQuality(currentLat, currentLon)

                weatherResult.fold(
                    onSuccess = { weather ->
                        updateCurrentWeather(weather)
                    },
                    onFailure = { e ->
                        binding.errorTextView.text = getString(R.string.weather_error)
                        binding.errorTextView.visibility = View.VISIBLE
                    }
                )

                airQualityResult.fold(
                    onSuccess = { airQuality ->
                        updateAirQuality(airQuality.list.firstOrNull())
                    },
                    onFailure = { e ->
                        // 대기질 정보 로드 실패는 무시
                    }
                )

                loadHourlyForecast()
            } catch (e: Exception) {
                binding.errorTextView.text = getString(R.string.weather_error)
                binding.errorTextView.visibility = View.VISIBLE
            }
        }
    }

    private fun updateCurrentWeather(weather: WeatherResponse) {
        val main = weather.main
        val weatherInfo = weather.weather.firstOrNull()

        // 온도 표시
        binding.currentTempTextView.text = "${main.temp.toInt()}°"
        
        // 날씨 상태
        binding.weatherConditionTextView.text = weatherInfo?.description?.replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
        } ?: ""

        // 체감온도
        binding.feelsLikeTextView.text = getString(R.string.weather_feels_like) + " ${main.feelsLike.toInt()}°"

        // 날씨 아이콘은 간단히 텍스트로 표시 (나중에 아이콘 추가 가능)
        weatherInfo?.let {
            when {
                it.main.contains("Clear", ignoreCase = true) -> binding.weatherIconImageView.setImageResource(R.drawable.ic_weather_cloud)
                it.main.contains("Cloud", ignoreCase = true) -> binding.weatherIconImageView.setImageResource(R.drawable.ic_weather_cloud)
                else -> binding.weatherIconImageView.setImageResource(R.drawable.ic_weather_cloud)
            }
        }

        // 업데이트 시간
        val updateTime = SimpleDateFormat("yyyy.MM.dd. HH:mm", Locale.getDefault()).format(Date())
        binding.updateTimeTextView.text = "$updateTime ${getString(R.string.weather_updated)}"
    }

    private fun updateAirQuality(airQuality: AirQualityData?) {
        airQuality?.let {
            val pm25 = it.components.pm25 ?: 0.0
            val pm10 = it.components.pm10 ?: 0.0
            
            val aqi = it.main.aqi
            val fineDustStatus = when (aqi) {
                1 -> getString(R.string.weather_air_quality_good)
                2 -> getString(R.string.weather_air_quality_moderate)
                3 -> getString(R.string.weather_air_quality_unhealthy)
                4 -> getString(R.string.weather_air_quality_unhealthy)
                5 -> getString(R.string.weather_air_quality_very_unhealthy)
                else -> getString(R.string.weather_air_quality_moderate)
            }

            binding.fineDustTextView.text = "${getString(R.string.weather_air_quality_fine)} $fineDustStatus"
            binding.ultrafineDustTextView.text = "${getString(R.string.weather_air_quality_ultrafine)} $fineDustStatus"
        }
    }

    private fun loadHourlyForecast() {
        lifecycleScope.launch {
            try {
                val result = weatherService.getHourlyForecast(currentLat, currentLon)
                result.fold(
                    onSuccess = { forecast ->
                        hourlyForecastAdapter.submitList(forecast.list.take(8)) // 최근 8시간 표시
                    },
                    onFailure = { e ->
                        // 에러 처리
                    }
                )
            } catch (e: Exception) {
                // 에러 처리
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_LAT = "lat"
        private const val ARG_LON = "lon"
        private const val ARG_LOCATION_NAME = "location_name"

        fun newInstance(lat: Double, lon: Double, locationName: String): WeatherDetailBottomSheetFragment {
            return WeatherDetailBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putDouble(ARG_LAT, lat)
                    putDouble(ARG_LON, lon)
                    putString(ARG_LOCATION_NAME, locationName)
                }
            }
        }
    }

    private class HourlyForecastAdapter : RecyclerView.Adapter<HourlyForecastAdapter.ViewHolder>() {
        private var items: List<HourlyForecastItem> = emptyList()

        fun submitList(newItems: List<HourlyForecastItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_hourly_forecast, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val timeTextView: TextView = itemView.findViewById(R.id.timeTextView)
            private val tempTextView: TextView = itemView.findViewById(R.id.tempTextView)
            private val iconImageView: View = itemView.findViewById(R.id.iconImageView)

            fun bind(item: HourlyForecastItem) {
                val date = Date(item.dt * 1000)
                val timeFormat = SimpleDateFormat("HH시", Locale.getDefault())
                timeTextView.text = timeFormat.format(date)
                tempTextView.text = "${item.main.temp.toInt()}°"
                // 아이콘은 간단히 표시 (나중에 날씨 아이콘 추가 가능)
            }
        }
    }
}

