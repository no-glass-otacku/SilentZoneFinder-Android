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
import kotlinx.coroutines.launch
import java.util.regex.Pattern

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

    companion object {
        private val EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        )
        private const val MIN_PASSWORD_LENGTH = 6
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigation.selectedItemId = R.id.navigation_profile
        overridePendingTransition(0, 0)

        // 저장된 로그인 정보 기준으로 UI 갱신
        checkLoginStatus()
    }

    // -----------------------------
    // UI 상태 전환
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
        if (email.isNullOrBlank()) {
            showLoginLayout()
        } else {
            val name = email.substringBefore("@")
            showLoggedInLayout(name, email)
        }
    }

    // -----------------------------
    // 클릭 리스너 묶음
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
                        "로그인 실패",
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
                        "알 수 없는 오류로 로그인에 실패했습니다.",
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
                        "회원가입 완료. 이메일로 전송된 안내를 확인하세요.",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: RestException) {
                    Toast.makeText(
                        this@ProfileActivity,
                        "회원가입 실패",
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
                        "알 수 없는 오류로 회원가입에 실패했습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // 알림 토글
        switchQuietAlert.setOnCheckedChangeListener { _, isChecked ->
            val msg = if (isChecked) {
                "조용한 존 추천 알림이 켜졌습니다."
            } else {
                "조용한 존 추천 알림이 꺼졌습니다."
            }
            Toast.makeText(this@ProfileActivity, msg, Toast.LENGTH_SHORT).show()
        }

        // 알림 히스토리 / 설정은 아직 화면 없으니 안내만
        rowNotificationHistory.setOnClickListener {
            val intent = Intent(this@ProfileActivity, NotificationHistoryActivity::class.java)
            startActivity(intent)
        }

        rowSettings.setOnClickListener {
            val intent = Intent(this@ProfileActivity, SettingsActivity::class.java)
            startActivity(intent)
        }


        // Sign Out (텍스트)
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
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            Toast.makeText(this, "올바른 이메일 형식이 아닙니다.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun isValidPassword(password: String): Boolean {
        if (password.length < MIN_PASSWORD_LENGTH) {
            Toast.makeText(
                this,
                "비밀번호는 ${MIN_PASSWORD_LENGTH}자 이상이어야 합니다.",
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
    // 하단 네비게이션
    // -----------------------------
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val targetActivity = when (item.itemId) {
                R.id.navigation_map -> MainActivity::class.java
                R.id.navigation_my_reviews -> MyReviewsActivity::class.java
                R.id.navigation_my_favorite -> MyFavoritesActivity::class.java
                R.id.navigation_profile -> {
                    // 현재 화면
                    return@setOnItemSelectedListener true
                }
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
