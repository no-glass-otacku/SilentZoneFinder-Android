package com.example.silentzonefinder_android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.silentzonefinder_android.databinding.ActivityContactSupportBinding

class ContactSupportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactSupportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactSupportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 뒤로가기 버튼
        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
