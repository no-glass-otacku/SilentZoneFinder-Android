package com.example.silentzonefinder_android

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.silentzonefinder_android.databinding.ActivityNoiseThresholdBinding
import com.google.android.material.slider.Slider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

class NoiseThresholdActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoiseThresholdBinding
    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("notification_prefs", MODE_PRIVATE)
    }

    @Serializable
    private data class FavoriteDto(
        @SerialName("user_id") val userId: String,
        @SerialName("kakao_place_id") val kakaoPlaceId: String,
        @SerialName("alert_threshold_db") val alertThresholdDb: Double? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoiseThresholdBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadCurrentThreshold()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSave.setOnClickListener {
            saveThreshold()
        }

        // 슬라이더 값 변경 시 텍스트 업데이트
        binding.sliderThreshold.addOnChangeListener { _: Slider, value: Float, _: Boolean ->
            binding.tvThresholdValue.text = "${value.toInt()} dB"
            binding.tvDescription.text = getString(
                R.string.noise_threshold_description,
                value.toInt()
            )
        }
    }

    private fun loadCurrentThreshold() {
        lifecycleScope.launch {
            try {
                val session = withContext(Dispatchers.IO) {
                    SupabaseManager.client.auth.currentSessionOrNull()
                }

                if (session == null) {
                    // 로그인하지 않은 경우 기본값 사용
                    val defaultThreshold = prefs.getFloat("default_threshold", 65f).toDouble()
                    binding.sliderThreshold.value = defaultThreshold.toFloat()
                    binding.tvThresholdValue.text = "${defaultThreshold.toInt()} dB"
                    return@launch
                }

                val userId = session.user?.id?.toString().orEmpty()
                if (userId.isEmpty()) {
                    val defaultThreshold = prefs.getFloat("default_threshold", 65f).toDouble()
                    binding.sliderThreshold.value = defaultThreshold.toFloat()
                    binding.tvThresholdValue.text = "${defaultThreshold.toInt()} dB"
                    return@launch
                }

                // 즐겨찾기에서 임계값 가져오기 (가장 최근 설정값 사용)
                val favorites = withContext(Dispatchers.IO) {
                    try {
                        SupabaseManager.client.postgrest["favorites"]
                            .select(Columns.ALL) {
                                filter {
                                    eq("user_id", userId)
                                }
                            }
                            .decodeList<FavoriteDto>()
                    } catch (e: Exception) {
                        emptyList<FavoriteDto>()
                    }
                }

                val threshold = favorites
                    .mapNotNull { it.alertThresholdDb }
                    .maxOrNull() ?: prefs.getFloat("default_threshold", 65f).toDouble()

                binding.sliderThreshold.value = threshold.toFloat()
                binding.tvThresholdValue.text = "${threshold.toInt()} dB"
                binding.tvDescription.text = getString(
                    R.string.noise_threshold_description,
                    threshold.toInt()
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this@NoiseThresholdActivity,
                    "임계값 로드 실패: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun saveThreshold() {
        val threshold = binding.sliderThreshold.value.toDouble()

        lifecycleScope.launch {
            try {
                val session = withContext(Dispatchers.IO) {
                    SupabaseManager.client.auth.currentSessionOrNull()
                }

                if (session == null) {
                    // 로그인하지 않은 경우 로컬에만 저장
                    prefs.edit().putFloat("default_threshold", threshold.toFloat()).apply()
                    Toast.makeText(
                        this@NoiseThresholdActivity,
                        "임계값이 저장되었습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return@launch
                }

                val userId = session.user?.id?.toString().orEmpty()
                if (userId.isEmpty()) {
                    prefs.edit().putFloat("default_threshold", threshold.toFloat()).apply()
                    Toast.makeText(
                        this@NoiseThresholdActivity,
                        "임계값이 저장되었습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return@launch
                }

                // 모든 즐겨찾기에 임계값 업데이트
                val favorites = withContext(Dispatchers.IO) {
                    try {
                        SupabaseManager.client.postgrest["favorites"]
                            .select(Columns.ALL) {
                                filter {
                                    eq("user_id", userId)
                                }
                            }
                            .decodeList<FavoriteDto>()
                    } catch (e: Exception) {
                        emptyList<FavoriteDto>()
                    }
                }

                // 각 즐겨찾기에 임계값 업데이트
                favorites.forEach { favorite ->
                    withContext(Dispatchers.IO) {
                        try {
                            SupabaseManager.client.postgrest["favorites"]
                                .update(mapOf("alert_threshold_db" to threshold)) {
                                    filter {
                                        eq("user_id", userId)
                                        eq("kakao_place_id", favorite.kakaoPlaceId)
                                    }
                                }
                        } catch (e: Exception) {
                            // 개별 업데이트 실패는 무시
                        }
                    }
                }

                // 기본값도 저장
                prefs.edit().putFloat("default_threshold", threshold.toFloat()).apply()

                Toast.makeText(
                    this@NoiseThresholdActivity,
                    "임계값이 저장되었습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@NoiseThresholdActivity,
                    "임계값 저장 실패: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

