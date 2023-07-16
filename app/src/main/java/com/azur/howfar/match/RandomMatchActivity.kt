package com.azur.howfar.match

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.azur.howfar.R
import com.azur.howfar.activity.BaseActivity
import com.azur.howfar.databinding.ActivityRandomMatchBinding
import com.azur.howfar.dilog.IncomingCallDialog
import com.azur.howfar.models.CallData
import com.azur.howfar.models.CallEngagementType
import com.azur.howfar.models.CallType
import com.azur.howfar.models.UserProfile

class RandomMatchActivity : BaseActivity() {
    private val binding by lazy { ActivityRandomMatchBinding.inflate(layoutInflater) }
    private val user = FirebaseAuth.getInstance().currentUser
    private var randomProfile = UserProfile()

    var runnableR = Runnable {
        binding.ivUser.clearAnimation()
        binding.ivUser2.visibility = View.VISIBLE
        binding.btnMatch.visibility = View.VISIBLE
        binding.btnCall.visibility = View.VISIBLE
        binding.ivMatch.visibility = View.GONE
    }
    var zoomin: Animation? = null
    private var animZoomin: Animation? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setMyProfile()
        matchAgain()
        binding.btnMatch.setOnClickListener { matchAgain() }
        binding.btnCall.setOnClickListener { makeACall() }
    }

    override fun onResume() {
        window.statusBarColor = resources!!.getColor(R.color.black_back)
        super.onResume()
    }

    private fun makeACall() {
        onBackPressed()
        val callData = CallData(channelName = user!!.uid, callerUid = user.uid, callType = CallType.VIDEO, uids = arrayListOf(user.uid, randomProfile.uid))
        val intent = Intent(this, IncomingCallDialog::class.java)
        intent.putExtra("CREATE", CallEngagementType.CREATE)
        intent.putExtra("callData", Gson().toJson(callData))
        startActivity(intent)
    }

    private fun setMyProfile() {
        val ref = FirebaseDatabase.getInstance().reference.child(USER_DETAILS).child(user!!.uid)
        ref.get().addOnSuccessListener {
            if (it.exists()) {
                val userProfile = it.getValue(UserProfile::class.java)!!
                try {
                    Glide.with(this.applicationContext).load(userProfile.image).circleCrop().into(binding.ivUser)
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun matchAgain() {
        zoomin = AnimationUtils.loadAnimation(this, R.anim.zoomin)
        animZoomin = AnimationUtils.loadAnimation(applicationContext, R.anim.zoomin)
        binding.lytStatus.text = "Searching for new Friends..."
        handler.removeCallbacks(runnableR)
        handler.postDelayed(runnableR, 3000)
        binding.ivUser2.visibility = View.GONE
        binding.btnCall.visibility = View.GONE
        binding.btnMatch.visibility = View.GONE
        binding.ivUser.startAnimation(animZoomin)

        binding.ivMatch.visibility = View.VISIBLE
        var allUsersListExceptMe = arrayListOf<UserProfile>()
        val ref = FirebaseDatabase.getInstance().reference.child(USER_DETAILS)
        ref.get().addOnSuccessListener {
            if (it.exists()) for (i in it.children) {
                val userProfile = i.getValue(UserProfile::class.java)!!
                if (userProfile.uid != user!!.uid) allUsersListExceptMe.add(userProfile)
                randomProfile = allUsersListExceptMe.shuffled().last()
                try {
                    binding.lytStatus.text = "Matched with ${randomProfile.name}"
                    Glide.with(this).load(randomProfile.image).circleCrop().into(binding.ivUser2)
                    binding.ivUser2.visibility = View.VISIBLE
                } catch (e: Exception) {
                }
            } else binding.lytStatus.text = "No user matched..."
        }
    }

    override fun onPause() {
        super.onPause()
        onBackPressed()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        handler.removeCallbacks(runnableR)
    }

    companion object {
        const val TRANSFER_HISTORY = "user_coins_transfer"
        const val USER_DETAILS = "user_details"
        const val CHAT_REFERENCE = "chat_reference"
    }
}