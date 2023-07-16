package com.azur.howfar.user.freecoins

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.databinding.ActivityFreeDimondsBinding

class FreeHFCentsActivity : BaseActivity() {
    private val binding by lazy { ActivityFreeDimondsBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.watchVideo.setOnClickListener {
            Toast.makeText(this, "Coming soon", Toast.LENGTH_LONG).show()
        }

    }

    fun onClickCopy(view: View?) {
        Log.d("TAG", "onClickCopy: ")
    }

    fun onClickShare(view: View?) {
        Log.d("TAG", "onClickCopy: 1")
    }

    fun onClickSubmit(view: View?) {
        Log.d("TAG", "onClickCopy:w ")
    }
}