package com.example.silentzonefinder_android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.silentzonefinder_android.databinding.ActivityProfileBinding
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.storage.storage
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.load
import io.ktor.http.ContentType
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import android.net.Uri

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    // Supabase 클라이언트 (에러 나면 null 로 처리)
    private val supabase: SupabaseClient? by lazy {
        try {
            SupabaseManager.client
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Supabase 초기화 실패", e)
            null
        }
    }

    // 간단한 로그인 상태 저장용 (이메일만 저장)
    private val prefs by lazy {
        getSharedPreferences("auth_prefs", MODE_PRIVATE)
    }

    private lateinit var pickImageLauncher: ActivityResultLauncher<String>

    companion object {
        private val EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        )
        private const val MIN_PASSWORD_LENGTH = 6
        private const val PREF_KEY_EMAIL = "user_email"

        private const val PREF_KEY_PROFILE_IMAGE_URL = "profile_image_url"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 프로필 이미지용 런처 초기화
        initImagePickLaunchers()

        setupBottomNavigation()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        // SharedPreferences에 저장된 로그인 상태 확인
        checkLoginStatus()
        // SharedPreferences에 저장된 프로필 이미지 URL로 다시 로드
        loadProfileImageFromSupabase()
    }


    // -----------------------------
    // UI 상태 전환 (로그인 / 비로그인)
    // -----------------------------
    private fun showLoginLayout() {
        binding.loginLayout.visibility = View.VISIBLE
        binding.loggedInLayout.visibility = View.GONE
    }

    private fun showLoggedInLayout(name: String, email: String) {
        binding.loginLayout.visibility = View.GONE
        binding.loggedInLayout.visibility = View.VISIBLE

        binding.textUserName.text = name
        binding.textUserEmail.text = email

        // 현재는 더미 숫자. 추후 실제 데이터로 바꾸면 됨
        binding.textReviewCount.text = "12"
        binding.textOptimalCount.text = "8"
    }

    private fun checkLoginStatus() {
        val email = prefs.getString("user_email", null)
        if (email.isNullOrEmpty()) {
            showLoginLayout()
        } else {
            val name = email.substringBefore("@")
            showLoggedInLayout(name, email)
        }
    }

    // -----------------------------
    // 버튼 리스너
    // -----------------------------
    private fun setupClickListeners() = with(binding) {

        // 로그인 버튼
        btnLogin.setOnClickListener {
            val email = inputEmail.text.toString().trim()
            val password = inputPassword.text.toString()

            if (!isValidEmail(email) || !isValidPassword(password)) return@setOnClickListener

            val client = supabase ?: run {
                Toast.makeText(
                    this@ProfileActivity,
                    "Supabase가 설정되지 않았습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    client.auth.signInWith(Email) {
                        this.email = email
                        this.password = password
                    }
                    // 로그인 성공
                    saveLoggedInUser(email)
                    val name = email.substringBefore("@")
                    showLoggedInLayout(name, email)
                    Toast.makeText(this@ProfileActivity, "로그인되었습니다.", Toast.LENGTH_SHORT).show()
                } catch (e: RestException) {
                    Toast.makeText(
                        this@ProfileActivity,
                        "이메일 또는 비밀번호를 확인해주세요.",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: HttpRequestException) {
                    Toast.makeText(
                        this@ProfileActivity,
                        "네트워크 오류로 로그인에 실패했습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Log.e("ProfileActivity", "login error", e)
                    Toast.makeText(
                        this@ProfileActivity,
                        "알 수 없는 오류가 발생했습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // 회원가입 버튼
        btnSignUp.setOnClickListener {
            val email = inputEmail.text.toString().trim()
            val password = inputPassword.text.toString()

            if (!isValidEmail(email) || !isValidPassword(password)) return@setOnClickListener

            val client = supabase ?: run {
                Toast.makeText(
                    this@ProfileActivity,
                    "Supabase가 설정되지 않았습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    client.auth.signUpWith(Email) {
                        this.email = email
                        this.password = password
                    }
                    Toast.makeText(
                        this@ProfileActivity,
                        "회원가입이 완료되었습니다. 다시 로그인해주세요.",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: RestException) {
                    Toast.makeText(
                        this@ProfileActivity,
                        "이미 가입된 이메일이거나 요청을 처리할 수 없습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: HttpRequestException) {
                    Toast.makeText(
                        this@ProfileActivity,
                        "네트워크 오류로 회원가입에 실패했습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Log.e("ProfileActivity", "signUp error", e)
                    Toast.makeText(
                        this@ProfileActivity,
                        "알 수 없는 오류가 발생했습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // 알림 스위치 (예시: 단순 토스트)
        switchQuietAlert.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Toast.makeText(this@ProfileActivity, "조용한 장소 알림이 켜졌습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@ProfileActivity, "조용한 장소 알림이 꺼졌습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // 설정 화면 이동
        rowSettings.setOnClickListener {
            val intent = Intent(this@ProfileActivity, SettingsActivity::class.java)
            startActivity(intent)
        }

        // 프로필 사진 변경 버튼
        btnChangeAvatar.setOnClickListener {
            showImageSourceDialog()
        }

        // 로그아웃
        textSignOut.setOnClickListener {
            performLogout()
        }
    }

    private fun performLogout() {
        lifecycleScope.launch {
            try {
                supabase?.auth?.signOut()
            } catch (e: Exception) {
                Log.e("ProfileActivity", "signOut error", e)
            } finally {
                clearLoggedInUser()
                showLoginLayout()
                Toast.makeText(this@ProfileActivity, "로그아웃되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // -----------------------------
    // 유효성 검사 + 로그인 정보 저장
    // -----------------------------
    private fun isValidEmail(email: String): Boolean {
        if (email.isBlank()) {
            Toast.makeText(this, "이메일을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            Toast.makeText(this, "이메일 형식이 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun isValidPassword(password: String): Boolean {
        if (password.length < MIN_PASSWORD_LENGTH) {
            Toast.makeText(
                this,
                "비밀번호는 최소 ${MIN_PASSWORD_LENGTH}자 이상이어야 합니다.",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        return true
    }

    private fun saveLoggedInUser(email: String) {
        prefs.edit().putString("user_email", email).apply()
    }

    private fun clearLoggedInUser() {
        prefs.edit().clear().apply()
    }

    // -----------------------------
    // 프로필 이미지 선택 (카메라 / 갤러리)
    // -----------------------------
    private fun initImagePickLaunchers() {
        pickImageLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                // 미리보기용: 바로 눈에 보이게
                binding.imageAvatar.setImageURI(uri)

                // 실제로 앱 재실행 후 사용할 것은 Supabase URL만
                uploadProfileImageToSupabase(uri)
            }
        }
    }



    private fun showImageSourceDialog() {
        val items = arrayOf("갤러리에서 선택")

        AlertDialog.Builder(this)
            .setTitle("프로필 사진 설정")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> pickImageLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun loadProfileImageFromSupabase() {
        val url = prefs.getString(PREF_KEY_PROFILE_IMAGE_URL, null) ?: return
        binding.imageAvatar.load(url)
    }



    private fun uploadProfileImageToSupabase(uri: Uri) {
        val client = supabase ?: return

        lifecycleScope.launch {
            try {
                val bytes = uriToBytes(uri)
                if (bytes.isEmpty()) {
                    Log.e("ProfileActivity", "uriToBytes 결과가 비어 있습니다.")
                    return@launch
                }

                val fileName = "avatar_${System.currentTimeMillis()}.jpg"

                client.storage.from("avatars").upload(
                    path = fileName,
                    data = bytes
                ) {
                    upsert = true
                    contentType = ContentType.Image.JPEG   // ← String 말고 이 타입
                }

                val publicUrl = client.storage.from("avatars").publicUrl(fileName)
                Log.d("ProfileActivity", "Avatar uploaded. URL = $publicUrl")

                // 이제부터는 Supabase URL만 저장
                prefs.edit()
                    .putString(PREF_KEY_PROFILE_IMAGE_URL, publicUrl)
                    .apply()

                // 업로드 직후에도 같은 URL로 다시 로딩
                binding.imageAvatar.load(publicUrl)

            } catch (e: Exception) {
                Log.e("ProfileActivity", "uploadProfileImageToSupabase error", e)
                Toast.makeText(
                    this@ProfileActivity,
                    "프로필 이미지 업로드에 실패했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }



    private fun uriToBytes(uri: android.net.Uri): ByteArray {
        val inputStream = contentResolver.openInputStream(uri)
        return inputStream?.readBytes() ?: ByteArray(0)
    }





    // -----------------------------
    // 하단 네비게이션
    // -----------------------------
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val targetActivity = when (item.itemId) {
                R.id.navigation_map -> MainActivity::class.java
                R.id.navigation_my_reviews -> MyReviewsActivity::class.java
                R.id.navigation_my_favorite -> MyFavoritesActivity::class.java
                R.id.navigation_profile -> ProfileActivity::class.java
                else -> return@setOnItemSelectedListener false
            }

            val intent = Intent(this, targetActivity)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            startActivity(intent)
            overridePendingTransition(0, 0)
            true
        }
    }
}
