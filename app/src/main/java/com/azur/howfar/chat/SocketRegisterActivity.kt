package com.azur.howfar.chat

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.azur.howfar.R
import com.azur.howfar.databinding.ActivitySocketRegisterBinding

class SocketRegisterActivity : AppCompatActivity() {
    private val binding by lazy{ActivitySocketRegisterBinding.inflate(layoutInflater)}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.save.setOnClickListener {
            val input = binding.username.text.trim().toString()
            if (input == "") return@setOnClickListener
            startActivity(Intent(this, ChatBoxActivity::class.java).putExtra("name", input))
        }
    }
}