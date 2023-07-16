package com.azur.howfar.activity

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import com.azur.howfar.R
import com.azur.howfar.databinding.ActivitySettingBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class SettingActivity : BaseActivity(), OnCheckedChangeListener {
    private val binding by lazy { ActivitySettingBinding.inflate(layoutInflater) }
    private lateinit var pref: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        pref= getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        binding.logOut.setOnClickListener {
            Firebase.auth.signOut()
            startActivity(Intent(this, LoginActivityActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            })
            overridePendingTransition(R.anim.enter_left_to_right, R.anim.exit_left_to_right)
        }
        binding.contactUs.setOnClickListener {
            startActivity(Intent(this, ContactUsActivity::class.java))
            overridePendingTransition(R.anim.enter_left_to_right, R.anim.exit_left_to_right)
        }
        binding.aboutUs.setOnClickListener {
            startActivity(Intent(this, AboutUsActivity::class.java))
            overridePendingTransition(R.anim.enter_left_to_right, R.anim.exit_left_to_right)
        }
        binding.privacyPolicy.setOnClickListener {
            val uri = Uri.parse("http://www.howfar.online")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }

        binding.liveSwitch.setOnCheckedChangeListener(this)
    }

    override fun onResume() {
        when(pref.getBoolean(getString(R.string.live_switch), false)){
            true-> binding.liveSwitch.isChecked = true
            else -> binding.liveSwitch.isChecked = true
        }
        super.onResume()
    }

    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        when(buttonView?.id){
            R.id.live_switch-> pref.edit().putBoolean(getString(R.string.live_switch), isChecked).apply()
        }
    }
}