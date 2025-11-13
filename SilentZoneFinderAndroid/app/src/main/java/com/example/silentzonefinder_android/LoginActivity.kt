package com.example.silentzonefinder_android

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.silentzonefinder_android.databinding.ActivityLoginBinding
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val supabase: io.github.jan.supabase.SupabaseClient? by lazy {
        try {
            SupabaseManager.client
        } catch (e: IllegalStateException) {
            android.util.Log.e("LoginActivity", "Supabase 초기화 실패 (IllegalStateException)", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("LoginActivity", "Supabase 초기화 실패 (기타 예외)", e)
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
        
        try {
            binding = ActivityLoginBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Exception) {
            android.util.Log.e("LoginActivity", "레이아웃 바인딩 실패", e)
            throw e
        }
        
        // Supabase 초기화는 필요할 때만 수행 (onCreate에서는 초기화하지 않음)
        // 이렇게 하면 onCreate에서 크래시가 발생하지 않음

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
            android.util.Log.d("LoginActivity", "회원가입 시도: $email")
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            
            android.util.Log.d("LoginActivity", "회원가입 성공")
            // Supabase 기본 설정에서는 이메일 인증이 필요하므로 안내 메시지 표시
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
            android.util.Log.d("LoginActivity", "로그인 시도: $email")
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            
            android.util.Log.d("LoginActivity", "로그인 성공")
            Toast.makeText(this, "로그인 성공!", Toast.LENGTH_SHORT).show()
            
            // 로그인 성공 후 MainActivity로 이동
            val intent = android.content.Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        } catch (e: HttpRequestException) {
            android.util.Log.e("LoginActivity", "로그인 실패 (HttpRequestException)", e)
            android.util.Log.e("LoginActivity", "오류 메시지: ${e.message}")
            android.util.Log.e("LoginActivity", "오류 스택: ${e.stackTraceToString()}")
            
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
            android.util.Log.e("LoginActivity", "로그인 실패 (RestException)", e)
            android.util.Log.e("LoginActivity", "오류 메시지: ${e.message}")
            Toast.makeText(this, "서버 오류가 발생했습니다: ${e.message ?: "알 수 없는 오류"}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("LoginActivity", "로그인 실패 (Exception)", e)
            android.util.Log.e("LoginActivity", "오류 타입: ${e.javaClass.simpleName}")
            android.util.Log.e("LoginActivity", "오류 메시지: ${e.message}")
            android.util.Log.e("LoginActivity", "오류 스택: ${e.stackTraceToString()}")
            
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
}

