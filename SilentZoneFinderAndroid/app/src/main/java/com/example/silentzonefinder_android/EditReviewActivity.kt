package com.example.silentzonefinder_android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.example.silentzonefinder_android.databinding.ActivityEditReviewBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class EditReviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditReviewBinding

    private var reviewId: Long = -1L
    private var kakaoPlaceId: String = ""
    private var placeName: String = ""
    private var placeAddress: String = ""
    private var initialRating: Int = 0
    private var initialNoiseLevel: Double = 0.0
    private var initialReviewText: String = ""

    companion object {
        private const val EXTRA_REVIEW_ID = "extra_review_id"
        private const val EXTRA_KAKAO_PLACE_ID = "extra_kakao_place_id"
        private const val EXTRA_PLACE_NAME = "extra_place_name"
        private const val EXTRA_PLACE_ADDRESS = "extra_place_address"
        private const val EXTRA_RATING = "extra_rating"
        private const val EXTRA_NOISE_LEVEL = "extra_noise_level"
        private const val EXTRA_REVIEW_TEXT = "extra_review_text"

        fun createIntent(
            context: Context,
            reviewId: Long,
            kakaoPlaceId: String,
            placeName: String,
            placeAddress: String,
            rating: Int,
            noiseLevelDb: Double,
            reviewText: String
        ): Intent {
            return Intent(context, EditReviewActivity::class.java).apply {
                putExtra(EXTRA_REVIEW_ID, reviewId)
                putExtra(EXTRA_KAKAO_PLACE_ID, kakaoPlaceId)
                putExtra(EXTRA_PLACE_NAME, placeName)
                putExtra(EXTRA_PLACE_ADDRESS, placeAddress)
                putExtra(EXTRA_RATING, rating)
                putExtra(EXTRA_NOISE_LEVEL, noiseLevelDb)
                putExtra(EXTRA_REVIEW_TEXT, reviewText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        parseExtrasOrFinish()
        setupToolbar()
        populateInitialValues()
        setupListeners()
    }

    private fun parseExtrasOrFinish() {
        reviewId = intent.getLongExtra(EXTRA_REVIEW_ID, -1L)
        kakaoPlaceId = intent.getStringExtra(EXTRA_KAKAO_PLACE_ID).orEmpty()
        placeName = intent.getStringExtra(EXTRA_PLACE_NAME).orEmpty()
        placeAddress = intent.getStringExtra(EXTRA_PLACE_ADDRESS).orEmpty()
        initialRating = intent.getIntExtra(EXTRA_RATING, 0)
        initialNoiseLevel = intent.getDoubleExtra(EXTRA_NOISE_LEVEL, 0.0)
        initialReviewText = intent.getStringExtra(EXTRA_REVIEW_TEXT).orEmpty()

        if (reviewId <= 0 || kakaoPlaceId.isBlank()) {
            Toast.makeText(this, R.string.edit_review_invalid_data, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun populateInitialValues() {
        binding.tvPlaceName.text = placeName
        binding.tvPlaceAddress.text = placeAddress.ifBlank { getString(R.string.edit_review_no_address) }
        binding.ratingBar.rating = initialRating.coerceIn(0, 5).toFloat()
        if (initialNoiseLevel > 0) {
            binding.etNoiseLevel.setText(initialNoiseLevel.roundToInt().toString())
        }
        binding.etReviewText.setText(initialReviewText)
        updateNoiseStatusLabel(initialNoiseLevel)
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener { attemptSave() }
        binding.etNoiseLevel.addTextChangedListener { editable ->
            val noise = editable?.toString()?.toDoubleOrNull()
            updateNoiseStatusLabel(noise)
        }
    }

    private fun attemptSave() {
        val rating = binding.ratingBar.rating.toInt().coerceIn(1, 5)
        val reviewText = binding.etReviewText.text?.toString()?.trim().orEmpty()
        val noiseLevel = binding.etNoiseLevel.text?.toString()?.toDoubleOrNull()

        if (rating <= 0) {
            Toast.makeText(this, R.string.edit_review_error_rating, Toast.LENGTH_SHORT).show()
            return
        }

        if (noiseLevel == null || noiseLevel <= 0) {
            Toast.makeText(this, R.string.edit_review_error_noise, Toast.LENGTH_SHORT).show()
            return
        }

        if (reviewText.isBlank()) {
            Toast.makeText(this, R.string.edit_review_error_text, Toast.LENGTH_SHORT).show()
            return
        }

        updateReview(rating, reviewText, noiseLevel)
    }

    private fun updateReview(rating: Int, reviewText: String, noiseLevel: Double) {
        lifecycleScope.launch {
            setLoading(true)
            try {
                val session = withContext(Dispatchers.IO) {
                    SupabaseManager.client.auth.currentSessionOrNull()
                }

                val userId = session?.user?.id?.toString()
                if (userId == null) {
                    Toast.makeText(this@EditReviewActivity, R.string.edit_review_login_required, Toast.LENGTH_LONG).show()
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    SupabaseManager.client.postgrest["reviews"].update(
                        mapOf(
                            "rating" to rating,
                            "text" to reviewText,
                            "noise_level_db" to noiseLevel
                        )
                    ) {
                        filter {
                            eq("id", reviewId)
                            eq("user_id", userId)
                        }
                    }
                }

                Toast.makeText(this@EditReviewActivity, R.string.edit_review_success, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@EditReviewActivity,
                    getString(R.string.edit_review_failure, e.message ?: "-"),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun updateNoiseStatusLabel(noise: Double?) {
        val status = when {
            noise == null || noise <= 0 -> getString(R.string.edit_review_noise_hint)
            noise <= 45 -> getString(R.string.noise_level_optimal)
            noise <= 55 -> getString(R.string.noise_level_good)
            noise <= 65 -> getString(R.string.noise_level_normal)
            else -> getString(R.string.noise_level_loud)
        }
        binding.tvNoiseStatus.text = status
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.isVisible = isLoading
        binding.btnSave.isEnabled = !isLoading
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}



















