package com.example.silentzonefinder_android

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.silentzonefinder_android.databinding.ActivityNewReviewBinding // 1. 바인딩 클래스 import
import java.util.UUID

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

    // 최종 dB 값을 리턴하는 간단한 함수 (Getter 역할)
    private fun getFinalDecibelValue(): Int {
        return finalMeasuredDb
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 툴바 설정 (뒤로가기 버튼 및 타이틀)
        setSupportActionBar(binding.toolbar) // XML에 정의한 Toolbar 사용
        supportActionBar?.apply {
            title = "New Review" // 툴바 타이틀 설정
            setDisplayHomeAsUpEnabled(true) // 뒤로가기 화살표 활성화
        }
        checkAudioPermission()
        setupSubmitButton()
    }

    // 💡 [삭제] 임시값 설정: 실제 구현 시에는 로그인 세션 및 이전 화면에서 전달받은 값 사용 필수
    private val DUMMY_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    private val DUMMY_PLACE_ID = "Kakao_12345"
    private fun setupSubmitButton() {
        binding.btnSubmitReview.setOnClickListener {
            // 1. 별점 값 읽기 (Float 타입)
            val ratingFloat = binding.ratingBar.rating
            // Int 타입으로 변환
            val ratingInt = ratingFloat.toInt()

            // 2. 리뷰 텍스트 읽기
            val reviewText = binding.etReview.text.toString()

            // 4. (TODO: 태그 값 읽기)

            // 5. Room 데이터베이스 저장 함수 호출 시 Int 값을 사용
            // saveReviewData(ratingInt, reviewText, tags)

            // 6. 화면 복귀 호출 (아래 2단계에서 구현)
            returnToPreviousScreen()
        }
    }
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

    private fun returnToPreviousScreen() {
        // RESULT_OK는 작업이 성공적으로 완료되었음을 의미합니다.
        setResult(RESULT_OK)

        // 현재 Activity를 스택에서 제거하여 이전 화면으로 돌아갑니다.
        finish()
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
}
