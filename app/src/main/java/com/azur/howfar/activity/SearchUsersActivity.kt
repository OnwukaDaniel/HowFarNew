package com.azur.howfar.activity

import android.Manifest
import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.recyclerview.widget.LinearLayoutManager
import com.azur.howfar.databinding.ActivitySearchUsersBinding
import com.azur.howfar.howfarchat.chat.ActivitySearchChat
import com.azur.howfar.livedata.ValueEventLiveData
import com.azur.howfar.models.EventListenerType
import com.azur.howfar.models.EventListenerType.onDataChange
import com.azur.howfar.models.UserProfile
import com.azur.howfar.retrofit.Const
import com.azur.howfar.user.FollowersUsersAdapter
import com.azur.howfar.utils.Util
import com.google.firebase.database.FirebaseDatabase

class SearchUsersActivity : AppCompatActivity() {
    private val binding by lazy { ActivitySearchUsersBinding.inflate(layoutInflater) }
    val dataset: MutableList<UserProfile> = ArrayList()
    private val allUsers: ArrayList<UserProfile> = arrayListOf()
    private var followrsUsersAdapter = FollowersUsersAdapter()

    inner class InputTextWatcher : TextWatcher {
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit
        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit

        override fun afterTextChanged(p0: Editable?) {
            searchUser(p0.toString().trim().lowercase())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val intent = intent
        binding.backBtn.setOnClickListener{
            onBackPressed()
        }

        followrsUsersAdapter.list = dataset
        binding.rvFeed.adapter = followrsUsersAdapter
        binding.rvFeed.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.etSearch.addTextChangedListener(InputTextWatcher())

        val ref = FirebaseDatabase.getInstance().reference.child(USER_DETAILS)
        ref.get().addOnSuccessListener {
            if (it.exists()) {
                for (i in it.children) {
                    val userProfile = i.getValue(UserProfile::class.java)!!
                    if (userProfile !in dataset) {
                        dataset.add(userProfile)
                        allUsers.add(userProfile)
                        followrsUsersAdapter.notifyItemInserted(dataset.size)
                    }
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun searchUser(input: String) {
        clearAdapter()
        if (input != "") for (i in allUsers) {
            if (input in i.name.lowercase() || input in i.phone) dataset.add(i)
            followrsUsersAdapter.notifyDataSetChanged()
        } else {
            dataset.clear()
            followrsUsersAdapter.notifyDataSetChanged()
            for (i in allUsers) dataset.add(i)
        }
        followrsUsersAdapter.list = dataset
        binding.rvFeed.adapter = followrsUsersAdapter
        followrsUsersAdapter.notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun clearAdapter() {
        dataset.clear()
        followrsUsersAdapter.notifyDataSetChanged()
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