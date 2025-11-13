package com.example.silentzonefinder_android

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.silentzonefinder_android.databinding.ActivityProfileBinding
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding
    private val supabase: io.github.jan.supabase.SupabaseClient? by lazy {
        try {
            SupabaseManager.client
        } catch (e: IllegalStateException) {
            android.util.Log.e("ProfileActivity", "Supabase 초기화 실패 (IllegalStateException)", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("ProfileActivity", "Supabase 초기화 실패 (기타 예외)", e)
            e.printStackTrace()
            null
        }
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

        setupLoginButtons()
        checkLoginStatus()
    }

    override fun onResume() {
        super.onResume()
        setupBottomNavigation()
        binding.bottomNavigation.selectedItemId = R.id.navigation_profile
        overridePendingTransition(0, 0)
        
        // 화면이 다시 나타날 때 로그인 상태 확인
        checkLoginStatus()
    }

    private fun checkLoginStatus() {
        val client = supabase ?: return
        
        lifecycleScope.launch {
            try {
                val session = client.auth.currentSessionOrNull()
                val user = session?.user
                if (user != null) {
                    // 로그인된 경우
                    showLoggedInUI(user.email ?: "이메일 없음")
                } else {
                    // 로그인하지 않은 경우
                    showLoginUI()
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileActivity", "로그인 상태 확인 실패", e)
                showLoginUI()
            }
        }
    }

    private fun showLoginUI() {
        binding.loginLayout.visibility = android.view.View.VISIBLE
        binding.loggedInLayout.visibility = android.view.View.GONE
    }

    private fun showLoggedInUI(email: String) {
        binding.loginLayout.visibility = android.view.View.GONE
        binding.loggedInLayout.visibility = android.view.View.VISIBLE
        binding.textUserEmail.text = "이메일: $email"
    }

    private fun setupLoginButtons() {
        binding.btnSignUp.setOnClickListener {
            val email = binding.inputEmail.text.toString().trim()
            val password = binding.inputPassword.text.toString()
            
            if (!validateInput(email, password)) {
                return@setOnClickListener
            }
            
            lifecycleScope.launch { signUp(email, password) }
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.inputEmail.text.toString().trim()
            val password = binding.inputPassword.text.toString()
            
            if (!validateInput(email, password)) {
                return@setOnClickListener
            }
            
            lifecycleScope.launch { login(email, password) }
        }

        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch { logout() }
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            Toast.makeText(this, "이메일을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            Toast.makeText(this, "올바른 이메일 형식이 아닙니다.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.length < MIN_PASSWORD_LENGTH) {
            Toast.makeText(this, "비밀번호는 최소 ${MIN_PASSWORD_LENGTH}자 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private suspend fun signUp(email: String, password: String) {
        val client = supabase ?: run {
            Toast.makeText(this, "Supabase 연결이 설정되지 않았습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            android.util.Log.d("ProfileActivity", "회원가입 시도: $email")
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            
            android.util.Log.d("ProfileActivity", "회원가입 성공")
            Toast.makeText(this, "회원가입 완료! 이메일 인증이 필요합니다.", Toast.LENGTH_LONG).show()
        } catch (e: HttpRequestException) {
            val errorMessage = when {
                e.message?.contains("400") == true || e.message?.contains("Bad Request") == true -> 
                    "잘못된 요청입니다. 입력 정보를 확인해주세요."
                e.message?.contains("409") == true || e.message?.contains("Conflict") == true -> 
                    "이미 존재하는 이메일입니다."
                e.message?.contains("422") == true || e.message?.contains("Unprocessable") == true -> 
                    "입력 정보가 올바르지 않습니다."
                else -> "회원가입에 실패했습니다: ${e.message ?: "알 수 없는 오류"}"
            }
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        } catch (e: RestException) {
            Toast.makeText(this, "서버 오류가 발생했습니다: ${e.message ?: "알 수 없는 오류"}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            val errorMsg = e.message ?: "알 수 없는 오류"
            when {
                errorMsg.contains("email") && errorMsg.contains("already") -> 
                    "이미 존재하는 이메일입니다."
                errorMsg.contains("password") -> 
                    "비밀번호가 올바르지 않습니다."
                else -> "회원가입에 실패했습니다: $errorMsg"
            }.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
    }

    private suspend fun login(email: String, password: String) {
        val client = supabase ?: run {
            Toast.makeText(this, "Supabase 연결이 설정되지 않았습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            android.util.Log.d("ProfileActivity", "로그인 시도: $email")
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            
            android.util.Log.d("ProfileActivity", "로그인 성공")
            Toast.makeText(this, "로그인 성공!", Toast.LENGTH_SHORT).show()
            
            // 로그인 성공 후 UI 업데이트
            checkLoginStatus()
            
            // 입력 필드 초기화
            binding.inputEmail.text.clear()
            binding.inputPassword.text.clear()
        } catch (e: HttpRequestException) {
            android.util.Log.e("ProfileActivity", "로그인 실패 (HttpRequestException)", e)
            
            val errorMessage = when {
                e.message?.contains("400") == true || e.message?.contains("Bad Request") == true -> 
                    "이메일 또는 비밀번호가 올바르지 않습니다."
                e.message?.contains("401") == true || e.message?.contains("Unauthorized") == true -> 
                    "인증에 실패했습니다. 이메일과 비밀번호를 확인해주세요."
                e.message?.contains("404") == true || e.message?.contains("Not Found") == true -> 
                    "사용자를 찾을 수 없습니다."
                e.message?.contains("Email not confirmed") == true || e.message?.contains("email_not_confirmed") == true ->
                    "이메일 인증이 필요합니다. 이메일을 확인해주세요."
                else -> "로그인에 실패했습니다: ${e.message ?: "알 수 없는 오류"}"
            }
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        } catch (e: RestException) {
            android.util.Log.e("ProfileActivity", "로그인 실패 (RestException)", e)
            Toast.makeText(this, "서버 오류가 발생했습니다: ${e.message ?: "알 수 없는 오류"}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("ProfileActivity", "로그인 실패 (Exception)", e)
            
            val errorMsg = e.message ?: "알 수 없는 오류"
            val finalMessage = when {
                errorMsg.contains("Invalid login") || (errorMsg.contains("email") && errorMsg.contains("password")) -> 
                    "이메일 또는 비밀번호가 올바르지 않습니다."
                errorMsg.contains("Email not confirmed") || errorMsg.contains("email_not_confirmed") ->
                    "이메일 인증이 필요합니다. 이메일을 확인해주세요."
                errorMsg.contains("User not found") -> 
                    "사용자를 찾을 수 없습니다."
                else -> "로그인에 실패했습니다: $errorMsg"
            }
            Toast.makeText(this, finalMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun logout() {
        val client = supabase ?: run {
            Toast.makeText(this, "Supabase 연결이 설정되지 않았습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            android.util.Log.d("ProfileActivity", "로그아웃 시도")
            client.auth.signOut()
            
            android.util.Log.d("ProfileActivity", "로그아웃 성공")
            Toast.makeText(this, "로그아웃되었습니다.", Toast.LENGTH_SHORT).show()
            
            // 로그아웃 후 UI 업데이트
            checkLoginStatus()
        } catch (e: Exception) {
            android.util.Log.e("ProfileActivity", "로그아웃 실패", e)
            Toast.makeText(this, "로그아웃에 실패했습니다: ${e.message ?: "알 수 없는 오류"}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            // 1. 이동할 Activity 클래스를 먼저 결정합니다.
            val targetActivity = when (item.itemId) {
                R.id.navigation_map -> MainActivity::class.java
                R.id.navigation_my_reviews -> MyReviewsActivity::class.java
                R.id.navigation_my_favorite -> MyFavoritesActivity::class.java
                R.id.navigation_profile -> {
                    return@setOnItemSelectedListener true
                }
                else -> return@setOnItemSelectedListener false // 예상치 못한 ID
            }

            // 2. 공통 로직을 한 번만 실행합니다.
            val intent = Intent(this, targetActivity)

            // Activity 재활용 및 부드러운 전환을 위한 플래그
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)

            startActivity(intent)

            // 3. 애니메이션 설정 (0, 0은 애니메이션 없음)
            overridePendingTransition(0, 0)

            true // 이벤트 처리가 완료되었음을 반환
        }
    }
}