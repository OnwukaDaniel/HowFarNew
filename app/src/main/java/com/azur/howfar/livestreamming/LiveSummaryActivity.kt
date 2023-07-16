package com.azur.howfar.livestreamming

import android.os.Bundle
import android.view.View
import com.azur.howfar.R
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.databinding.ActivityLiveSummaryBinding
import com.azur.howfar.models.BroadcastCallData
import com.azur.howfar.models.UserProfile
import com.azur.howfar.retrofit.Const
import com.azur.howfar.utils.TimeUtils
import com.bumptech.glide.Glide
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson

class LiveSummaryActivity : BaseActivity() {
    private val binding by lazy { ActivityLiveSummaryBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val json = intent.getStringExtra("data")
        val commentsCount = intent.getIntExtra("comments", 0)
        val broadcastTime = intent.getIntExtra("broadcastTime", 0)
        val jsonUserProfile = intent.getStringExtra("profile")
        val coinCount = intent.getIntExtra("coinCount", 0)
        val broadcastCallData = Gson().fromJson(json, BroadcastCallData::class.java)
        val userProfile = Gson().fromJson(jsonUserProfile, UserProfile::class.java)

        Glide.with(this).load(userProfile.image).error(R.drawable.ic_avatar).into(binding.imgUserLarge)
        Glide.with(this).load(userProfile.image).error(R.drawable.ic_avatar).into(binding.imgUser)
        binding.btnHomePage.setOnClickListener { v: View? -> onBackPressed() }
        binding.comments.text = commentsCount.toString()
        binding.newCoins.text = coinCount.toString()
        binding.duration.text = TimeUtils.milliSecondsToTimer(broadcastTime.toLong())

        val callerHistory = FirebaseDatabase.getInstance().reference
            .child(WatchLiveActivity.BROADCAST_HISTORY)
            .child(broadcastCallData.callerUid)
            .child(broadcastCallData.timeCalled)
        callerHistory.get().addOnSuccessListener { hist ->
            if (hist.exists()) {
                var broadcastHistoryData = hist.getValue(BroadcastCallData::class.java)!!
                binding.userJoined.text = broadcastHistoryData.uids.size.toString()
            }
        }
    }
}