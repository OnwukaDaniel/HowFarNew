package com.azur.howfar.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.azur.howfar.databinding.ActivityAboutUsBinding

class AboutUsActivity : AppCompatActivity() {
    private val binding by lazy { ActivityAboutUsBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
    }
}