package com.example.silentzonefinder_android

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.silentzonefinder_android.databinding.ActivityProfileBinding
import com.example.silentzonefinder_android.utils.PermissionHelper
import com.example.silentzonefinder_android.worker.QuietZoneWorker
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    private val supabase: SupabaseClient? by lazy {
        try {
            SupabaseManager.client
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Supabase 초기화 실패", e)
            null
        }
    }

    private val prefs by lazy {
        getSharedPreferences("auth_prefs", MODE_PRIVATE)
    }

    private val notificationPrefs: SharedPreferences by lazy {
        getSharedPreferences("notification_prefs", MODE_PRIVATE)
    }

    private lateinit var pickImageLauncher: ActivityResultLauncher<String>

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "알림 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            binding.switchQuietAlert.isChecked = false
            Toast.makeText(this, "알림 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

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
        QuietZoneWorker.cancel(this)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNavigation.selectedItemId = R.id.navigation_profile
        initImagePickLaunchers()
        setupBottomNavigation()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        setupBottomNavigation()
        binding.bottomNavigation.selectedItemId = R.id.navigation_profile
        overridePendingTransition(0, 0)
        checkLoginStatus()
        loadProfileImageFromSupabase()

        // 필요하면 애니메이션 제거도 여기서
        // overridePendingTransition(0, 0)
        loadGlobalQuietAlertFromSupabase()
    }

    private fun showLoginLayout() {
        binding.loginLayout.visibility = View.VISIBLE
        binding.loggedInLayout.visibility = View.GONE
    }

    private fun showLoggedInLayout(name: String, email: String) {
        binding.loginLayout.visibility = View.GONE
        binding.loggedInLayout.visibility = View.VISIBLE
        binding.textUserName.text = name
        binding.textUserEmail.text = email
        binding.textReviewCount.text = "12"

    }

    private fun checkLoginStatus() {
        val email = prefs.getString(PREF_KEY_EMAIL, null)
        if (email.isNullOrEmpty()) {
            showLoginLayout()
        } else {
            val name = email.substringBefore("@")
            showLoggedInLayout(name, email)
        }
    }

    private fun setupClickListeners() = with(binding) {
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

        switchQuietAlert.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !PermissionHelper.hasNotificationPermission(this@ProfileActivity)
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@setOnCheckedChangeListener
                }
                //scheduleQuietZoneWorker()
                notificationPrefs.edit().putBoolean("quiet_zone_notifications_enabled", true).apply()
                Toast.makeText(this@ProfileActivity, "조용한 존 추천 알림이 켜졌습니다.", Toast.LENGTH_SHORT).show()
            } else {
                //cancelQuietZoneWorker()
                notificationPrefs.edit().putBoolean("quiet_zone_notifications_enabled", false).apply()
                Toast.makeText(this@ProfileActivity, "조용한 존 추천 알림이 꺼졌습니다.", Toast.LENGTH_SHORT).show()
            }
            onGlobalQuietAlertToggled(isChecked)
        }

        //val notificationsEnabled =
            //notificationPrefs.getBoolean("quiet_zone_notifications_enabled", false)
        //switchQuietAlert.isChecked = notificationsEnabled

        rowNotificationHistory.setOnClickListener {
            val intent = Intent(this@ProfileActivity, NotificationHistoryActivity::class.java)
            startActivity(intent)
        }

        rowSettings.setOnClickListener {
            val intent = Intent(this@ProfileActivity, SettingsActivity::class.java)
            startActivity(intent)
        }

        rowContactSupport.setOnClickListener {
            val intent = Intent(this@ProfileActivity, ContactSupportActivity::class.java)
            startActivity(intent)
        }

        btnChangeAvatar.setOnClickListener {
            showImageSourceDialog()
        }

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
                "비밀번호는 최소 ${'$'}MIN_PASSWORD_LENGTH자 이상이어야 합니다.",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        return true
    }

    private fun saveLoggedInUser(email: String) {
        prefs.edit().putString(PREF_KEY_EMAIL, email).apply()
    }

    private fun clearLoggedInUser() {
        prefs.edit().clear().apply()
    }

    private fun initImagePickLaunchers() {
        pickImageLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                binding.imageAvatar.setImageURI(uri)
                uploadProfileImageToSupabase(uri)
            }
        }
    }
    // ★ Supabase에 마스터 스위치 상태 저장
    private fun onGlobalQuietAlertToggled(enabled: Boolean) {
        val client = supabase ?: return

        lifecycleScope.launch {
            try {
                val session = client.auth.currentSessionOrNull() ?: return@launch
                val userId = session.user?.id?.toString() ?: return@launch

                withContext(Dispatchers.IO) {
                    client.postgrest["user_notification_settings"].upsert(
                        UserNotificationSettingsDto(
                            userId = userId,
                            quietRecommendationEnabled = enabled
                        )
                    )
                }
                Log.d("ProfileActivity", "Global quiet alert updated: $enabled")
            } catch (e: Exception) {
                Log.e("ProfileActivity", "update global quiet alert failed", e)
            }
        }
    }

    // ★ Supabase에서 마스터 스위치 상태 불러오기
    private fun loadGlobalQuietAlertFromSupabase() {
        val client = supabase ?: return

        lifecycleScope.launch {
            try {
                val session = client.auth.currentSessionOrNull() ?: return@launch
                val userId = session.user?.id?.toString() ?: return@launch

                val enabled = withContext(Dispatchers.IO) {
                    val rows = client.postgrest["user_notification_settings"]
                        .select {
                            filter {
                                eq("user_id", userId)
                            }
                            limit(1)
                        }
                        .decodeList<UserNotificationSettingsDto>()

                    rows.firstOrNull()?.quietRecommendationEnabled ?: true
                }

                // 스위치 상태 반영
                binding.switchQuietAlert.isChecked = enabled

                // 로컬 Worker / SharedPreferences도 맞춰주고 싶으면
                if (enabled) {
                    notificationPrefs.edit()
                        .putBoolean("quiet_zone_notifications_enabled", true)
                        .apply()
                } else {
                    notificationPrefs.edit()
                        .putBoolean("quiet_zone_notifications_enabled", false)
                        .apply()
                }

                Log.d("ProfileActivity", "Global quiet alert loaded: $enabled")
            } catch (e: Exception) {
                Log.e("ProfileActivity", "load global quiet alert failed", e)
            }
        }
    }

    @Serializable
    private data class UserNotificationSettingsDto(
        @SerialName("user_id") val userId: String,
        @SerialName("quiet_recommendation_enabled")
        val quietRecommendationEnabled: Boolean
    )



    private fun showImageSourceDialog() {
        val dialog = com.example.silentzonefinder_android.fragment.ImageSourceDialogFragment.newInstance(
            title = getString(R.string.profile_image_setting),
            hasCamera = false // 프로필은 갤러리만 사용
        )
        dialog.setOnSourceSelectedListener(object : com.example.silentzonefinder_android.fragment.ImageSourceDialogFragment.OnSourceSelectedListener {
            override fun onCameraSelected() {
                // 프로필에서는 카메라 사용 안 함
            }
            override fun onGallerySelected() {
                pickImageLauncher.launch("image/*")
            }
        })
        dialog.show(supportFragmentManager, "image_source")
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
                    contentType = ContentType.Image.JPEG
                }

                val publicUrl = client.storage.from("avatars").publicUrl(fileName)
                Log.d("ProfileActivity", "Avatar uploaded. URL = ${'$'}publicUrl")

                prefs.edit()
                    .putString(PREF_KEY_PROFILE_IMAGE_URL, publicUrl)
                    .apply()

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

    private fun uriToBytes(uri: Uri): ByteArray {
        val inputStream = contentResolver.openInputStream(uri)
        return inputStream?.readBytes() ?: ByteArray(0)
    }

    private fun setupBottomNavigation() {

        binding.bottomNavigation.setOnItemSelectedListener { item ->

            val targetActivity = when (item.itemId) {

                // Map 화면
                R.id.navigation_map -> MainActivity::class.java

                // My Reviews 화면
                R.id.navigation_my_reviews -> MyReviewsActivity::class.java

                // My Favorites 화면
                R.id.navigation_my_favorite -> MyFavoritesActivity::class.java

                // Profile 화면
                R.id.navigation_profile -> {
                    return@setOnItemSelectedListener true
                }

                else -> return@setOnItemSelectedListener false
            }

            if (targetActivity == this::class.java) {
                return@setOnItemSelectedListener true
            }

            val intent = Intent(this, targetActivity).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }

            startActivity(intent)
            overridePendingTransition(0, 0)
            true
        }
    }


}
