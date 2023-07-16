package com.azur.howfar.howfarchat.chat

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.azur.howfar.R
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.databinding.ActivityCustomizeChatBinding

class ActivityCustomizeChat : BaseActivity(), View.OnClickListener {
    private val binding by lazy { ActivityCustomizeChatBinding.inflate(layoutInflater) }
    private lateinit var pref: SharedPreferences
    private val myAuth = FirebaseAuth.getInstance().currentUser!!.uid
    private var receiverUid = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        receiverUid = intent.getStringExtra("uid")!!
        pref = getSharedPreferences(getString(R.string.ALL_PREFERENCE), Context.MODE_PRIVATE)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        binding.displayBack.setOnClickListener { onBackPressed() }
        binding.cancel.setOnClickListener { onBackPressed() }

        binding.bubbleEdit.visibility = View.VISIBLE
        binding.displayBottomSheet.visibility = View.GONE
        binding.purple.setOnClickListener(this)
        binding.pink.setOnClickListener(this)
        binding.darkPink.setOnClickListener(this)
        binding.green.setOnClickListener(this)
        binding.orange.setOnClickListener(this)
        binding.blue.setOnClickListener(this)
        binding.color1.setOnClickListener(this)
        binding.color2.setOnClickListener(this)
        binding.color3.setOnClickListener(this)
    }

    override fun onResume() {
        super.onResume()
        val unwrappedDrawableRight = AppCompatResources.getDrawable(this, R.drawable.chat_bubble_purple_right)
        val unwrappedDrawableLeft = AppCompatResources.getDrawable(this, R.drawable.chat_bubble_purple_left)
        val wrappedDrawableRight = DrawableCompat.wrap(unwrappedDrawableRight!!)
        val wrappedDrawableLeft = DrawableCompat.wrap(unwrappedDrawableLeft!!)
        val chatColor = pref.getString(getString(R.string.chatBubbleColor) + receiverUid, "#660099")
        DrawableCompat.setTint(wrappedDrawableRight, Color.parseColor(chatColor))
        DrawableCompat.setTint(wrappedDrawableLeft, Color.parseColor(chatColor))
        binding.displayToolbar.setBackgroundColor(Color.parseColor(chatColor))
        window.statusBarColor = Color.parseColor(chatColor)
        binding.displayReceived.chatLayout.background = wrappedDrawableLeft
        binding.displaySent.chatLayout.background = wrappedDrawableRight
        binding.displaySent2.chatLayout.background = wrappedDrawableRight
    }

    override fun onClick(p0: View?) {
        val unwrappedDrawableRight = AppCompatResources.getDrawable(this, R.drawable.chat_bubble_purple_right)
        val unwrappedDrawableLeft = AppCompatResources.getDrawable(this, R.drawable.chat_bubble_purple_left)
        val wrappedDrawableRight = DrawableCompat.wrap(unwrappedDrawableRight!!)
        val wrappedDrawableLeft = DrawableCompat.wrap(unwrappedDrawableLeft!!)

        when (p0?.id) {
            R.id.dark_pink -> pref.edit().putString((getString(R.string.chatBubbleColor) + receiverUid), "#C10451").apply()
            R.id.pink -> pref.edit().putString((getString(R.string.chatBubbleColor) + receiverUid), "#E7166B").apply()
            R.id.purple -> pref.edit().putString((getString(R.string.chatBubbleColor) + receiverUid), "#660099").apply()
            R.id.green -> pref.edit().putString((getString(R.string.chatBubbleColor) + receiverUid), "#669900").apply()
            R.id.orange -> pref.edit().putString((getString(R.string.chatBubbleColor) + receiverUid), "#FFBB33").apply()
            R.id.color1 -> pref.edit().putString((getString(R.string.chatBubbleColor) + receiverUid), "#8E6C28").apply()
            R.id.blue -> pref.edit().putString((getString(R.string.chatBubbleColor) + receiverUid), "#0038FF").apply()
            R.id.color2 -> pref.edit().putString((getString(R.string.chatBubbleColor) + receiverUid), "#9C0000").apply()
            R.id.color3 -> pref.edit().putString((getString(R.string.chatBubbleColor) + receiverUid), "#3D4C22").apply()
        }
        val chatColor = pref.getString(getString(R.string.chatBubbleColor) + receiverUid, "#660099")
        DrawableCompat.setTint(wrappedDrawableRight, Color.parseColor(chatColor))
        DrawableCompat.setTint(wrappedDrawableLeft, Color.parseColor(chatColor))
        binding.displayToolbar.setBackgroundColor(Color.parseColor(chatColor))
        window.statusBarColor = Color.parseColor(chatColor)
        binding.displayReceived.chatLayout.background = wrappedDrawableLeft
        binding.displaySent.chatLayout.background = wrappedDrawableRight
        binding.displaySent2.chatLayout.background = wrappedDrawableRight
    }
}