package com.azur.howfar.howfarwallet

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.azur.howfar.R
import com.azur.howfar.databinding.ActivityAddMoneyBinding

class AddMoneyActivity : AppCompatActivity() {
    private val binding by lazy{ ActivityAddMoneyBinding.inflate(layoutInflater)}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
    }
}