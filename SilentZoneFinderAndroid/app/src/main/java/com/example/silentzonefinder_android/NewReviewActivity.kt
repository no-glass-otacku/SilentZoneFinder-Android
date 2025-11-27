package com.example.silentzonefinder_android

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.silentzonefinder_android.adapter.ReviewImageAdapter
import com.example.silentzonefinder_android.data.ReviewImage
import com.example.silentzonefinder_android.databinding.ActivityNewReviewBinding
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

private const val STATE_IMAGE_URIS = "state_image_uris"
class NewReviewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNewReviewBinding
    private val REQUEST_RECORD_AUDIO_PERMISSION = 200 // 녹음 권한 요청 코드

    private lateinit var audioRecord: AudioRecord
    private lateinit var measurementThread: Thread
    private var isMeasuring = false

    // AudioRecord 설정값
    private val RECORDER_SAMPLERATE = 8000
    private val RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO
    private val RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING)
    private var finalMeasuredDb: Int = 0 // 측정된 최종 dB 값을 저장할 변수

    //Image
    private lateinit var imageAdapter: ReviewImageAdapter
    private val uploadedImages = mutableListOf<ReviewImage>() // 관리할 이미지 리스트

    // Intent로 받은 장소 정보
    private var kakaoPlaceId: String = ""
    private var placeName: String = ""
    private var address: String = ""
    private var lat: Double? = null
    private var lng: Double? = null
    
    // 수정 모드 관련 변수
    private var reviewId: Long = -1L
    private var isEditMode: Boolean = false

    companion object {
        private const val TAG = "NewReviewActivity"
        private const val EXTRA_KAKAO_PLACE_ID = "extra_kakao_place_id"
        private const val EXTRA_PLACE_NAME = "extra_place_name"
        private const val EXTRA_ADDRESS = "extra_address"
        private const val EXTRA_LAT = "extra_lat"
        private const val EXTRA_LNG = "extra_lng"
        private const val EXTRA_REVIEW_ID = "extra_review_id"

        fun createIntent(
            context: Context,
            kakaoPlaceId: String,
            placeName: String,
            address: String,
            lat: Double? = null,
            lng: Double? = null
        ): Intent {
            return Intent(context, NewReviewActivity::class.java).apply {
                putExtra(EXTRA_KAKAO_PLACE_ID, kakaoPlaceId)
                putExtra(EXTRA_PLACE_NAME, placeName)
                putExtra(EXTRA_ADDRESS, address)
                lat?.let { putExtra(EXTRA_LAT, it) }
                lng?.let { putExtra(EXTRA_LNG, it) }
            }
        }
        
        fun createEditIntent(
            context: Context,
            reviewId: Long,
            kakaoPlaceId: String,
            placeName: String,
            address: String,
            lat: Double? = null,
            lng: Double? = null
        ): Intent {
            return Intent(context, NewReviewActivity::class.java).apply {
                putExtra(EXTRA_REVIEW_ID, reviewId)
                putExtra(EXTRA_KAKAO_PLACE_ID, kakaoPlaceId)
                putExtra(EXTRA_PLACE_NAME, placeName)
                putExtra(EXTRA_ADDRESS, address)
                lat?.let { putExtra(EXTRA_LAT, it) }
                lng?.let { putExtra(EXTRA_LNG, it) }
            }
        }
    }

    // 최종 dB 값을 리턴하는 간단한 함수 (Getter 역할)
    private fun getFinalDecibelValue(): Int {
        return finalMeasuredDb
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //상태 복구 로직 (화면 회전 시 이미지 유지)
        if (savedInstanceState != null) {
            // 1. 저장된 URI 리스트를 가져옵니다.
            val uris = savedInstanceState.getParcelableArrayList<Uri>(STATE_IMAGE_URIS)

            // 2. 리스트가 null이 아니라면 반복문을 통해 복구합니다.
            uris?.forEach { uri ->
                // ReviewImage 객체로 변환하여 리스트에 추가합니다.
                uploadedImages.add(ReviewImage(uri))
            }
        }
        // Intent에서 장소 정보 받기
        reviewId = intent.getLongExtra(EXTRA_REVIEW_ID, -1L)
        isEditMode = reviewId > 0
        
        kakaoPlaceId = intent.getStringExtra(EXTRA_KAKAO_PLACE_ID) ?: ""
        placeName = intent.getStringExtra(EXTRA_PLACE_NAME).orEmpty()
        address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
        lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN).takeIf { !it.isNaN() }
        lng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN).takeIf { !it.isNaN() }

        if (kakaoPlaceId.isBlank()) {
            Toast.makeText(this, getString(R.string.new_review_no_place_info), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 툴바 설정 (뒤로가기 버튼 및 타이틀)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = if (isEditMode) getString(R.string.edit_review_title) else "New Review"
            setDisplayHomeAsUpEnabled(true)
        }
        
        if (isEditMode) {
            // 수정 모드: 기존 리뷰 데이터 로드
            loadExistingReview()
            // 저장 버튼 텍스트 변경
            binding.btnSubmitReview.text = getString(R.string.edit_review_save)
        } else {
            // 새 리뷰 모드: 소음 측정 시작
            checkAudioPermission()
        }
        setupImageRecyclerView()
        setupImageUpload() //image button listener
        setupSubmitButton()
    }

    //Activity가 파괴되기 전에 현재 선택된 이미지 URI 목록을 저장: 이미지 URI 목록을 화면 회전이나 백그라운드 강제 종료로부터 보호하기 위함
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // 현재 uploadedImages 리스트에서 Uri만 추출하여 Bundle에 저장합니다.
        val uriList = ArrayList(uploadedImages.map { it.uri })
        outState.putParcelableArrayList(STATE_IMAGE_URIS, uriList)
    }
    
    private fun loadExistingReview() {
        lifecycleScope.launch {
            try {
                val session = withContext(Dispatchers.IO) {
                    SupabaseManager.client.auth.currentSessionOrNull()
                }
                val userId = session?.user?.id?.toString()
                if (userId == null) {
                    Toast.makeText(this@NewReviewActivity, "로그인이 필요합니다.", Toast.LENGTH_LONG).show()
                    finish()
                    return@launch
                }

                val reviewDto = withContext(Dispatchers.IO) {
                    SupabaseManager.client.postgrest["reviews"]
                        .select {
                            filter {
                                eq("id", reviewId)
                                eq("user_id", userId)
                            }
                        }
                        .decodeSingle<ReviewDto>()
                }

                // 기존 리뷰 데이터로 UI 채우기
                finalMeasuredDb = reviewDto.noiseLevelDb.toInt()
                binding.ratingBar.rating = reviewDto.rating.toFloat()
                binding.etReview.setText(reviewDto.text ?: "")
                
                // 소음 측정 뷰 숨기고 리뷰 작성 뷰로 바로 이동
                binding.noiseMeasurementView.visibility = View.GONE
                binding.reviewWritingView.visibility = View.VISIBLE
                
                // 소음 값 표시 (수정 불가)
                binding.tvFinalDecibel.text = "${finalMeasuredDb}\ndB"
                binding.tvOptimalText.text = getNoiseStatusText(finalMeasuredDb)
                
                // Record Again 버튼 숨기기 (수정 모드에서는 소음 수정 불가)
                binding.btnRecordAgain.visibility = View.GONE
                
                // "Add Your Review" 텍스트를 찾아서 변경 (선택사항)
                try {
                    val reviewTitleView = binding.root.findViewById<android.widget.TextView>(R.id.reviewTitleTextView)
                    reviewTitleView?.text = getString(R.string.edit_review_title)
                } catch (e: Exception) {
                    // TextView가 없어도 계속 진행
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load review", e)
                Toast.makeText(this@NewReviewActivity, getString(R.string.new_review_load_failed), Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
    
    @Serializable
    private data class ReviewDto(
        val id: Long,
        @SerialName("kakao_place_id") val kakaoPlaceId: String,
        val rating: Int,
        val text: String?,
        @SerialName("noise_level_db") val noiseLevelDb: Double
    )
    private fun setupSubmitButton() {
        binding.btnSubmitReview.setOnClickListener {
            // 1. 별점 값 읽기
            val ratingFloat = binding.ratingBar.rating
            val ratingInt = ratingFloat.toInt().coerceIn(1, 5)

            // 2. 리뷰 텍스트 읽기
            val reviewText = binding.etReview.text.toString().trim()

            // 3. 측정된 dB 값
            val noiseLevelDb = finalMeasuredDb.toDouble()

            // 4. 유효성 검사
            if (ratingInt == 0) {
                Toast.makeText(this, getString(R.string.new_review_error_rating), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isEditMode && noiseLevelDb <= 0) {
                Toast.makeText(this, getString(R.string.new_review_error_noise), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 5. 리뷰 저장 또는 수정
            if (isEditMode) {
                updateReviewToSupabase(ratingInt, reviewText, noiseLevelDb)
            } else {
                saveReviewToSupabase(ratingInt, reviewText, noiseLevelDb)
            }
        }
    }

    private fun saveReviewToSupabase(rating: Int, text: String, noiseLevelDb: Double) {
        lifecycleScope.launch {
            binding.btnSubmitReview.isEnabled = false
            // ProgressBar가 있다면 표시 (레이아웃에 없을 수 있음)
            try {
                val progressBar = binding.root.findViewById<View>(R.id.progressBar)
                progressBar?.isVisible = true
            } catch (e: Exception) {
                // ProgressBar가 없어도 계속 진행
            }

            try {
                // 1. 현재 로그인한 사용자 ID 가져오기
                val currentSession = withContext(Dispatchers.IO) {
                    try {
                        SupabaseManager.client.auth.currentSessionOrNull()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get current session", e)
                        null
                    }
                }

                if (currentSession == null) {
                    Toast.makeText(this@NewReviewActivity, "로그인이 필요합니다.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val user = currentSession.user
                if (user == null) {
                    Toast.makeText(this@NewReviewActivity, "로그인이 필요합니다.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val userId = user.id.toString()

                // 2. places 테이블에 장소 정보 upsert
                withContext(Dispatchers.IO) {
                    try {
                        val placeData = buildMap<String, Any> {
                            put("kakao_place_id", kakaoPlaceId)
                            put("name", placeName)
                            put("address", address)
                            lat?.let { put("lat", it) }
                            lng?.let { put("lng", it) }
                        }

                        SupabaseManager.client.postgrest["places"]
                            .upsert(placeData) {
                                onConflict = "kakao_place_id"
                            }
                        Log.d(TAG, "Place upserted: $kakaoPlaceId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to upsert place", e)
                        // places 테이블 upsert 실패해도 리뷰 저장은 계속 진행
                    }
                }

                // 이미지 업로드 및 URL 수집
                val imagesJson: String? = withContext(Dispatchers.IO) {
                    val urls = mutableListOf<String>()

                    // 선택된 이미지 리스트(uploadedImages)를 하나씩 업로드
                    for (image in uploadedImages) {
                        val url = uploadImageToSupabase(image)
                        if (url != null) {
                            urls.add(url)
                        }
                    }

                    // URL 리스트를 JSON 문자열로 변환 (예: ["url1", "url2"])
                    if (urls.isNotEmpty()) {
                        urls.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]")
                    } else {
                        null // 이미지가 없으면 null
                    }
                }

                // 3. reviews 테이블에 리뷰 저장
                val reviewData = ReviewInsertDto(
                    kakaoPlaceId = kakaoPlaceId,
                    userId = userId,
                    rating = rating,
                    text = text,
                    images = imagesJson, // 업로드된 URL 목록
                    noiseLevelDb = noiseLevelDb
                )

                withContext(Dispatchers.IO) {
                    SupabaseManager.client.postgrest["reviews"]
                        .insert(reviewData)
                }

                Log.d(TAG, "Review saved successfully")
                Toast.makeText(this@NewReviewActivity, getString(R.string.new_review_save_success), Toast.LENGTH_SHORT).show()

                // 4. 성공 시 이전 화면으로 복귀
                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save review", e)
                Toast.makeText(
                    this@NewReviewActivity,
                    getString(R.string.new_review_save_failure, e.message ?: "-"),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.btnSubmitReview.isEnabled = true
                // ProgressBar가 있다면 숨김
                try {
                    val progressBar = binding.root.findViewById<View>(R.id.progressBar)
                    progressBar?.isVisible = false
                } catch (e: Exception) {
                    // ProgressBar가 없어도 계속 진행
                }
            }
        }
    }
    
    private fun updateReviewToSupabase(rating: Int, text: String, noiseLevelDb: Double) {
        lifecycleScope.launch {
            binding.btnSubmitReview.isEnabled = false
            try {
                val progressBar = binding.root.findViewById<View>(R.id.progressBar)
                progressBar?.isVisible = true
            } catch (e: Exception) {
                // ProgressBar가 없어도 계속 진행
            }

            try {
                val currentSession = withContext(Dispatchers.IO) {
                    try {
                        SupabaseManager.client.auth.currentSessionOrNull()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get current session", e)
                        null
                    }
                }

                if (currentSession == null) {
                    Toast.makeText(this@NewReviewActivity, "로그인이 필요합니다.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val user = currentSession.user
                if (user == null) {
                    Toast.makeText(this@NewReviewActivity, "로그인이 필요합니다.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val userId = user.id.toString()

                // 리뷰 업데이트 (소음은 수정 불가이므로 기존 값 유지)
                withContext(Dispatchers.IO) {
                    SupabaseManager.client.postgrest["reviews"].update(
                        mapOf(
                            "rating" to rating,
                            "text" to text
                            // noise_level_db는 수정하지 않음
                        )
                    ) {
                        filter {
                            eq("id", reviewId)
                            eq("user_id", userId)
                        }
                    }
                }

                Log.d(TAG, "Review updated successfully")
                Toast.makeText(this@NewReviewActivity, getString(R.string.new_review_update_success), Toast.LENGTH_SHORT).show()

                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to update review", e)
                Toast.makeText(
                    this@NewReviewActivity,
                    getString(R.string.new_review_update_failure, e.message ?: "-"),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.btnSubmitReview.isEnabled = true
                // ProgressBar가 있다면 숨김
                try {
                    val progressBar = binding.root.findViewById<View>(R.id.progressBar)
                    progressBar?.isVisible = false
                } catch (e: Exception) {
                    // ProgressBar가 없어도 계속 진행
                }
            }
        }
    }

    @Serializable
    private data class ReviewInsertDto(
        @SerialName("kakao_place_id") val kakaoPlaceId: String,
        @SerialName("user_id") val userId: String,
        val rating: Int,
        val text: String,
        val images: String? = null,
        @SerialName("noise_level_db") val noiseLevelDb: Double
    )
    private fun getSelectedAmenities(): String {
        val selectedChips = mutableListOf<String>()

        // ChipGroup의 모든 체크된 Chip ID를 가져옵니다.
        val checkedChipIds = binding.chipGroupAmenities.checkedChipIds

        for (id in checkedChipIds) {
            // ID를 기반으로 Chip 객체를 찾습니다.
            // findViewById를 호출할 때 MaterialChip 타입을 명시하는 것이 더 안전합니다.
            val chip = binding.chipGroupAmenities.findViewById<com.google.android.material.chip.Chip>(id)
            chip?.let {
                selectedChips.add(it.text.toString())
            }
        }
        // "Wi-Fi, AC, Coffee"와 같이 쉼표로 구분된 하나의 문자열로 반환
        return selectedChips.joinToString(", ")
    }


    // 툴바의 뒤로가기 버튼 클릭 이벤트 처리
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish() // 현재 Activity 종료
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // 권한이 없으면 요청
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        } else {
            // 권한이 이미 있다면 소음 측정 준비 (3단계에서 구현)
            setupNoiseMeasurement() //-> btnStartRecording 버튼에 클릭 리스너를 설정
        }
    }

    // 권한 요청 결과 처리
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupNoiseMeasurement()
            } else {
                // 권한 거부 시 사용자에게 설명하거나 Activity 종료
                Toast.makeText(this, "마이크 권한이 필요합니다.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
    // ✅ 이 어노테이션을 함수 위에 추가하여 권한 관련 경고를 무시하도록 합니다.
    @SuppressLint("MissingPermission")
    private fun setupNoiseMeasurement() {
        binding.btnStartRecording.setOnClickListener {
            if (!isMeasuring) {
                // 이 버튼이 눌릴 일은 거의 없지만, 안전을 위해 남겨둡니다.
                startMeasurement()
            } else {
                // 사용자가 측정을 중간에 멈추고 싶을 때를 위해 필요합니다.
                stopMeasurement()
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startMeasurement() {
        // 권한 확인이 완료되었을 때만 호출
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORDER_SAMPLERATE,
            RECORDER_CHANNELS,
            RECORDER_AUDIO_ENCODING,
            BUFFER_SIZE
        )

        audioRecord.startRecording()
        isMeasuring = true
        binding.btnStartRecording.text = "Stop Recording"

        measurementThread = Thread {
            val buffer = ShortArray(BUFFER_SIZE)
            while (isMeasuring) {
                audioRecord.read(buffer, 0, BUFFER_SIZE)
                val maxAmplitude = buffer.maxOrNull()?.toDouble() ?: 0.0

                // 데시벨(dB) 계산: 20 * log10(진폭 / 기준값)
                // 기준값(Reference Amplitude)은 보통 1로 설정하거나, 마이크 최대값/정규화 값 사용
                val db = if (maxAmplitude > 0) {
                    // 상수로 보정하면 더 정확하지만, 여기서는 단순화하여 계산
                    20.0 * Math.log10(maxAmplitude)
                } else {
                    0.0
                }
                finalMeasuredDb = db.toInt()
                // UI 업데이트는 반드시 메인 스레드에서!
                runOnUiThread {
                    updateDecibelUI(db)
                }
                Thread.sleep(50) // 50ms 마다 업데이트
            }
        }
        measurementThread.start()
    }

    //UI 업데이트 함수
    private fun updateDecibelUI(dbValue: Double) {
        val roundedDb = String.format("%.0f", dbValue)
        binding.tvDecibelValue.text = "${roundedDb}\ndB"
        // 3단계의 게이지 애니메이션 로직 호출
        // dB 값을 0~100 사이의 ProgressBar 값으로 변환
        // (예시: 30dB -> 30, 80dB -> 80. 실제 앱에 맞게 정규화 필요)
        val progressValue = dbValue.coerceIn(0.0, 100.0).toInt()

        // ProgressBar 업데이트 (애니메이션을 추가하여 부드럽게 보이도록 함)
        binding.noiseProgressBar.progress = progressValue

        // 만약 애니메이션이 필요하다면:
         ObjectAnimator.ofInt(binding.noiseProgressBar, "progress", progressValue)
             .setDuration(50)
             .start()
    }

    private fun stopMeasurement() {
        // 측정 중단 플래그 설정
        isMeasuring = false
        // AudioRecord 리소스 해제
        if (::audioRecord.isInitialized) {
            audioRecord.stop()
            audioRecord.release()
        }
        // UI 버튼 텍스트 변경 (측정 전 상태로 복귀)
        binding.btnStartRecording.text = "Start Recording"
        // 최종 측정된 dB 값을 가져옵니다.
        //    이 값은 측정 스레드(measurementThread)에서 최종적으로 기록한 값이어야 합니다.
        //    (아래 예시에서는 finalMeasuredDb라는 변수를 사용한다고 가정합니다.)
        val finalDbValue = getFinalDecibelValue() // 💡 이 함수는 실제 측정 로직에 따라 구현해야 함

        // 화면 전환 함수 호출
        switchToReviewWritingView(finalDbValue)

        // (선택적) 측정 스레드 종료 대기 (안전성 확보)
        measurementThread.join()
    }

    // 🌟 함수 1: 화면 전환 및 데이터 표시 (이 함수를 호출하여 화면을 바꿉니다.) 🌟
    private fun switchToReviewWritingView(dbValue: Int) {
        // 1. 소음 측정 뷰 숨기기
        binding.noiseMeasurementView.visibility = View.GONE

        // 2. 리뷰 작성 뷰 보이기
        binding.reviewWritingView.visibility = View.VISIBLE

        // 3. 리뷰 작성 뷰에 측정된 최종 dB 값 표시
        // (이 예시에서는 리뷰 작성 뷰 내부의 TextView ID가 tvFinalDecibel 이라고 가정)
        binding.tvFinalDecibel.text = "${dbValue}\ndB"
        binding.tvOptimalText.text = getNoiseStatusText(dbValue)

        // **(추가)** Record Again 버튼 클릭 리스너 설정
        binding.btnRecordAgain.setOnClickListener {
            switchToNoiseMeasurementView()
        }
    }

    // 🌟 함수 2: 다시 측정 화면으로 돌아가기 (선택적) 🌟
    private fun switchToNoiseMeasurementView() {
        binding.reviewWritingView.visibility = View.GONE
        binding.noiseMeasurementView.visibility = View.VISIBLE
        // dB 값 초기화 등 추가 로직 ->?
        binding.tvDecibelValue.text = "--\ndB" // 측정 전 상태로 되돌림 (이전 뷰의 ID 사용)
    }

    // 🌟 함수 3: dB 값에 따른 텍스트 반환 🌟
    private fun getNoiseStatusText(db: Int): String {
        return when {
            db <= 45 -> "Library Quiet"
            db <= 58 -> "Quiet Conversation"
            db <= 70 -> "Lively Chatter"
            else -> "High Traffic"
        }
    }

    // 갤러리 Intent를 실행하고 결과를 처리하는 Launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // 이미지 선택 성공
            val uri = result.data?.data
            if (uri != null) {
                val newImage = ReviewImage(uri)
                //전체 뷰를 다시 그릴 필요 없이 해당 위치의 뷰만 업데이트
                uploadedImages.add(newImage) // 1. 데이터 리스트에 데이터 추가
                imageAdapter.notifyItemInserted(uploadedImages.size - 1) // 2. UI에 데이터 추가를 알림
            }
        }
    }

    private fun openGallery() {
        // Intent.ACTION_PICK을 사용하여 갤러리를 엽니다.
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*" // 이미지 타입만 필터링
        }
        imagePickerLauncher.launch(intent)
    }
    private fun setupImageUpload() {
        binding.btnAddImage.setOnClickListener {
            openGallery()
        }
    }

    private fun setupImageRecyclerView() {
        //RecyclerView 초기화 함수

        imageAdapter = ReviewImageAdapter(uploadedImages) { imageToDelete ->
            // 이미지 삭제 로직 (Delete 버튼 클릭 시 호출됨)
            val position = uploadedImages.indexOf(imageToDelete)
            if (position != -1) {
                uploadedImages.removeAt(position)
                imageAdapter.notifyItemRemoved(position)
            }
        }

        // RecyclerView 설정
        binding.rvImages.adapter = imageAdapter
        // (LayoutManager는 XML에서 이미 설정했습니다.)
    }

    private suspend fun uploadImageToSupabase(image: ReviewImage): String? {
        val storage = SupabaseManager.client.storage
        val bucketName = "review-images"

        val fileName = "${UUID.randomUUID()}.jpg"

        return try {
            val bytes = contentResolver.openInputStream(image.uri)?.use { it.readBytes() }

            if (bytes == null) {
                Log.e("SupabaseUpload", "이미지 데이터를 읽을 수 없습니다.")
                return null
            }

            storage.from(bucketName).upload(
                path = fileName,
                data = bytes
            ) {
                upsert = false
            }

            // 공개 URL 가져오기
            val publicUrl = storage.from(bucketName).publicUrl(fileName)

            Log.d("SupabaseUpload", "Image uploaded: $publicUrl")
            publicUrl

        } catch (e: Exception) {
            Log.e("SupabaseUpload", "이미지 업로드 실패: ${e.message}")
            null
        }
    }
}
