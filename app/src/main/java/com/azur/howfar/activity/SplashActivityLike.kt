package com.azur.howfar.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.azur.howfar.R

class SplashActivityLike : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splesh_like)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        val anim = AnimationUtils.loadAnimation(this, R.anim.splesh)
        anim.fillAfter = true
        //findViewById<View>(R.id.tv1).visibility = View.VISIBLE
        //findViewById<View>(R.id.tv1).startAnimation(anim)
        Handler(Looper.myLooper()!!).postDelayed({ startActivity(Intent(this@SplashActivityLike, MainActivity::class.java)) }, 500)
        overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_right_to_left)
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    }

    override fun finish() {
        overridePendingTransition(R.anim.enter_right_to_left, R.anim.exit_left_to_right)
        super.finish()
    }
}