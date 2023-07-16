package com.azur.howfar.user

import android.Manifest
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.FirebaseDatabase
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.databinding.ActivityFollowrsListBinding
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.EventListenerType.onDataChange
import com.azur.howfar.models.UserProfile
import com.azur.howfar.retrofit.Const

class FollowrsListActivity : BaseActivity() {
    private val binding by lazy { ActivityFollowrsListBinding.inflate(layoutInflater) }
    val list: MutableList<UserProfile> = ArrayList()
    private var followrsUsersAdapter = FollowersUsersAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val intent = intent
        val user = intent.getStringExtra(Const.USER_STR)!!

        followrsUsersAdapter.list = list
        binding.rvFeed.adapter = followrsUsersAdapter
        binding.rvFeed.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        val following = FirebaseDatabase.getInstance().reference.child(FOLLOWERS).child(user)
        ValueEventLiveData(following).observe(this) { followersSnap ->
            when (followersSnap.second) {
                onDataChange -> {
                    for (i in followersSnap.first.children) {
                        FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(i.value.toString()).get().addOnSuccessListener { snap ->
                            if (snap.exists()) {
                                val userProfile = snap.getValue(UserProfile::class.java)!!
                                println("Data ************************* $userProfile")
                                if (userProfile !in list) list.add(userProfile)
                                followrsUsersAdapter.notifyItemInserted(list.size)
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val CALL_REFERENCE = "call_reference"
        const val USER_DETAILS = "user_details"
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val CHAT_REFERENCE = "chat_reference"
        const val MY_BLOCKED_CONTACTS = "blocked_contacts"
        const val PERMISSION_CODE = 22
        const val FOLLOWERS = "followers"
        const val FOLLOWING = "following"
        val REQUESTED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )

        const val FOLLOW = 0
        const val IS_FOLLOWING = 1
        const val I_AM_FOLLOWING = 2
    }
}