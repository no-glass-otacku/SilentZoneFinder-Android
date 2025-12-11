package com.example.silentzonefinder_android.service

import com.example.silentzonefinder_android.BuildConfig
import com.example.silentzonefinder_android.data.AirQualityResponse
import com.example.silentzonefinder_android.data.HourlyForecastResponse
import com.example.silentzonefinder_android.data.WeatherResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class WeatherService(private val httpClient: HttpClient) {
    
    companion object {
        private const val BASE_URL = "https://api.openweathermap.org/data/2.5"
        private const val AIR_QUALITY_URL = "https://api.openweathermap.org/data/2.5/air_pollution"
    }
    
    suspend fun getCurrentWeather(lat: Double, lon: Double): Result<WeatherResponse> {
        return try {
            val apiKey = BuildConfig.OPENWEATHERMAP_API_KEY
            if (apiKey.isEmpty()) {
                return Result.failure(Exception("OpenWeatherMap API key is not configured"))
            }
            
            val response = httpClient.get("$BASE_URL/weather") {
                parameter("lat", lat)
                parameter("lon", lon)
                parameter("appid", apiKey)
                parameter("units", "metric") // 섭씨 온도
                parameter("lang", "kr") // 한국어
            }.body<WeatherResponse>()
            
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getAirQuality(lat: Double, lon: Double): Result<AirQualityResponse> {
        return try {
            val apiKey = BuildConfig.OPENWEATHERMAP_API_KEY
            if (apiKey.isEmpty()) {
                return Result.failure(Exception("OpenWeatherMap API key is not configured"))
            }
            
            val response = httpClient.get(AIR_QUALITY_URL) {
                parameter("lat", lat)
                parameter("lon", lon)
                parameter("appid", apiKey)
            }.body<AirQualityResponse>()
            
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getHourlyForecast(lat: Double, lon: Double): Result<HourlyForecastResponse> {
        return try {
            val apiKey = BuildConfig.OPENWEATHERMAP_API_KEY
            if (apiKey.isEmpty()) {
                return Result.failure(Exception("OpenWeatherMap API key is not configured"))
            }
            
            val response = httpClient.get("$BASE_URL/forecast") {
                parameter("lat", lat)
                parameter("lon", lon)
                parameter("appid", apiKey)
                parameter("units", "metric")
                parameter("lang", "kr")
            }.body<HourlyForecastResponse>()
            
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}






