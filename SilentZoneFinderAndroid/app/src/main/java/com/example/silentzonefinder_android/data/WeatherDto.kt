package com.example.silentzonefinder_android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
data class WeatherResponse(
    val coord: Coordinates,
    val weather: List<Weather>,
    val base: String,
    val main: MainWeather,
    val visibility: Int,
    val wind: Wind? = null,
    val clouds: Clouds? = null,
    val dt: Long,
    val sys: Sys,
    val timezone: Int,
    val id: Int,
    val name: String,
    val cod: Int
)

@Serializable
data class Coordinates(
    val lon: Double,
    val lat: Double
)

@Serializable
data class Weather(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
)

@Serializable
data class MainWeather(
    val temp: Double,
    @SerialName("feels_like") val feelsLike: Double,
    @SerialName("temp_min") val tempMin: Double,
    @SerialName("temp_max") val tempMax: Double,
    val pressure: Int,
    val humidity: Int
)

@Serializable
data class Wind(
    val speed: Double,
    val deg: Int? = null
)

@Serializable
data class Clouds(
    val all: Int
)

@Serializable
data class Sys(
    val type: Int? = null,
    val id: Int? = null,
    val country: String,
    val sunrise: Long? = null,
    val sunset: Long? = null
)

@Serializable
data class AirQualityResponse(
    val coord: Coordinates,
    val list: List<AirQualityData>
)

@Serializable
data class AirQualityData(
    val main: AirQualityMain,
    val components: AirQualityComponents,
    val dt: Long
)

@Serializable
data class AirQualityMain(
    val aqi: Int // 1=Good, 2=Fair, 3=Moderate, 4=Poor, 5=Very Poor
)

@Serializable
data class AirQualityComponents(
    val co: Double? = null,
    val no: Double? = null,
    val no2: Double? = null,
    val o3: Double? = null,
    val so2: Double? = null,
    @SerialName("pm2_5") val pm25: Double? = null,
    @SerialName("pm10") val pm10: Double? = null,
    val nh3: Double? = null
)

@Serializable
data class HourlyForecastResponse(
    val cod: String,
    val message: Int,
    val cnt: Int,
    val list: List<HourlyForecastItem>,
    val city: City
)

@Serializable
data class HourlyForecastItem(
    val dt: Long,
    val main: MainWeather,
    val weather: List<Weather>,
    val clouds: Clouds? = null,
    val wind: Wind? = null,
    val visibility: Int? = null,
    @SerialName("pop") val probabilityOfPrecipitation: Double? = null,
    @SerialName("dt_txt") val dtTxt: String? = null
)

@Serializable
data class City(
    val id: Int,
    val name: String,
    val coord: Coordinates,
    val country: String,
    val population: Int,
    val timezone: Int,
    val sunrise: Long,
    val sunset: Long
)

// 일별 예보 아이템 (시간대별 예보를 일별로 그룹화한 데이터)
data class DailyForecastItem(
    val date: Date,
    val minTemp: Double,
    val maxTemp: Double,
    val weather: Weather,
    val dt: Long
)



